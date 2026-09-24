package com.auth.oidc.service;

import com.auth.oidc.entity.TokenKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OIDC 令牌服务 —— RS256 签发/验签 access token、id_token、service token。
 * <p>
 * 单点登出（令牌侧）：登出时把 jti 写入 Redis 黑名单（TTL=剩余有效期），
 * userinfo/service 过滤器校验"签名有效 + 未拉黑"。
 * <p>
 * 依赖方向：本类在 auth-biz-oidc，供 OIDC 端点与 ServiceTokenFilter 使用；
 * 管理端 HS256 令牌体系（JwtTokenProvider）与本类互不相干，勿混用。
 */
@Service
@RequiredArgsConstructor
public class OidcTokenService {

    /** 登出黑名单键前缀：oidc:blacklist:{jti} */
    public static final String BLACKLIST_PREFIX = "oidc:blacklist:";

    /** 令牌用途 claim：access-用户访问令牌 service-服务间令牌 */
    public static final String CLAIM_TOKEN_USE = "token_use";

    private final TokenKeyService tokenKeyService;
    private final StringRedisTemplate redis;

    /**
     * 签发 OIDC access token（按应用，roles claim 只含该应用的角色标签）。
     *
     * @param clientId  应用 client_id（写入 aud）
     * @param userId    用户 ID（sub）
     * @param username  登录名
     * @param realName  姓名
     * @param employeeNo 工号
     * @param orgId     组织 ID（ABAC 预留 claim）
     * @param tenantId  租户 ID
     * @param roles     该应用角色标签
     * @param scope     授权范围
     * @param validity  有效期（秒）
     */
    public String issueAccessToken(String clientId, Long userId, String username, String realName,
                                   String employeeNo, Long orgId, Long tenantId, List<String> roles,
                                   String scope, long validity) {
        return sign(clientId, userId, tenantId, validity, Map.of(
                CLAIM_TOKEN_USE, "access",
                "username", username,
                "name", realName,
                "employee_no", employeeNo,
                "org_id", orgId == null ? 0 : orgId,
                "scope", scope == null ? "openid profile" : scope,
                "roles", roles));
    }

    /**
     * 签发 OIDC id_token（OpenID Connect Core 1.0 标准声明 + 业务扩展）。
     */
    public String issueIdToken(String clientId, Long userId, String username, String realName,
                               String employeeNo, Long tenantId, String nonce, long validity) {
        java.util.HashMap<String, Object> claims = new java.util.HashMap<>();
        claims.put(CLAIM_TOKEN_USE, "id");
        claims.put("preferred_username", username);
        claims.put("name", realName);
        claims.put("employee_no", employeeNo);
        if (nonce != null && !nonce.isBlank()) {
            claims.put("nonce", nonce);
        }
        return sign(clientId, userId, tenantId, validity, claims);
    }

    /**
     * 签发服务间令牌（client_credentials 模式）：token_use=service，scope=service。
     */
    public String issueServiceToken(String clientId, long validity) {
        return sign(clientId, null, null, validity, Map.of(
                CLAIM_TOKEN_USE, "service",
                "scope", "service"));
    }

    /**
     * 验签并返回载荷（签名有效 + 未过期），黑名单由调用方按用途校验。
     *
     * @throws io.jsonwebtoken.JwtException 令牌无效或过期
     */
    public Claims parse(String token) {
        String kid = readKid(token);
        TokenKey key = kid != null ? tokenKeyService.byKid(kid) : null;
        if (key == null) {
            key = tokenKeyService.activeKey();
        }
        PublicKey publicKey = tokenKeyService.parsePublicKey(key.getPublicKey());
        return Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
    }

    /** 手工解码头段取 kid（不验签，kid 仅用于选公钥，真实验签在 parse 内完成） */
    private String readKid(String token) {
        try {
            String headerJson = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]),
                    java.nio.charset.StandardCharsets.UTF_8);
            return cn.hutool.json.JSONUtil.parseObj(headerJson).getStr("kid");
        } catch (Exception e) {
            return null;
        }
    }

    /** jti 是否已登出拉黑 */
    public boolean isRevoked(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + jti));
    }

    /** 登出：按剩余有效期拉黑 jti */
    public void revoke(Claims claims) {
        long remain = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remain > 0) {
            redis.opsForValue().set(BLACKLIST_PREFIX + claims.getId(), "1", Duration.ofMillis(remain));
        }
    }

    /** JWKS（当前签发密钥的公钥，kid/n/e base64url） */
    public List<Map<String, Object>> jwks() {
        TokenKey key = tokenKeyService.activeKey();
        RSAPublicKey rsa = (RSAPublicKey) tokenKeyService.parsePublicKey(key.getPublicKey());
        Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
        return List.of(Map.of(
                "kty", "RSA",
                "kid", key.getKid(),
                "use", "sig",
                "alg", "RS256",
                "n", urlEncoder.encodeToString(rsa.getModulus().toByteArray()),
                "e", urlEncoder.encodeToString(rsa.getPublicExponent().toByteArray())));
    }

    /** RS256 签名（统一 kid/iss/sub/exp/iat/jti） */
    private String sign(String aud, Long sub, Long tenantId, long validitySeconds,
                        Map<String, Object> extraClaims) {
        TokenKey key = tokenKeyService.activeKey();
        PrivateKey privateKey = tokenKeyService.parsePrivateKey(key.getPrivateKey());
        Date now = new Date();
        var builder = Jwts.builder()
                .header().keyId(key.getKid()).and()
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validitySeconds * 1000));
        if (sub != null) {
            builder.subject(String.valueOf(sub));
        }
        if (aud != null) {
            builder.audience().add(aud).and();
        }
        if (tenantId != null) {
            builder.claim("tenantId", tenantId);
        }
        extraClaims.forEach(builder::claim);
        return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
    }
}
