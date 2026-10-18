package com.auth.system.dto;

import com.auth.system.entity.IamTenant;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户展示对象。
 */
@Data
public class TenantVO {

    /** 租户 ID */
    private Long id;
    /** 租户代码 */
    private String code;
    /** 租户名称 */
    private String name;
    /** 自定义域名 */
    private String domain;
    /** 套餐代码 */
    private String planCode;
    /** 状态：1-启用 0-停用 */
    private Integer status;
    /** 品牌 Logo */
    private String logoUrl;
    /** 品牌主色 */
    private String primaryColor;
    /** 默认语言 */
    private String locale;
    /** 默认时区 */
    private String timezone;
    /** 最大用户数 */
    private Integer maxUsers;
    /** 到期时间 */
    private LocalDateTime expiresAt;
    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 从实体构造展示对象。
     *
     * @param tenant 租户实体
     * @return 展示对象
     */
    public static TenantVO from(IamTenant tenant) {
        TenantVO vo = new TenantVO();
        vo.setId(tenant.getId());
        vo.setCode(tenant.getCode());
        vo.setName(tenant.getName());
        vo.setDomain(tenant.getDomain());
        vo.setPlanCode(tenant.getPlanCode());
        vo.setStatus(tenant.getStatus());
        vo.setLogoUrl(tenant.getLogoUrl());
        vo.setPrimaryColor(tenant.getPrimaryColor());
        vo.setLocale(tenant.getLocale());
        vo.setTimezone(tenant.getTimezone());
        vo.setMaxUsers(tenant.getMaxUsers());
        vo.setExpiresAt(tenant.getExpiresAt());
        vo.setCreateTime(tenant.getCreateTime());
        return vo;
    }
}
