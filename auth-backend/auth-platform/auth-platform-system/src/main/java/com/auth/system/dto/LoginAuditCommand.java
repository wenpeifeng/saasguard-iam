package com.auth.system.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录审计命令。
 *
 * <p>异步审计不能依赖请求线程中的 ThreadLocal 租户上下文，因此调用方必须显式传入
 * tenantId，确保多租户日志不会串租户。</p>
 */
@Data
@Builder
public class LoginAuditCommand {

    private Long tenantId;
    private String username;
    private Long userId;
    private String loginType;
    private String clientId;
    private boolean success;
    private String failReason;
    private String ip;
    private String userAgent;
}
