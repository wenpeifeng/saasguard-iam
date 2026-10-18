package com.auth.app.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 服务令牌在线态注册表。
 * <p>
 * JWT 仍负责完整性和有效期；Redis 在线键负责应用停用、密钥重置后的即时撤销。
 * 键中只保存令牌 SHA-256，不把原始凭证暴露给 Redis 运维视图。
 */
@Component
@RequiredArgsConstructor
public class ServiceTokenSessionRegistry {

    private static final String PREFIX = "oidc:service-session:";

    private final StringRedisTemplate redis;

    /** 登记已签发的服务令牌。 */
    public void register(Long tenantId, String clientId, String token, long validitySeconds) {
        redis.opsForValue().set(key(tenantId, clientId, token), "1", Duration.ofSeconds(validitySeconds));
    }

    /** 令牌是否仍处于在线态。 */
    public boolean isActive(Long tenantId, String clientId, String token) {
        return Boolean.TRUE.equals(redis.hasKey(key(tenantId, clientId, token)));
    }

    /** 撤销某租户应用的全部服务令牌（应用停用或客户端密钥重置）。 */
    public void invalidateClient(Long tenantId, String clientId) {
        var keys = redis.keys(prefix(tenantId, clientId) + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private String key(Long tenantId, String clientId, String token) {
        return prefix(tenantId, clientId) + sha256(token);
    }

    private String prefix(Long tenantId, String clientId) {
        return PREFIX + tenantId + ":" + clientId + ":";
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算服务令牌摘要", e);
        }
    }
}
