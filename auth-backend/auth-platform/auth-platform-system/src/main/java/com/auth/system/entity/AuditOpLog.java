package com.auth.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端操作日志实体（audit_op_log）—— 追加型审计表，只插不改不删。
 * 由 OpLogInterceptor 按写请求（POST/PUT/DELETE）统一切面落库。
 */
@Data
@TableName("audit_op_log")
public class AuditOpLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 操作人 ID（认证中心管理端账号，未登录写操作为空） */
    private Long userId;

    /** 操作人用户名 */
    private String username;

    /** HTTP 方法（POST/PUT/DELETE） */
    private String method;

    /** 请求 URI */
    private String uri;

    /** 响应状态码 */
    private Integer statusCode;

    /** 操作明细（请求体摘要，敏感字段脱敏） */
    private String detail;

    /** 来源 IP */
    private String ip;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 操作时间 */
    private LocalDateTime createTime;
}
