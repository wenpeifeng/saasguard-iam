package com.auth.app.entity;

import com.auth.framework.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应用声明的角色标签实体（app_client_role）。
 * 本期即 xxx.admin 系统管理员标签；升级路径：应用声明完整角色清单后，
 * 配合 app_user_role 即可承载普通角色分配，token roles claim 无需改动。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_client_role")
public class AppClientRole extends BaseEntity {

    /** 所属应用（app_client.client_id） */
    private String clientId;

    /** 角色标签（如 erp.admin） */
    private String roleCode;

    /** 角色名称 */
    private String roleName;
}
