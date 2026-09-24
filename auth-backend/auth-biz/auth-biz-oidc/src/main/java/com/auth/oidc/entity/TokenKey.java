package com.auth.oidc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * OIDC 签名密钥实体（token_key）—— 共享表（无租户列），
 * 首次启动由 TokenKeyService 自动生成 RS256 密钥对入库。
 */
@Data
@TableName("token_key")
public class TokenKey implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 密钥 ID（JWKS kid，与 JWT header.kid 对应） */
    private String kid;

    /** 算法（固定 RS256） */
    private String algorithm;

    /** 公钥 PEM（X.509 SubjectPublicKeyInfo） */
    private String publicKey;

    /** 私钥 PEM（PKCS#8） */
    private String privateKey;

    /** 状态：1-签发中 0-已轮换（仅验签） */
    private Integer status;

    /** 生成时间 */
    private LocalDateTime createTime;
}
