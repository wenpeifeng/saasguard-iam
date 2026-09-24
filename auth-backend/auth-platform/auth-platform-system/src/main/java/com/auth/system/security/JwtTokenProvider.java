package com.auth.system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 令牌提供者（生成 / 解析 / 校验 / Redis 会话管理）—— 认证中心管理端登录态。
 * <p>
 * 会话策略（JWT + Redis 双层控制，支持强制下线）：
 * <ul>
 *   <li>签发：HS256 签名，subject 存用户 ID，jti 存随机 ID；</li>
 *   <li>在线态：登录时写 Redis 键 {@code token:{userId}:{token}}（TTL=令牌有效期），
 *       过滤器校验"签名有效 + 键存在"才放行 —— 键被删除即令牌失效
 *       （全局停用/离职时删除全部在线键可实现一键全端下线）；</li>
 *   <li>黑名单：登出时写 {@code auth:blacklist:{token}}（TTL=剩余有效期），双保险。</li>
 * </ul>
 * 密钥与有效期支持配置项 auth.jwt.secret / auth.jwt.expire-minutes 覆盖（默认值仅供开发）。
 * <p>
 * 注意：本类签发的是认证中心管理端自身的登录令牌；P2 的 OIDC access token
 * 由 auth-biz-oidc 按应用签发（RS256/JWKS），两者用途与密钥体系不同，勿混用。
 */
@Component
public class JwtTokenProvider {

    /** 在线令牌键前缀：token:{userId}:{token} */
    public static final String TOKEN_KEY_PREFIX = "token:";

    /** 黑名单键前缀：auth:blacklist:{token} */
    public static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    /** 默认租户（预留租户模型，与 TenantContextHolder.DEFAULT_TENANT_ID 一致） */
    private static final long DEFAULT_TENANT_ID = 1000L;

    /** 租户 claim 名 */
    private static final String CLAIM_TENANT_ID = "tenantId";

    /** 用户名 claim 名（日志与展示用） */
    private static final String CLAIM_USERNAME = "username";

    private final SecretKey secretKey;

    /** 令牌有效期（毫秒） */
    private final long expireMillis;

    private final StringRedisTemplate redis;

    public JwtTokenProvider(@Value("${auth.jwt.secret:auth-center-dev-jwt-secret-key-change-me-in-production-0123456789abcdef}")
                            String secret,
                            @Value("${auth.jwt.expire-minutes:480}") long expireMinutes,
                            StringRedisTemplate redis) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = Duration.ofMinutes(expireMinutes).toMillis();
        this.redis = redis;
    }

    /**
     * 签发管理端登录令牌并写入 Redis 在线会话。
     *
     * @param userId   用户 ID（idm_user.id）
     * @param tenantId 租户 ID（null 按默认 1000 处理）
     * @param username 登录名（写入 claim，便于日志/展示）
     * @return 已签名的 JWT 字符串
     */
    public String createToken(Long userId, Long tenantId, String username) {
        Date now = new Date();
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TENANT_ID, tenantId == null ? DEFAULT_TENANT_ID : tenantId)
                .claim(CLAIM_USERNAME, username)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(secretKey)
                .compact();
        redis.opsForValue().set(onlineKey(userId, token), "1", Duration.ofMillis(expireMillis));
        return token;
    }

    /**
     * 解析令牌中的用户 ID。
     *
     * @param token JWT 字符串
     * @return 用户 ID
     * @throws JwtException 令牌无效或已过期时抛出，由调用方决定 401
     */
    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    /**
     * 解析令牌中的租户 ID。
     *
     * @param token JWT 字符串
     * @return 租户 ID；令牌无该 claim 时返回默认租户 1000
     * @throws JwtException 令牌无效或已过期时抛出
     */
    public Long getTenantId(String token) {
        Object value = parse(token).get(CLAIM_TENANT_ID);
        return value instanceof Number number ? number.longValue() : DEFAULT_TENANT_ID;
    }

    /**
     * 解析令牌中的用户名。
     *
     * @param token JWT 字符串
     * @return 用户名；无 claim 时返回 null
     * @throws JwtException 令牌无效或已过期时抛出
     */
    public String getUsername(String token) {
        Object value = parse(token).get(CLAIM_USERNAME);
        return value instanceof String s ? s : null;
    }

    /**
     * 校验令牌综合有效性：签名未过期 + 未被拉黑 + Redis 在线键仍存在。
     *
     * @param token JWT 字符串
     * @return true 表示可用
     */
    public boolean isValid(String token) {
        try {
            Long userId = getUserId(token);
            if (Boolean.TRUE.equals(redis.hasKey(blacklistKey(token)))) {
                return false;
            }
            return Boolean.TRUE.equals(redis.hasKey(onlineKey(userId, token)));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 登出：删除在线会话键并写入黑名单（TTL 取令牌剩余有效期，自动清理）。
     *
     * @param userId 用户 ID
     * @param token  JWT 字符串
     */
    public void invalidate(Long userId, String token) {
        long remain = remainMillis(token);
        if (remain <= 0) {
            return;
        }
        redis.delete(onlineKey(userId, token));
        redis.opsForValue().set(blacklistKey(token), "1", Duration.ofMillis(remain));
    }

    /**
     * 下线用户全部在线会话（"一键全系统失效"的令牌侧收口）：
     * 删除该用户所有 {@code token:{userId}:*} 在线键 —— 键被删即令牌失效
     * （过滤器校验"签名有效 + 键存在"）。离职/全局停用时调用。
     *
     * @param userId 用户 ID
     * @return 删除的会话键数量
     */
    public long invalidateAllUserSessions(Long userId) {
        java.util.Set<String> keys = redis.keys(TOKEN_KEY_PREFIX + userId + ":*");
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        Long deleted = redis.delete(keys);
        return deleted == null ? 0 : deleted;
    }

    /**
     * 计算令牌剩余有效毫秒数（解析失败按 0 处理）。
     *
     * @param token JWT 字符串
     * @return 剩余毫秒，已过期或非法返回 0
     */
    public long remainMillis(String token) {
        try {
            Date expiration = Jwts.parser().verifyWith(secretKey).build()
                    .parseSignedClaims(token).getPayload().getExpiration();
            return Math.max(0, expiration.getTime() - System.currentTimeMillis());
        } catch (JwtException | IllegalArgumentException e) {
            return 0;
        }
    }

    /** 拼接在线会话键：token:{userId}:{token} */
    private String onlineKey(Long userId, String token) {
        return TOKEN_KEY_PREFIX + userId + ":" + token;
    }

    /** 拼接黑名单键：auth:blacklist:{token} */
    private String blacklistKey(String token) {
        return BLACKLIST_KEY_PREFIX + token;
    }

    /** 解析并验签令牌，返回 claims 载荷 */
    private Claims parse(String token) {
        return Jwts.parser().verifyWith(secretKey).build()
                .parseSignedClaims(token).getPayload();
    }
}
