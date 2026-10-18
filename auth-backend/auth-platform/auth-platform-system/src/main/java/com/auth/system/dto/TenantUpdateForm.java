package com.auth.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** 租户可维护配置；租户代码和首位管理员创建后不可通过此接口修改。 */
@Data
public class TenantUpdateForm {

    @NotBlank(message = "租户名称不能为空")
    @Size(max = 128, message = "租户名称不能超过 128 个字符")
    private String name;

    @Size(max = 255, message = "域名不能超过 255 个字符")
    private String domain;

    @NotBlank(message = "套餐不能为空")
    @Pattern(regexp = "FREE|STANDARD|ENTERPRISE", message = "套餐代码不正确")
    private String planCode;

    private String logoUrl;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "品牌主色须为 #RRGGBB")
    private String primaryColor;

    private String locale;
    private String timezone;
    @Min(value = 0, message = "最大用户数不能小于 0")
    private Integer maxUsers;
    private LocalDateTime expiresAt;
}
