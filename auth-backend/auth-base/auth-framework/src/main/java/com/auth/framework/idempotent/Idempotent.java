package com.auth.framework.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等注解 —— 防重复提交（商业系统核心防护）。
 * <p>
 * 场景：用户连点两次"审核/提交"、网络重试导致同一请求重复到达。
 * 原理：同一指纹（方法+参数+用户）在 {@link #expireSeconds()} 内只有第一次放行，
 * 重复请求直接抛业务异常"请勿重复提交"。
 * <p>
 * 用法（加在写操作 Controller/Service 方法上）：
 * <pre>{@code
 * @Idempotent(expireSeconds = 5)
 * @PutMapping("/{id}/audit")
 * public Result<Void> audit(@PathVariable Long id) { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** 幂等窗口（秒）：窗口内相同指纹只放行一次，默认 5 秒 */
    int expireSeconds() default 5;

    /** 提示文案 */
    String message() default "请勿重复提交，稍候再试";
}
