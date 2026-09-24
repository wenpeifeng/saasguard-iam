package com.auth.framework.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限注解 —— Controller 方法级的权限码校验（统一鉴权的核心入口）。
 * <p>
 * 权限码规范：{@code 模块:资源:操作}，与 sys_menu 按钮型菜单的 perms 字段一一对应。
 * 登录后用户的权限码由 JwtAuthFilter 从角色-菜单关系加载进 SecurityContext
 * （超管角色自动持有全部按钮权限），本注解据此做方法级拦截，无权访问返回 403 Result JSON。
 * <p>
 * 用法：
 * <pre>{@code
 * @RequirePerm("purchase:order:add")
 * @PostMapping("/orders")
 * public Result<Void> create(@Valid @RequestBody OrderForm form) { ... }
 * }</pre>
 * <p>
 * 分层规则：查询接口 {@code :list}，新增 {@code :add}，修改 {@code :edit}，
 * 删除 {@code :delete}，审核/过账等流转动作 {@code :audit}，导出 {@code :export}。
 * 菜单可见性由前端按同一权限码控制（HasPerm 组件），前后端同源校验。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('{value}')")
public @interface RequirePerm {

    /**
     * 权限码，如 {@code purchase:order:add}。
     */
    String value();
}
