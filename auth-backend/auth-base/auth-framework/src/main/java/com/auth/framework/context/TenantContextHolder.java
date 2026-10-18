package com.auth.framework.context;

/**
 * 租户上下文（ThreadLocal）—— 多租户隔离维度的请求级载体。
 * <p>
 * SaaS 租户由登录租户代码、JWT tenantId claim 或平台超管显式切换确定。
 * 未建立上下文的引导/兼容场景仍落在演示租户 1000，但任何业务请求都应尽早显式写入。
 * <p>
 * 写入方：auth-platform-system 的 JwtAuthFilter（从 JWT claims 加载），
 * 请求结束（filter finally）调用 {@link #clear()} 清理，防止线程复用串上下文。
 * 读取方：租户插件（{@code TenantLineInnerInterceptor} 取 tenantId）与审计填充器。
 * <p>
 * 放置在 framework 而非 system 的原因：MybatisPlusConfig / 拦截器位于 framework，
 * 依赖方向只能向下（common ← framework ← system），system 写入、framework 读取。
 */
public final class TenantContextHolder {

    /** 默认演示租户 ID（兼容既有初始化数据） */
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

    /**
     * 在指定租户上下文内执行任务，并在结束后恢复调用前上下文。
     *
     * @param tenantId 租户 ID
     * @param action   任务
     * @param <T>      返回类型
     * @return 任务结果
     */
    public static <T> T callWithTenant(Long tenantId, java.util.function.Supplier<T> action) {
        TenantContext previous = HOLDER.get();
        try {
            set(new TenantContext(tenantId));
            return action.get();
        } finally {
            if (previous == null) {
                clear();
            } else {
                set(previous);
            }
        }
    }

    /**
     * 在指定租户上下文内执行无返回任务，并在结束后恢复调用前上下文。
     *
     * @param tenantId 租户 ID
     * @param action   任务
     */
    public static void runWithTenant(Long tenantId, Runnable action) {
        callWithTenant(tenantId, () -> {
            action.run();
            return null;
        });
    }
}
