package com.auth.app.dto;

import com.auth.app.entity.AppClient;
import com.auth.app.entity.AppClientRole;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用视图（client_secret 永不返回；明文密钥仅在创建/重置响应中出现一次）。
 */
@Data
public class ClientVO {

    private Long id;
    private String clientId;
    private String appName;
    private String redirectUri;
    private Integer accessTokenValidity;
    private Boolean consentRequired;
    private Integer status;
    private Long adminUserId;
    private String remark;
    private List<RoleVO> roles;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 角色标签视图 */
    @Data
    public static class RoleVO {
        private String roleCode;
        private String roleName;
    }

    /** 实体 + 角色声明 → VO（密钥不出现） */
    public static ClientVO from(AppClient client, List<AppClientRole> roles) {
        ClientVO vo = new ClientVO();
        vo.setId(client.getId());
        vo.setClientId(client.getClientId());
        vo.setAppName(client.getAppName());
        vo.setRedirectUri(client.getRedirectUri());
        vo.setAccessTokenValidity(client.getAccessTokenValidity());
        vo.setConsentRequired(client.getConsentRequired() != null && client.getConsentRequired() == 1);
        vo.setStatus(client.getStatus());
        vo.setAdminUserId(client.getAdminUserId());
        vo.setRemark(client.getRemark());
        vo.setCreateTime(client.getCreateTime());
        vo.setUpdateTime(client.getUpdateTime());
        vo.setRoles(roles.stream().map(r -> {
            RoleVO rv = new RoleVO();
            rv.setRoleCode(r.getRoleCode());
            rv.setRoleName(r.getRoleName());
            return rv;
        }).toList());
        return vo;
    }
}
