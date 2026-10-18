package com.auth.system.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 登录响应（管理端）。
 */
@Data
@Builder
public class LoginVO {

    /** 当前租户 ID */
    private Long tenantId;

    /** 当前租户代码 */
    private String tenantCode;

    /** 当前租户名称 */
    private String tenantName;

    /** JWT 访问令牌（后续请求 Authorization: Bearer {token}） */
    private String token;

    /** 令牌类型，固定 Bearer */
    private String tokenType;

    /** 用户 ID */
    private Long userId;

    /** 登录名 */
    private String username;

    /** 姓名 */
    private String realName;

    /** 平台角色编码列表 */
    private List<String> roles;
}
