package com.auth.idm.service.impl;

import com.auth.common.exception.BusinessException;
import com.auth.framework.context.TenantContextHolder;
import com.auth.idm.entity.IdmUser;
import com.auth.idm.mapper.IdmUserMapper;
import com.auth.system.dto.LoginForm;
import com.auth.system.dto.LoginAuditCommand;
import com.auth.system.dto.LoginVO;
import com.auth.system.security.JwtTokenProvider;
import com.auth.system.security.SysRoleService;
import com.auth.system.service.AuditService;
import com.auth.system.service.AuthService;
import com.auth.system.service.TenantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 管理端认证服务实现（实现在 idm：主账号数据归 idm 模块，依赖方向 idm → system）。
 * <p>
 * 登录口诀：账号存在 + 口令匹配 + 全局状态为在职（离职/全局停用一律拒绝）。
 * 用户名支持登录名或工号匹配。成功/失败均异步落登录日志（IP/UA/类型）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final IdmUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final SysRoleService roleService;
    private final AuditService auditService;
    private final TenantService tenantService;

    @Override
    public LoginVO login(LoginForm form, String ip, String userAgent) {
        var tenant = tenantService.requireActive(form.getTenantCode());
        return TenantContextHolder.callWithTenant(tenant.getId(), () -> doLogin(form, ip, userAgent, tenant));
    }

    /** 在已解析的租户上下文中完成账号校验和令牌签发。 */
    private LoginVO doLogin(LoginForm form, String ip, String userAgent,
                            com.auth.system.entity.IamTenant tenant) {
        String loginType = form.getLoginType() == null ? "ADMIN" : form.getLoginType();
        IdmUser user = userMapper.selectOne(new LambdaQueryWrapper<IdmUser>()
                .and(w -> w.eq(IdmUser::getUsername, form.getUsername())
                        .or().eq(IdmUser::getEmployeeNo, form.getUsername())));
        // 用户不存在与口令错误统一报"用户名或密码错误"，不泄露账号存在性
        if (user == null || !passwordEncoder.matches(form.getPassword(), user.getPassword())) {
            recordLoginAudit(tenant.getId(), form, user == null ? null : user.getId(), loginType,
                    false, "用户名或密码错误", ip, userAgent);
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == IdmUser.STATUS_LEFT) {
            recordLoginAudit(tenant.getId(), form, user.getId(), loginType,
                    false, "账号已离职", ip, userAgent);
            throw new BusinessException("账号已离职，禁止登录");
        }
        if (user.getStatus() == IdmUser.STATUS_SUSPENDED) {
            recordLoginAudit(tenant.getId(), form, user.getId(), loginType,
                    false, "账号已被全局停用", ip, userAgent);
            throw new BusinessException("账号已被全局停用，禁止登录");
        }
        String token = tokenProvider.createToken(user.getId(), user.getTenantId(), user.getUsername());
        recordLoginAudit(tenant.getId(), form, user.getId(), loginType, true, null, ip, userAgent);
        log.info("[管理端登录] userId={} username={} type={}", user.getId(), user.getUsername(), loginType);
        return LoginVO.builder()
                .token(token)
                .tokenType("Bearer")
                .tenantId(tenant.getId())
                .tenantCode(tenant.getCode())
                .tenantName(tenant.getName())
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .roles(roleService.getRoleCodesByUserId(user.getId()))
                .build();
    }

    private void recordLoginAudit(Long tenantId, LoginForm form, Long userId, String loginType,
                                  boolean success, String failReason, String ip, String userAgent) {
        auditService.recordLogin(LoginAuditCommand.builder()
                .tenantId(tenantId)
                .username(form.getUsername())
                .userId(userId)
                .loginType(loginType)
                .clientId(form.getClientId())
                .success(success)
                .failReason(failReason)
                .ip(ip)
                .userAgent(userAgent)
                .build());
    }

    @Override
    public void logout(Long userId, String token) {
        tokenProvider.invalidate(userId, token);
        log.info("[管理端登出] userId={}", userId);
    }
}
