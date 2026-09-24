package com.auth.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理端登录表单。
 */
@Data
public class LoginForm {

    /** 登录名（工号或用户名） */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 口令（明文，仅 HTTPS 传输） */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 登录类型：ADMIN-管理端（默认） OIDC-统一登录页（登录日志审计用） */
    private String loginType;

    /** OIDC 登录对应的应用 client_id（管理端为空，登录日志审计用） */
    private String clientId;
}
