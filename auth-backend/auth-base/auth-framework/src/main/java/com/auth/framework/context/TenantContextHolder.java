package com.auth.framework.context;

/**
 * 租户上下文（ThreadLocal）—— 多租户隔离维度的请求级载体。
 * <p>
 * 本期为"预留租户"模型：登录流程不暴露租户概念，所有请求落在默认租户 1000（与 ERP 对齐），
 * 但表结构/唯一索引/插件机制完整具备租户维度，后续子公司或外部企业接入时
 * 只需在登录环节识别租户并写入本上下文即可，无需迁移存量数据。
 * <p>
 * 写入方：auth-platform-system 的 JwtAuthFilter（从 JWT claims 加载），
 * 请求结束（filter finally）调用 {@link #clear()} 清理，防止线程复用串上下文。
 * 读取方：租户插件（{@code TenantLineInnerInterceptor} 取 tenantId）与审计填充器。
 * <p>
 * 放置在 framework 而非 system 的原因：MybatisPlusConfig / 拦截器位于 framework，
 * 依赖方向只能向下（common ← framework ← system），system 写入、framework 读取。
 */
public final class TenantContextHolder {

    /** 默认租户 ID（本期唯一租户，与各表 tenant_id 列 DEFAULT 一致） */
    public static final long DEFAULT_TENANT_ID = 1000L;

    /** 租户上下文快照：tenantId 来自令牌 */
    public record TenantContext(Long tenantId) {
    }

    private static final ThreadLocal<TenantContext> HOLDER = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    /** 写入当前请求的租户上下文（JwtAuthFilter 认证成功后调用） */
    public static void set(TenantContext context) {
        HOLDER.set(context);
    }

    /** 读取原始上下文（可能为 null：未登录或非 HTTP 线程） */
    public static TenantContext get() {
        return HOLDER.get();
    }

    /** 清理上下文（请求结束必须调用，防止线程池串数据） */
    public static void clear() {
        HOLDER.remove();
    }

    /** 当前租户 ID；无上下文返回默认租户 1000 */
    public static Long getTenantId() {
        TenantContext ctx = HOLDER.get();
        return ctx != null && ctx.tenantId() != null ? ctx.tenantId() : DEFAULT_TENANT_ID;
    }
}
