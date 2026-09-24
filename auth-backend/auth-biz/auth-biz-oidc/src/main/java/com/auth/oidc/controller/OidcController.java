package com.auth.oidc.controller;

import com.auth.app.entity.AppClient;
import com.auth.app.service.AppClientService;
import com.auth.common.exception.BusinessException;
import com.auth.common.result.Result;
import com.auth.framework.security.SecurityUtils;
import com.auth.idm.entity.IdmUser;
import com.auth.idm.mapper.IdmUserMapper;
import com.auth.oidc.service.OidcAuthorizeService;
import com.auth.oidc.service.OidcTokenService;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC 协议端点 —— 语义对标 RFC 6749（授权码 + PKCE）/ OpenID Connect Core 1.0。
 * <p>
 * 端点一览（均在 SecurityConfig 白名单，认证自管）：
 * <ul>
 *   <li>GET  /.well-known/openid-configuration — 发现文档</li>
 *   <li>GET  /.well-known/jwks.json — 验签公钥（RS256）</li>
 *   <li>POST /oauth/authorize — 授权确认（带管理端 JWT；SPA 登录页调用）</li>
 *   <li>POST /oauth/token — 授权码换令牌（PKCE）/ client_credentials 服务令牌</li>
 *   <li>GET  /oauth/userinfo — 用户信息（RS256 access token）</li>
 *   <li>POST /oauth/logout — 单点登出（令牌拉黑）</li>
 * </ul>
 */
@Tag(name = "OIDC 协议端点")
@RestController
@RequiredArgsConstructor
public class OidcController {

    private final OidcAuthorizeService authorizeService;
    private final OidcTokenService tokenService;
    private final AppClientService clientService;
    private final IdmUserMapper userMapper;

    /** 签发者标识（写入 iss claim 与发现文档） */
    @Value("${auth.oidc.issuer:http://localhost:8082/api}")
    private String issuer;

    /**
     * OIDC 发现文档。
     */
    @Operation(summary = "发现文档")
    @GetMapping("/.well-known/openid-configuration")
    public Map<String, Object> discovery() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("issuer", issuer);
        doc.put("authorization_endpoint", issuer + "/oauth/authorize");
        doc.put("token_endpoint", issuer + "/oauth/token");
        doc.put("userinfo_endpoint", issuer + "/oauth/userinfo");
        doc.put("jwks_uri", issuer + "/.well-known/jwks.json");
        doc.put("logout_endpoint", issuer + "/oauth/logout");
        doc.put("response_types_supported", List.of("code"));
        doc.put("grant_types_supported", List.of("authorization_code", "client_credentials"));
        doc.put("code_challenge_methods_supported", List.of("S256", "plain"));
        doc.put("subject_types_supported", List.of("public"));
        doc.put("id_token_signing_alg_values_supported", List.of("RS256"));
        doc.put("scopes_supported", List.of("openid", "profile"));
        doc.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post"));
        doc.put("claims_supported", List.of(
                "sub", "iss", "aud", "exp", "iat", "jti", "nonce",
                "name", "preferred_username", "employee_no", "org_id", "tenant_id", "roles", "scope"));
        return doc;
    }

    /**
     * JWKS（RS256 验签公钥）。
     */
    @Operation(summary = "JWKS")
    @GetMapping({"/.well-known/jwks.json", "/oauth/jwks"})
    public Map<String, Object> jwks() {
        return Map.of("keys", tokenService.jwks());
    }

    /**
     * 授权确认（统一登录页在用户登录/同意后调用，Authorization 为管理端 JWT）。
     * 返回 redirectUri 由前端执行 302 跳转，对业务系统与标准重定向流一致。
     */
    @Operation(summary = "授权（授权码签发）")
    @PostMapping("/oauth/authorize")
    public Result<OidcAuthorizeService.AuthorizeResult> authorize(
            @RequestBody OidcAuthorizeService.AuthorizeRequest request, HttpServletRequest servletRequest) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null || userId == 0L) {
            throw new BusinessException("未登录，请先在统一登录页完成认证");
        }
        return Result.ok(authorizeService.authorize(userId, request, servletRequest.getRemoteAddr()));
    }

    /**
     * 令牌端点（RFC 6749 §3.2）。
     * <ul>
     *   <li>grant_type=authorization_code：授权码 + PKCE 换 access/id token（机头式客户端认证）；</li>
     *   <li>grant_type=client_credentials：应用换取服务间令牌（scope=service）。</li>
     * </ul>
     * 应用认证支持 Basic（client_id:client_secret）与表单两种方式。
     */
    @Operation(summary = "令牌端点")
    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> token(@RequestHeader(value = "Authorization", required = false) String basicAuth,
                                     @RequestParam("grant_type") String grantType,
                                     @RequestParam(value = "client_id", required = false) String clientId,
                                     @RequestParam(value = "client_secret", required = false) String clientSecret,
                                     @RequestParam(value = "code", required = false) String code,
                                     @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                                     @RequestParam(value = "code_verifier", required = false) String codeVerifier) {
        // Basic 认证优先（client_id:client_secret base64）
        if (basicAuth != null && basicAuth.startsWith("Basic ")) {
            String decoded = new String(java.util.Base64.getDecoder().decode(basicAuth.substring(6)),
                    java.nio.charset.StandardCharsets.UTF_8);
            int idx = decoded.indexOf(':');
            if (clientId == null && idx > 0) {
                clientId = decoded.substring(0, idx);
                clientSecret = decoded.substring(idx + 1);
            }
        }
        if (clientId == null || clientSecret == null) {
            throw new BusinessException("缺少客户端凭证（client_id/client_secret）");
        }
        if (!clientService.verifySecret(clientId, clientSecret)) {
            throw new BusinessException("客户端凭证无效");
        }
        AppClient client = clientService.loadEnabledClient(clientId);
        long validity = client.getAccessTokenValidity() == null ? 7200 : client.getAccessTokenValidity();

        Map<String, Object> resp = new LinkedHashMap<>();
        if ("client_credentials".equals(grantType)) {
            // 服务间令牌（应用级启停/用户同步 API 用）
            resp.put("access_token", tokenService.issueServiceToken(clientId, validity));
            resp.put("token_type", "Bearer");
            resp.put("expires_in", validity);
            resp.put("scope", "service");
            return resp;
        }
        if (!"authorization_code".equals(grantType)) {
            throw new BusinessException("不支持的 grant_type：%s", grantType);
        }
        // 授权码 + PKCE
        OidcAuthorizeService.CodePayload payload = authorizeService.consumeCode(
                code, clientId, redirectUri, codeVerifier);
        IdmUser user = userMapper.selectById(payload.getUserId());
        if (user == null) {
            throw new BusinessException("授权码对应用户不存在");
        }
        List<String> roles = authorizeService.rolesOf(clientId, payload.getUserId());
        resp.put("access_token", tokenService.issueAccessToken(clientId, user.getId(), user.getUsername(),
                user.getRealName(), user.getEmployeeNo(), user.getOrgId(), user.getTenantId(),
                roles, payload.getScope(), validity));
        resp.put("token_type", "Bearer");
        resp.put("expires_in", validity);
        resp.put("scope", payload.getScope() == null ? "openid profile" : payload.getScope());
        // scope 含 openid 时签发 id_token（标准声明 + nonce 回显）
        if (payload.getScope() == null || payload.getScope().contains("openid")) {
            resp.put("id_token", tokenService.issueIdToken(clientId, user.getId(), user.getUsername(),
                    user.getRealName(), user.getEmployeeNo(), user.getTenantId(),
                    payload.getNonce(), Math.min(validity, 3600)));
        }
        return resp;
    }

    /**
     * 用户信息端点（RS256 access token，Bearer）。
     */
    @Operation(summary = "用户信息")
    @GetMapping("/oauth/userinfo")
    public Map<String, Object> userinfo(@RequestHeader("Authorization") String authorization) {
        Claims claims = requireAccessToken(authorization);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("sub", claims.getSubject());
        putIfPresent(info, "name", claims.get("name"));
        putIfPresent(info, "preferred_username", claims.get("username"));
        putIfPresent(info, "employee_no", claims.get("employee_no"));
        putIfPresent(info, "org_id", claims.get("org_id"));
        putIfPresent(info, "tenant_id", claims.get("tenantId"));
        putIfPresent(info, "scope", claims.get("scope"));
        Object roles = claims.get("roles");
        if (roles != null) {
            info.put("roles", roles);
        }
        return info;
    }

    /**
     * 单点登出（令牌侧）：传入 RS256 access token，中心按 jti 拉黑；
     * front/back-channel 广播由各业务系统按需对接（文档说明），本期不强制。
     */
    @Operation(summary = "单点登出")
    @PostMapping("/oauth/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authorization) {
        Claims claims = requireAccessToken(authorization);
        tokenService.revoke(claims);
        return Result.ok();
    }

    /** 解析并校验 Bearer RS256 access token（黑名单校验） */
    private Claims requireAccessToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException("缺少 Bearer 令牌");
        }
        Claims claims;
        try {
            claims = tokenService.parse(authorization.substring(7).trim());
        } catch (Exception e) {
            throw new BusinessException("令牌无效或已过期");
        }
        if (!"access".equals(claims.get(OidcTokenService.CLAIM_TOKEN_USE))) {
            throw new BusinessException("令牌用途不符（需要 access token）");
        }
        if (tokenService.isRevoked(claims.getId())) {
            throw new BusinessException("令牌已登出失效");
        }
        return claims;
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

}
