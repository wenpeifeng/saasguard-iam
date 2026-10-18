package com.auth.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 认证中心向子系统推送身份对账的配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "identity.sync")
public class IdentitySyncProperties {

    /** 提交后实时推送开关。 */
    private boolean enabled = true;

    /** 子系统内部回调鉴权令牌。 */
    private String callbackToken = "identity-sync-token";

    /** client_id -> 子系统内部同步端点。 */
    private Map<String, String> targets = new LinkedHashMap<>();
}
