package com.auth.idm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 主账号新建表单（入职建号）。
 * <p>
 * 口令必填（初始口令由管理员代发，用户登录后应自行修改）；
 * 工号创建后不可变（跨系统匹配键）。
 */
@Data
public class UserForm {

    /** 创建时额外校验密码，编辑档案不要求密码。 */
    public interface Create extends jakarta.validation.groups.Default { }

    /** 工号（不可变，租户内唯一） */
    @Size(max = 64, message = "工号长度不能超过 64")
    private String employeeNo;

    /** 姓名 */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过 64")
    private String realName;

    /** 组织 ID */
    @NotNull(message = "组织不能为空")
    private Long orgId;

    /** 岗位 */
    @Size(max = 64, message = "岗位长度不能超过 64")
    private String position;

    /** 邮箱（仅联系用，不作匹配键） */
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    /** 手机号 */
    @Size(max = 32, message = "手机号长度不能超过 32")
    private String phone;

    /** 初始口令（BCrypt 加密后入库） */
    @NotBlank(message = "初始密码不能为空", groups = Create.class)
    @Size(min = 6, max = 64, message = "密码长度须为 6-64 位", groups = Create.class)
    private String password;

    /** 入职日期（缺省当天） */
    @jakarta.validation.constraints.PastOrPresent(message = "入职日期不能晚于今天")
    private LocalDate entryDate;
}
