package com.auth.app.entity;

import com.auth.framework.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户×应用×角色标签实体（app_user_role）—— 本期即 xxx.admin 系统管理员标签。
 * role_code 必须为应用在 app_client_role 已声明的角色。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_user_role")
public class AppUserRole extends BaseEntity {

    /** 应用（app_client.client_id） */
    private String clientId;

    /** 用户（idm_user.id） */
    private Long userId;

    /** 角色标签（必须为应用已声明角色） */
    private String roleCode;
}
