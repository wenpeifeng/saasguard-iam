package com.auth.idm.entity;

import com.auth.framework.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 账号生命周期事件流水（idm_user_event，追加型只插不改）。
 * <p>
 * 事件类型：CREATE 建号 / TRANSFER 转岗 / LEAVE 离职 / STATUS_CHANGE 状态变更 / RESET_PWD 重置口令。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("idm_user_event")
public class IdmUserEvent extends BaseEntity {

    /** 事件类型：建号 */
    public static final String TYPE_CREATE = "CREATE";

    /** 事件类型：转岗 */
    public static final String TYPE_TRANSFER = "TRANSFER";

    /** 事件类型：离职 */
    public static final String TYPE_LEAVE = "LEAVE";

    /** 事件类型：状态变更（全局停用/恢复） */
    public static final String TYPE_STATUS_CHANGE = "STATUS_CHANGE";

    /** 事件类型：重置口令 */
    public static final String TYPE_RESET_PWD = "RESET_PWD";

    /** 用户 ID（idm_user.id） */
    private Long userId;

    /** 事件类型：CREATE/TRANSFER/LEAVE/STATUS_CHANGE/RESET_PWD */
    private String eventType;

    /** 事件明细（JSON：如转岗前后组织） */
    private String detail;
}
