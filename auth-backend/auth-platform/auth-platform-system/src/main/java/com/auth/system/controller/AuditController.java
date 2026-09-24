package com.auth.system.controller;

import com.auth.common.result.PageResult;
import com.auth.common.result.Result;
import com.auth.system.entity.AuditLoginLog;
import com.auth.system.entity.AuditOpLog;
import com.auth.system.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志接口 —— 超级管理员与审计员可查（只读）。
 */
@Tag(name = "审计日志")
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','AUDITOR')")
public class AuditController {

    private final AuditService auditService;

    /**
     * 登录日志分页查询（成功/失败、IP、UA、登录类型）。
     */
    @Operation(summary = "登录日志")
    @GetMapping("/login-logs")
    public Result<PageResult<AuditLoginLog>> loginLogs(AuditService.LoginLogQuery query) {
        return Result.ok(auditService.pageLogin(query));
    }

    /**
     * 操作日志分页查询（管理端写操作）。
     */
    @Operation(summary = "操作日志")
    @GetMapping("/op-logs")
    public Result<PageResult<AuditOpLog>> opLogs(AuditService.OpLogQuery query) {
        return Result.ok(auditService.pageOp(query));
    }
}
