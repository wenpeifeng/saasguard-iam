package com.auth.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户创建/修改表单。
 */
@Data
public class TenantForm {

    /** 租户代码，仅创建时生效 */
    @NotBlank(message = "租户代码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9-]{1,31}$", message = "租户代码须为 2-32 位小写字母、数字或连字符")
    private String code;

    /** 租户名称 */
    @NotBlank(message = "租户名称不能为空")
    @Size(max = 128, message = "租户名称不能超过 128 个字符")
    private String name;

    /** 自定义登录域名 */
    @Size(max = 255, message = "域名不能超过 255 个字符")
    private String domain;

    /** 套餐代码 */
    @NotBlank(message = "套餐不能为空")
    @Pattern(regexp = "FREE|STANDARD|ENTERPRISE", message = "套餐代码不正确")
    private String planCode;

    /** 品牌 Logo 地址 */
    private String logoUrl;

    /** 品牌主色 */
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "品牌主色须为 #RRGGBB")
    private String primaryColor;

    /** 默认语言 */
    private String locale;

    /** 默认时区 */
    private String timezone;

    /** 最大用户数，0 表示不限制 */
    @Min(value = 0, message = "最大用户数不能小于 0")
    private Integer maxUsers;

    /** 服务到期时间 */
    private LocalDateTime expiresAt;

    /** 初始租户管理员登录名，仅创建时使用 */
    @NotBlank(message = "租户管理员账号不能为空")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_.-]{2,63}$", message = "管理员账号格式不正确")
    private String adminUsername;

    /** 初始租户管理员姓名，仅创建时使用 */
    @NotBlank(message = "租户管理员姓名不能为空")
    private String adminRealName;

    /** 初始租户管理员密码，仅创建时使用 */
    @NotBlank(message = "租户管理员密码不能为空")
    @Size(min = 6, max = 72, message = "管理员密码长度须为 6-72 位")
    private String adminPassword;
}
