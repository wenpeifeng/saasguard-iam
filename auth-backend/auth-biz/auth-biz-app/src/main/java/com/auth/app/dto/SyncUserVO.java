package com.auth.app.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务间同步用户视图（SCIM 2.0 风格：软删除 active=false、对账式同步）。
 * <p>
 * active = 全局在职/停用 与 应用级启用 的合成结果：
 * 仅当"全局状态=在职 且 应用级状态=启用"时为 true。
 */
@Data
public class SyncUserVO {

    /** 工号（不可变，业务系统匹配/关联的唯一键，SCIM externalId 思路） */
    private String employeeNo;

    private String username;
    private String realName;
    private String orgName;
    private String position;
    private String email;
    private String phone;

    /** 身份来源：MANUAL / SEED。 */
    private String accountSource;

    /** 系统种子主档是否只读。 */
    private Boolean immutable;

    /** 全局人员状态：1在职 2离职 3全局停用。 */
    private Integer identityStatus;

    private LocalDate entryDate;
    private LocalDate leaveDate;
    private LocalDateTime identityCreateTime;

    /** 是否可登录本应用（全局在职 且 应用级启用） */
    private Boolean active;

    /** 失效原因（active=false 时给出：LEFT/SUSPENDED/APP_DISABLED/REVOKED） */
    private String inactiveReason;

    /** 该用户在本应用持有的角色标签 */
    private List<String> roles;

    /** 本应用维度的最近变更时间（增量同步游标） */
    private LocalDateTime updateTime;
}
