package com.auth.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志实体（audit_login_log）—— 追加型审计表，只插不改不删，
 * 不继承 BaseEntity（无审计四列/逻辑删除），列值由 AuditService 显式写入。
 */
@Data
@TableName("audit_login_log")
public class AuditLoginLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 用户 ID（账号不存在时为空） */
    private Long userId;

    /** 尝试登录的账号名（登录名或工号） */
    private String username;

    /** 登录类型：ADMIN-管理端 OIDC-统一登录页 */
    private String loginType;

    /** OIDC 登录对应的应用 client_id（管理端为空） */
    private String clientId;

    /** 结果：1-成功 0-失败 */
    private Integer success;

    /** 失败原因（密码错误/已离职/已停用/应用未分配等） */
    private String failReason;

    /** 来源 IP */
    private String ip;

    /** User-Agent */
    private String userAgent;

    /** 登录时间 */
    private LocalDateTime createTime;
}
