package com.auth.framework.ratelimit;

import com.auth.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.List;

/**
 * 限流切面 —— {@link RateLimit} 的实现，Redis + ZSET 滑动窗口。
 * <p>
 * 滑动窗口优于固定窗口：临界点（窗口边缘）不会出现双倍放行。
 * 原子性由 Lua 保证（读取过期成员 + 清理 + 计数 + 写入一次完成），
 * Redis 不可用时降级放行（限流是保护手段，不阻断主流程）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String KEY_PREFIX = "ratelimit:";

    /** Lua：ZREMRANGEBYSCORE 清理窗口外 -> ZCARD 计数 -> 未超则 ZADD 本次并设 TTL */
    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1])
            local count = redis.call('ZCARD', KEYS[1])
            if count < tonumber(ARGV[2]) then
                redis.call('ZADD', KEYS[1], ARGV[3], ARGV[4])
                redis.call('PEXPIRE', KEYS[1], ARGV[5])
                return 0
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        String dimensionKey = switch (rateLimit.limitType()) {
            case USER -> "u:" + com.auth.framework.security.SecurityUtils.getCurrentUserId();
            case IP -> "ip:" + (attrs != null ? clientIp(attrs.getRequest()) : "unknown");
            case GLOBAL -> "global";
        };
        String key = KEY_PREFIX + dimensionKey + ":" + joinPoint.getSignature().toShortString();

        try {
            long now = System.currentTimeMillis();
            Long rejected = redisTemplate.execute(
                    SLIDING_WINDOW_SCRIPT,
                    List.of(key),
                    // ARGV: 窗口起点ms / 最大次数 / score=now / member=now+随机后缀 / TTL ms
                    String.valueOf(now - rateLimit.windowSeconds() * 1000L),
                    String.valueOf(rateLimit.maxRequests()),
                    String.valueOf(now),
                    now + "-" + Thread.currentThread().getId() + "-" + (int) (Math.random() * 10000),
                    String.valueOf(rateLimit.windowSeconds() * 1000L + 1000));

            if (rejected != null && rejected == 1) {
                log.warn("[限流拦截] {} 超过 {}/{}s", key, rateLimit.maxRequests(), rateLimit.windowSeconds());
                throw new BusinessException(rateLimit.message());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Redis 异常降级放行：限流失效不能导致业务不可用
            log.warn("[限流降级] Redis 不可用，放行 {}", key, e);
        }
        return joinPoint.proceed();
    }

    /** 取真实客户端 IP（反代场景取 X-Forwarded-For 首位） */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
