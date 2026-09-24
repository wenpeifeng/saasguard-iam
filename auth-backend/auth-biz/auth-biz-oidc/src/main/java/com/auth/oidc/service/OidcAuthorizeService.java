package com.auth.oidc.service;

import cn.hutool.json.JSONUtil;
import com.auth.app.dto.AssignVO;
import com.auth.app.entity.AppClient;
import com.auth.app.service.AppAssignService;
import com.auth.app.service.AppClientService;
import com.auth.common.exception.BusinessException;
import com.auth.idm.entity.IdmUser;
import com.auth.idm.mapper.IdmUserMapper;
import com.auth.system.service.AuditService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * OIDC 授权服务 —— /authorize 校验、授权码签发/消费（RFC 6749 + PKCE RFC 7636）。
 * <p>
 * SPA 架构下的授权码语义：业务系统把用户重定向到统一登录页（前端），
 * 登录/授权确认后前端调 POST /oauth/authorize（带管理端 JWT），
 * 中心校验通过后返回 redirect_uri?code=...&state=...，前端执行跳转 ——
 * 对业务系统而言与标准重定向流完全一致。
 */
@Service
@RequiredArgsConstructor
public class OidcAuthorizeService {

    /** 授权码 Redis 键前缀：oidc:code:{code}（一次性，60 秒） */
    private static final String CODE_PREFIX = "oidc:code:";

    private static final long CODE_TTL_SECONDS = 60;

    private final AppClientService clientService;
    private final AppAssignService assignService;
    private final IdmUserMapper userMapper;
    private final StringRedisTemplate redis;
    private final AuditService auditService;

    /**
     * 授权请求（前端 /oauth/authorize 页面提交）。
     */
    @Data
    public static class AuthorizeRequest {
        private String clientId;
        private String redirectUri;
        /** 固定 code（本实现仅支持授权码模式） */
        private String responseType;
        private String scope;
        private String state;
        private String nonce;
        /** PKCE：code_challenge（S256 推荐） */
        private String codeChallenge;
        /** PKCE：code_challenge_method：S256 / plain */
        private String codeChallengeMethod;
        /** 用户已在授权确认页点击同意（应用配置 consent_required 时必传 true） */
        private Boolean consent;
    }

    /**
     * 授权码载荷（Redis 存储）。
     */
    @Data
    public static class CodePayload {
        private Long userId;
        private String clientId;
        private String redirectUri;
        private String scope;
        private String nonce;
        private String codeChallenge;
        private String codeChallengeMethod;
    }

    /**
     * 授权结果：needConsent=true 表示需要前端展示授权确认页；
     * 否则 redirectUri 为带 code/state 的最终跳转地址。
     */
    @Data
    public static class AuthorizeResult {
        private boolean needConsent;
        private String appName;
        private String redirectUri;
    }

    /**
     * 处理授权请求：校验 client/redirect_uri/用户进门权/consent，签发授权码。
     *
     * @param userId 已登录用户（管理端 JWT 解出）
     * @param ip     来源 IP（登录日志审计用）
     */
    public AuthorizeResult authorize(Long userId, AuthorizeRequest request, String ip) {
        // 1. 应用存在且启用
        AppClient client = clientService.loadEnabledClient(request.getClientId());
        if (client == null) {
            throw new BusinessException("应用不存在或已停用：%s", request.getClientId());
        }
        // 2. response_type 必须为 code
        if (!"code".equals(request.getResponseType())) {
            throw new BusinessException("仅支持 response_type=code（授权码模式）");
        }
        // 3. redirect_uri 精确匹配
        if (!client.getRedirectUri().equals(request.getRedirectUri())) {
            throw new BusinessException("redirect_uri 与应用注册不符");
        }
        // 4. PKCE：challenge 必填（本实现强制 PKCE），method 默认 S256
        String method = request.getCodeChallengeMethod() == null ? "S256" : request.getCodeChallengeMethod();
        if (!"S256".equals(method) && !"plain".equals(method)) {
            throw new BusinessException("transform 不支持的 code_challenge_method：%s", method);
        }
        if (request.getCodeChallenge() == null || request.getCodeChallenge().isBlank()) {
            throw new BusinessException("公共客户端必须携带 code_challenge（PKCE）");
        }
        // 5. 用户在岗且已分配本应用（应用级启用）
        IdmUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getStatus() == IdmUser.STATUS_LEFT) {
            auditService.recordLogin(user.getUsername(), userId, "OIDC", client.getClientId(),
                    false, "账号已离职", ip, null);
            throw new BusinessException("账号已离职，禁止登录");
        }
        if (user.getStatus() == IdmUser.STATUS_SUSPENDED) {
            auditService.recordLogin(user.getUsername(), userId, "OIDC", client.getClientId(),
                    false, "账号已被全局停用", ip, null);
            throw new BusinessException("账号已被全局停用，禁止登录");
        }
        AssignVO assign = assignService.checkLoginAccess(client.getClientId(), userId);
        if (assign == null) {
            auditService.recordLogin(user.getUsername(), userId, "OIDC", client.getClientId(),
                    false, "应用未分配或应用级禁用", ip, null);
            throw new BusinessException("未分配该应用或已被应用级禁用，请联系管理员");
        }
        // 6. consent：应用要求且用户未确认 → 前端展示授权确认页
        AuthorizeResult result = new AuthorizeResult();
        if (client.getConsentRequired() != null && client.getConsentRequired() == 1
                && !Boolean.TRUE.equals(request.getConsent())) {
            result.setNeedConsent(true);
            result.setAppName(client.getAppName());
            return result;
        }
        // 7. 签发一次性授权码（Redis 60 秒）
        CodePayload payload = new CodePayload();
        payload.setUserId(userId);
        payload.setClientId(client.getClientId());
        payload.setRedirectUri(request.getRedirectUri());
        payload.setScope(request.getScope());
        payload.setNonce(request.getNonce());
        payload.setCodeChallenge(request.getCodeChallenge());
        payload.setCodeChallengeMethod(method);
        String code = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(CODE_PREFIX + code, JSONUtil.toJsonStr(payload),
                Duration.ofSeconds(CODE_TTL_SECONDS));
        auditService.recordLogin(user.getUsername(), userId, "OIDC", client.getClientId(),
                true, null, ip, null);

        StringBuilder redirect = new StringBuilder(request.getRedirectUri())
                .append(request.getRedirectUri().contains("?") ? "&" : "?")
                .append("code=").append(code);
        if (request.getState() != null && !request.getState().isBlank()) {
            redirect.append("&state=").append(request.getState());
        }
        result.setRedirectUri(redirect.toString());
        return result;
    }

    /**
     * 消费授权码（/oauth/token 用）：一次性删除 + redirect_uri/PKCE 校验。
     */
    public CodePayload consumeCode(String code, String clientId, String redirectUri, String codeVerifier) {
        String key = CODE_PREFIX + code;
        String json = redis.opsForValue().get(key);
        if (json == null) {
            throw new BusinessException("授权码无效或已过期");
        }
        // 一次性：先删再用（并发重放防护）
        redis.delete(key);
        CodePayload payload = JSONUtil.toBean(json, CodePayload.class);
        if (!payload.getClientId().equals(clientId) || !payload.getRedirectUri().equals(redirectUri)) {
            throw new BusinessException("授权码与 client/redirect_uri 不匹配");
        }
        // PKCE 校验：S256 = BASE64URL(SHA256(verifier))；plain = verifier 即 challenge
        if (payload.getCodeChallenge() != null) {
            if (codeVerifier == null || codeVerifier.isBlank()) {
                throw new BusinessException("缺少 code_verifier（PKCE）");
            }
            String expected = "plain".equals(payload.getCodeChallengeMethod())
                    ? codeVerifier
                    : s256(codeVerifier);
            if (!expected.equals(payload.getCodeChallenge())) {
                throw new BusinessException("PKCE 校验失败：code_verifier 不匹配");
            }
        }
        return payload;
    }

    /** BASE64URL(SHA256(verifier)) */
    private String s256(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new BusinessException("PKCE 计算失败");
        }
    }

    /** 应用角色标签（签发 access token 的 roles claim） */
    public List<String> rolesOf(String clientId, Long userId) {
        return userRoles(clientId, userId);
    }

    private List<String> userRoles(String clientId, Long userId) {
        AssignVO assign = assignService.checkLoginAccess(clientId, userId);
        return assign != null && assign.getRoles() != null ? assign.getRoles() : List.of();
    }
}
