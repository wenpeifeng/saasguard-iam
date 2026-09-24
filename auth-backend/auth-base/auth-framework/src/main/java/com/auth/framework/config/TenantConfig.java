package com.auth.framework.config;

import com.auth.framework.context.TenantContextHolder;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;
import java.util.Set;

/**
 * 多租户插件配置（预留租户模型）。
 * <p>
 * {@code TenantLineInnerInterceptor} 对所有 SQL（含 XML 手写 SQL）自动追加
 * {@code WHERE tenant_id = 当前租户}、INSERT 自动补租户列 —— 业务代码零侵入。
 * 本期只有默认租户 1000，机制就位后未来放开多租户无需改业务代码。
 * <p>
 * 共享表白名单（不追加租户条件，全租户可见）：
 * <ul>
 *   <li>平台级 RBAC（sys_role / sys_user_role / sys_menu）：超级管理员/审计员是平台概念；</li>
 *   <li>应用注册表（oauth_client 及角色声明）：应用是平台登记的门，租户只影响"谁进哪扇门"（分配表隔离）；</li>
 *   <li>令牌签发密钥表（token_key）：全平台统一 JWKS；</li>
 *   <li>Flyway 历史表（防手滑误伤）。</li>
 * </ul>
 * 拿不准的表一律按隔离处理（追加租户条件），有疑问在此登记共享。
 */
@Configuration
public class TenantConfig {

    /**
     * 租户插件忽略的共享表（小写）。
     * 新增表时判定原则：描述"平台自身/全局资源"的进白名单，描述"租户的人和授权"的隔离。
     */
    public static final Set<String> TENANT_SHARED_TABLES = Set.of(
            // 平台级 RBAC（认证中心自身的管理权限，超管/审计员为平台概念）
            "sys_role", "sys_user_role", "sys_menu",
            // 应用注册与角色声明（平台登记的"门"）
            "app_client", "app_client_role",
            // 令牌签名密钥（全平台统一 JWKS）
            "token_key",
            // Flyway 历史表
            "flyway_schema_history"
    );

    /**
     * 租户条件处理器：租户值取 TenantContextHolder（无上下文默认 1000）。
     *
     * @return TenantLineHandler（供 MybatisPlusInterceptor 装配租户插件）
     */
    @Bean
    public TenantLineHandler tenantLineHandler() {
        return new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new LongValue(TenantContextHolder.getTenantId());
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // 系统库（information_schema 等）与 Flyway 历史表没有 tenant_id 列，必须跳过
                String normalized = normalize(tableName);
                if (normalized.startsWith("information_schema")
                        || normalized.startsWith("performance_schema")
                        || normalized.startsWith("mysql")
                        || normalized.startsWith("sys.")) {
                    return true;
                }
                return isTenantSharedTable(tableName);
            }
        };
    }

    /** 表名归一化：去反引号/空白并转小写 */
    private static String normalize(String tableName) {
        if (tableName == null) {
            return "";
        }
        return tableName.replace("`", "").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 是否租户共享表（不追加 tenant_id 条件）。
     *
     * @param tableName SQL 中出现的表名（可带反引号）
     * @return true 表示共享，租户插件跳过
     */
    public static boolean isTenantSharedTable(String tableName) {
        return TENANT_SHARED_TABLES.contains(normalize(tableName));
    }
}
