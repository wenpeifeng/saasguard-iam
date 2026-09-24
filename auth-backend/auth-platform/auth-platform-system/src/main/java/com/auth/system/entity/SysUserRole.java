package com.auth.system.entity;

import com.auth.framework.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户-平台角色关联实体（sys_user_role），user_id 指向 idm_user.id。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_role")
public class SysUserRole extends BaseEntity {

    /** 用户 ID（idm_user.id） */
    private Long userId;

    /** 角色 ID（sys_role.id） */
    private Long roleId;
}
