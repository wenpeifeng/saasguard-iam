package com.auth.app.entity;

import com.auth.framework.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应用注册实体（app_client）—— OAuth2 client / OIDC relying party。
 * <p>
 * "物业门禁"模型中的"门"：应用注册后由中心分配用户进门权（app_user_assign）。
 * client_secret 仅存 BCrypt，明文只在创建/重置时返回一次。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_client")
public class AppClient extends BaseEntity {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;

    /** 状态：停用（停用后该应用所有登录被拒） */
    public static final int STATUS_DISABLED = 0;

    /** 客户端标识（对外协议层 ID，如 erp-web） */
    private String clientId;

    /** 客户端密钥（BCrypt；client_credentials 换服务令牌用） */
    private String clientSecret;

    /** 应用名称 */
    private String appName;

    /** 回调地址（授权码投递点，必须精确匹配） */
    private String redirectUri;

    /** 访问令牌有效期（秒） */
    private Integer accessTokenValidity;

    /** 登录时是否展示授权确认页：1-需要 0-跳过 */
    private Integer consentRequired;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 应用管理员（idm_user.id，委托视图用；空=仅平台超管可管） */
    private Long adminUserId;

    /** 备注 */
    private String remark;
}
