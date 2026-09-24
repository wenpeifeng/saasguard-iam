package com.auth.framework.idempotent;

import com.auth.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * 幂等切面 —— {@link Idempotent} 的实现。
 * <p>
 * 指纹 = 用户ID + 请求路径 + 方法 + 参数 MD5，保证"同一人同一操作同一参数"才被视为重复；
 * Redis SET NX EX 原子抢占，窗口过期自动释放，无需人工清理。
 * 非_HTTP 上下文（定时任务内部调用）不拦截，直接放行。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private static final String KEY_PREFIX = "idem:";

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String fingerprint = buildFingerprint(joinPoint);
        if (fingerprint == null) {
            // 无 HTTP 上下文（内部调用），幂等无意义，直接执行
            return joinPoint.proceed();
        }

        String key = KEY_PREFIX + fingerprint;
        Boolean first = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(idempotent.expireSeconds()));

        if (Boolean.TRUE.equals(first)) {
            try {
                return joinPoint.proceed();
            } catch (Throwable t) {
                // 业务失败立即释放锁，允许用户马上重试修正
                redisTemplate.delete(key);
                throw t;
            }
        }
        log.warn("[幂等拦截] {} {}", fingerprint, idempotent.message());
        throw new BusinessException(idempotent.message());
    }

    /** 构造指纹：userId + uri + 方法签名 + 参数哈希 */
    private String buildFingerprint(ProceedingJoinPoint joinPoint) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        Long userId = com.auth.framework.security.SecurityUtils.getCurrentUserId();
        String args = java.util.Arrays.toString(joinPoint.getArgs());
        String raw = userId + "|" + request.getMethod() + "|" + request.getRequestURI()
                + "|" + joinPoint.getSignature().toShortString() + "|" + args;
        return md5(raw);
    }

    private String md5(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // 哈希失败退化为原文长度截断，保证幂等仍可用
            return String.valueOf(raw.hashCode());
        }
    }
}
