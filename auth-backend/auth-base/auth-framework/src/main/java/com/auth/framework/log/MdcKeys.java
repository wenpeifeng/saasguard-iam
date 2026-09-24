package com.auth.framework.log;

/**
 * 日志 MDC 键名约定 —— 全系统日志上下文字段的唯一事实来源。
 * <p>
 * 写入方：{@link TraceIdFilter}（traceId，请求最先执行）与 auth-platform-system 的
 * JwtAuthFilter（userId，令牌解析成功后）；
 * 读取方：logback-spring.xml（控制台 pattern 的 %X{traceId} 与 JSON encoder 的
 * includeMdcKeyName）。改键名必须同步 logback-spring.xml 与日志规范。
 * <p>
 * 放在 framework 而非 system：JwtAuthFilter（system）依赖本常量，依赖方向向下合法；
 * 反之 framework 不得引用 system。
 */
public final class MdcKeys {

    /** 请求链路 ID（16 位十六进制；上游传入 X-Trace-Id 时沿用） */
    public static final String TRACE_ID = "traceId";

    /** 登录用户 ID（JWT principal） */
    public static final String USER_ID = "userId";

    /** 租户 ID（来自 JWT claims，预留租户模型下恒为默认 1000） */
    public static final String TENANT_ID = "tenantId";

    /** 登录用户名（employee 工号在 idm 侧语义为 username） */
    public static final String USERNAME = "username";

    /** traceId 的 HTTP 头名（请求可携带用于链路透传；响应必回显便于前端/运维按 ID 检索日志） */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private MdcKeys() {
    }
}
