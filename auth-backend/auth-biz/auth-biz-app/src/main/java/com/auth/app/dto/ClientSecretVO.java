package com.auth.app.dto;

import lombok.Data;

/**
 * 应用创建/密钥重置响应 —— 明文密钥仅此一次返回，前端须提示立即保存。
 */
@Data
public class ClientSecretVO {

    /** 应用信息（含角色声明） */
    private ClientVO client;

    /** 明文密钥（仅本次响应可见，库内只存 BCrypt） */
    private String clientSecret;
}
