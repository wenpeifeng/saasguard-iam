package com.auth.system.service;

import com.auth.system.dto.LoginForm;
import com.auth.system.dto.LoginVO;

/**
 * 管理端认证服务（接口定义在 system，实现在 idm —— 主账号数据归 idm 模块，
 * 依赖方向 idm → system，禁止反向）。
 */
public interface AuthService {

    /**
     * 管理端登录：校验口令与账号全局状态，签发 JWT（成功/失败均落登录日志）。
     *
     * @param form      登录表单（用户名 + 口令，loginType=OIDC 时为统一登录页登录）
     * @param ip        来源 IP（审计用）
     * @param userAgent User-Agent（审计用）
     * @return 登录响应（令牌 + 用户摘要 + 角色编码）
     * @throws com.auth.common.exception.BusinessException 用户不存在 / 口令错误 / 离职或全局停用
     */
    LoginVO login(LoginForm form, String ip, String userAgent);

    /**
     * 登出：使指定令牌失效（Redis 在线键删除 + 黑名单）。
     *
     * @param userId 当前用户 ID
     * @param token  当前请求的 Bearer 令牌
     */
    void logout(Long userId, String token);
}
