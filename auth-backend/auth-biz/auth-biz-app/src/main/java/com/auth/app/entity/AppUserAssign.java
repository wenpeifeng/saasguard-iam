package com.auth.app.entity;

import com.auth.framework.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户×应用分配实体（app_user_assign）—— 进门权 + 应用级启停。
 * <p>
 * 应用级禁用（status=0）只影响本应用，不动全局状态；业务系统管理端
 * 通过服务间 API 调整此列，实现"ERP/MES 管理员自己店内禁用某员工"。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_user_assign")
public class AppUserAssign extends BaseEntity {

    /** 应用级状态：启用 */
    public static final int STATUS_ENABLED = 1;

    /** 应用级状态：应用级禁用（业务系统管理员可调） */
    public static final int STATUS_DISABLED = 0;

    /** 应用（app_client.client_id） */
    private String clientId;

    /** 用户（idm_user.id） */
    private Long userId;

    /** 应用级状态：1-启用 0-应用级禁用 */
    private Integer status;
}
