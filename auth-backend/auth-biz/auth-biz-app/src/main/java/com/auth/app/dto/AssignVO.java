package com.auth.app.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分配视图（用户×应用）：业务系统管理端用户列表的行结构。
 */
@Data
public class AssignVO {

    private Long assignId;
    private Long userId;
    private String employeeNo;
    private String username;
    private String realName;
    private String orgName;
    private String position;

    /** 全局状态：1-在职 2-离职 3-全局停用（离职为终态，业务系统不可再操作） */
    private Integer globalStatus;

    /** 应用级状态：1-启用 0-应用级禁用 */
    private Integer appStatus;

    /** 该用户在本应用持有的角色标签（如 erp.admin） */
    private List<String> roles;

    /** 分配时间 */
    private LocalDateTime assignTime;

    /** 应用级状态最近变更时间（增量同步对账依据） */
    private LocalDateTime updateTime;
}
