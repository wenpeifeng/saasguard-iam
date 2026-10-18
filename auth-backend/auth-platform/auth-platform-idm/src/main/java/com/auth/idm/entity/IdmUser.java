package com.auth.idm.entity;

import com.auth.framework.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 主账号实体（idm_user）—— 全集团统一身份，生命周期全部收口在认证中心。
 * <p>
 * 状态机（全局状态，最强状态优先）：
 * <pre>
 *   1 在职 ──离职──▶ 2 离职（终态：一切应用级启停操作被中心接口拒绝）
 *     ▲  │
 *     └全局停用/恢复┘ 3 全局停用（可恢复回在职）
 * </pre>
 * 匹配/关联属性用不可变工号 employeeNo（SCIM externalId 思路），不用邮箱。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("idm_user")
public class IdmUser extends BaseEntity {

    /** 全局状态：在职 */
    public static final int STATUS_ACTIVE = 1;

    /** 全局状态：离职（终态） */
    public static final int STATUS_LEFT = 2;

    /** 全局状态：全局停用（可恢复） */
    public static final int STATUS_SUSPENDED = 3;

    /** 工号（不可变，跨系统匹配/关联的唯一键） */
    private String employeeNo;

    /** 登录名（默认同工号，可改） */
    private String username;

    /** 姓名 */
    private String realName;

    /** 组织 ID（idm_org.id，转岗即改此列并写事件流水） */
    private Long orgId;

    /** 岗位 */
    private String position;

    /** 邮箱（仅联系用，不作匹配键） */
    private String email;

    /** 手机号（仅联系用） */
    private String phone;

    /** 登录口令（BCrypt，永不返回前端） */
    private String password;

    /** 全局状态：1-在职 2-离职（终态） 3-全局停用 */
    private Integer status;

    /** 入职日期 */
    private LocalDate entryDate;

    /** 离职日期（离职时写入） */
    private LocalDate leaveDate;

    /** 账号来源：MANUAL=人工建号，SEED=随项目初始化的稳定种子 */
    private String accountSource;

    /** 主档是否只读；种子账号为 true，授权关系仍可独立维护 */
    private Boolean immutable;
}
