package com.auth.system.event;

/**
 * 租户创建事件，用于在身份域原子初始化根组织与首位租户管理员。
 *
 * @param tenantId     租户 ID
 * @param tenantName   租户名称
 * @param adminUsername 管理员登录名
 * @param adminRealName 管理员姓名
 * @param adminPassword 管理员初始密码
 */
public record TenantCreatedEvent(Long tenantId, String tenantName, String adminUsername,
                                 String adminRealName, String adminPassword) {
}
