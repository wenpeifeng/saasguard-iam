package com.auth.system.entity;

import com.auth.framework.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 平台角色实体（sys_role）。
 * <p>
 * 平台级角色为平台概念（租户插件按共享表处理）：SUPER_ADMIN / APP_ADMIN / AUDITOR。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 角色编码（SUPER_ADMIN/APP_ADMIN/AUDITOR） */
    private String code;

    /** 角色名称 */
    private String name;

    /** 备注 */
    private String remark;
}
