package com.auth.idm.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 主账号 VO（口令永不返回）。
 */
@Data
public class UserVO {

    /** 用户 ID */
    private Long id;

    /** 工号（不可变匹配键） */
    private String employeeNo;

    /** 登录名 */
    private String username;

    /** 姓名 */
    private String realName;

    /** 组织 ID */
    private Long orgId;

    /** 组织名称（联查冗余展示） */
    private String orgName;

    /** 岗位 */
    private String position;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 全局状态：1-在职 2-离职 3-全局停用 */
    private Integer status;

    /** 入职日期 */
    private LocalDate entryDate;

    /** 离职日期 */
    private LocalDate leaveDate;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 账号来源：MANUAL / SEED */
    private String accountSource;

    /** 是否为只读种子主档 */
    private Boolean immutable;
}
