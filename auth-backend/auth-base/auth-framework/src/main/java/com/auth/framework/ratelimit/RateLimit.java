package com.auth.framework.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解 —— 基于 Redis 滑动窗口（商业系统并发防护）。
 * <p>
 * 场景：报表/统计类重查询防刷、外部对接接口保护、防止并发穿透拖垮数据库。
 * 与 {@code @Idempotent} 分工：幂等管"同一请求重复提交"，限流管"单位时间总量"。
 * <p>
 * 用法：
 * <pre>{@code
 * @RateLimit(windowSeconds = 10, maxRequests = 5)  // 每 10 秒最多 5 次（按用户维度）
 * @GetMapping("/statistics/daily")
 * public Result<...> daily(...) { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 统计窗口（秒） */
    int windowSeconds() default 10;

    /** 窗口内最大请求数 */
    int maxRequests() default 20;

    /** 限流维度：USER 按登录用户（默认），IP 按来源地址（公开接口用），GLOBAL 全局 */
    LimitType limitType() default LimitType.USER;

    /** 触发限流提示 */
    String message() default "请求过于频繁，请稍后再试";

    /** 限流维度 */
    enum LimitType { USER, IP, GLOBAL }
}
