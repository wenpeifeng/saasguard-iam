package com.auth.app.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 应用注册/修改表单。
 */
@Data
public class ClientForm {

    /** 客户端标识（创建后不可改） */
    @NotBlank(message = "client_id 不能为空")
    private String clientId;

    /** 应用名称 */
    @NotBlank(message = "应用名称不能为空")
    private String appName;

    /** 回调地址 */
    @NotBlank(message = "回调地址不能为空")
    private String redirectUri;

    /** 访问令牌有效期（秒） */
    @NotNull(message = "令牌有效期不能为空")
    @Min(value = 60, message = "令牌有效期至少 60 秒")
    private Integer accessTokenValidity;

    /** 登录时是否展示授权确认页 */
    private Boolean consentRequired = false;

    /** 应用管理员用户 ID（可空） */
    private Long adminUserId;

    /** 备注 */
    private String remark;

    /** 应用声明的角色标签（整体替换语义：以本清单为准） */
    private List<RoleItem> roles;

    /** 角色标签项 */
    @Data
    public static class RoleItem {
        /** 角色标签（如 erp.admin） */
        @NotBlank(message = "角色编码不能为空")
        private String roleCode;
        /** 角色名称 */
        @NotBlank(message = "角色名称不能为空")
        private String roleName;
    }
}
