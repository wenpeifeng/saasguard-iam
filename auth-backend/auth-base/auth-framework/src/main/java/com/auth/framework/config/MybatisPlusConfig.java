package com.auth.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.auth.framework.context.TenantContextHolder;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置。
 * <ul>
 *   <li>拦截器链（顺序敏感）：多租户 → 分页。租户改写 SQL 后再由分页插件加
 *       LIMIT/COUNT（MP 官方要求多租户在分页之前）；本期为预留租户模型，
 *       所有请求落在默认租户 1000，机制就位、业务零感知；</li>
 *   <li>分页插件：物理分页（LIMIT），自动适配 MySQL 方言，单页上限 500 条
 *       防止误传 pageSize=100000 拖垮数据库；</li>
 *   <li>审计字段自动填充：插入/更新时自动写入 tenant_id / create_by / create_time /
 *       update_by / update_time，业务代码不再手写这些赋值。</li>
 * </ul>
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * MP 拦截器链：多租户（{@link TenantConfig} 提供处理器）→ 分页。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(TenantLineHandler tenantLineHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1. 多租户：自动 WHERE tenant_id = ? / INSERT 补租户列（共享表白名单跳过）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));
        // 2. 分页：必须放最后
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    /**
     * 审计字段自动填充处理器。
     * <p>
     * createBy/updateBy 取登录用户上下文：JWT 过滤器（auth-platform-system）认证成功后把
     * 用户 ID（Long）放入 SecurityContext 的 principal；未登录场景（定时任务、
     * 系统初始化、登录接口本身）填 0 表示系统。
     */
    @Bean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "createBy", Long.class, currentUserId());
                this.strictInsertFill(metaObject, "updateBy", Long.class, currentUserId());
                // 租户：与库列默认值同语义
                this.strictInsertFill(metaObject, "tenantId", Long.class, TenantContextHolder.getTenantId());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                this.strictUpdateFill(metaObject, "updateBy", Long.class, currentUserId());
            }

            /** 获取当前登录用户 ID，统一走 SecurityUtils（未登录返回 0=系统操作） */
            private Long currentUserId() {
                return com.auth.framework.security.SecurityUtils.getCurrentUserId();
            }
        };
    }
}
