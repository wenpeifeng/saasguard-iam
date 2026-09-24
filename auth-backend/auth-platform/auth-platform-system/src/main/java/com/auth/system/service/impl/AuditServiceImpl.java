package com.auth.system.service.impl;

import com.auth.common.result.PageResult;
import com.auth.framework.context.TenantContextHolder;
import com.auth.framework.mybatis.PageUtils;
import com.auth.system.entity.AuditLoginLog;
import com.auth.system.entity.AuditOpLog;
import com.auth.system.mapper.AuditLoginLogMapper;
import com.auth.system.mapper.AuditOpLogMapper;
import com.auth.system.service.AuditService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 审计服务实现 —— 异步落库，审计失败不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLoginLogMapper loginLogMapper;
    private final AuditOpLogMapper opLogMapper;

    @Async
    @Override
    public void recordLogin(String username, Long userId, String loginType, String clientId,
                            boolean success, String failReason, String ip, String userAgent) {
        try {
            AuditLoginLog entry = new AuditLoginLog();
            entry.setTenantId(TenantContextHolder.getTenantId());
            entry.setUserId(userId);
            entry.setUsername(username);
            entry.setLoginType(loginType == null ? "ADMIN" : loginType);
            entry.setClientId(clientId);
            entry.setSuccess(success ? 1 : 0);
            entry.setFailReason(failReason);
            entry.setIp(ip);
            // UA 截断防超长
            entry.setUserAgent(userAgent != null && userAgent.length() > 512
                    ? userAgent.substring(0, 512) : userAgent);
            entry.setCreateTime(LocalDateTime.now());
            loginLogMapper.insert(entry);
        } catch (Exception e) {
            log.error("[审计] 登录日志落库失败 username={}", username, e);
        }
    }

    @Async
    @Override
    public void recordOp(AuditOpLog opLog) {
        try {
            if (opLog.getTenantId() == null) {
                opLog.setTenantId(TenantContextHolder.getTenantId());
            }
            opLogMapper.insert(opLog);
        } catch (Exception e) {
            log.error("[审计] 操作日志落库失败 uri={}", opLog.getUri(), e);
        }
    }

    @Override
    public PageResult<AuditLoginLog> pageLogin(LoginLogQuery query) {
        LambdaQueryWrapper<AuditLoginLog> wrapper = new LambdaQueryWrapper<AuditLoginLog>()
                .like(query.getUsername() != null && !query.getUsername().isBlank(),
                        AuditLoginLog::getUsername, query.getUsername())
                .eq(query.getUserId() != null, AuditLoginLog::getUserId, query.getUserId())
                .eq(query.getLoginType() != null, AuditLoginLog::getLoginType, query.getLoginType())
                .eq(query.getSuccess() != null, AuditLoginLog::getSuccess, query.getSuccess())
                .orderByDesc(AuditLoginLog::getId);
        return PageUtils.of(loginLogMapper, wrapper, query, AuditLoginLog.class, v -> v);
    }

    @Override
    public PageResult<AuditOpLog> pageOp(OpLogQuery query) {
        LambdaQueryWrapper<AuditOpLog> wrapper = new LambdaQueryWrapper<AuditOpLog>()
                .like(query.getUsername() != null && !query.getUsername().isBlank(),
                        AuditOpLog::getUsername, query.getUsername())
                .like(query.getUri() != null && !query.getUri().isBlank(),
                        AuditOpLog::getUri, query.getUri())
                .orderByDesc(AuditOpLog::getId);
        return PageUtils.of(opLogMapper, wrapper, query, AuditOpLog.class, v -> v);
    }
}
