package com.auth.system.service;

import com.auth.common.result.PageResult;
import com.auth.framework.mybatis.PageQuery;
import com.auth.system.entity.AuditLoginLog;
import com.auth.system.entity.AuditOpLog;
import lombok.Data;

/**
 * 审计服务 —— 4A 第一层审计（登录日志 + 操作日志）。
 * <p>
 * 记录接口容错：审计失败只打日志不抛异常，绝不影响主流程。
 */
public interface AuditService {

    /**
     * 记录一次登录尝试（成功/失败均落库）。
     *
     * @param username   尝试登录的账号名
     * @param userId     用户 ID（账号不存在传 null）
     * @param loginType  ADMIN-管理端 OIDC-统一登录页
     * @param clientId   OIDC 登录的应用 client_id（可为 null）
     * @param success    是否成功
     * @param failReason 失败原因（成功传 null）
     * @param ip         来源 IP
     * @param userAgent  User-Agent
     */
    void recordLogin(String username, Long userId, String loginType, String clientId,
                     boolean success, String failReason, String ip, String userAgent);

    /** 记录一条管理端操作日志（由 OpLogInterceptor 调用） */
    void recordOp(AuditOpLog opLog);

    /** 登录日志分页查询 */
    PageResult<AuditLoginLog> pageLogin(LoginLogQuery query);

    /** 操作日志分页查询 */
    PageResult<AuditOpLog> pageOp(OpLogQuery query);

    /** 登录日志查询条件 */
    @Data
    class LoginLogQuery extends PageQuery {
        private String username;
        private Long userId;
        private String loginType;
        private Integer success;
    }

    /** 操作日志查询条件 */
    @Data
    class OpLogQuery extends PageQuery {
        private String username;
        private String uri;
    }
}
