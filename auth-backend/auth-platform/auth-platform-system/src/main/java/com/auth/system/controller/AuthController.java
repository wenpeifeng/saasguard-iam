package com.auth.system.controller;

import com.auth.common.result.Result;
import com.auth.framework.security.SecurityUtils;
import com.auth.system.dto.LoginForm;
import com.auth.system.dto.LoginVO;
import com.auth.system.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端认证接口（登录 / 登出 / 当前用户）。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 管理端登录。
     */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginForm form, HttpServletRequest request) {
        return Result.ok(authService.login(form, request.getRemoteAddr(), request.getHeader("User-Agent")));
    }

    /**
     * 登出（使当前令牌失效）。
     */
    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ")
                ? header.substring("Bearer ".length()).trim() : null;
        if (token != null) {
            authService.logout(SecurityUtils.getCurrentUserId(), token);
        }
        return Result.ok();
    }

    /**
     * 探活（白名单，供前端/运维检查服务可用性）。
     */
    @Operation(summary = "探活")
    @GetMapping("/me")
    public Result<Long> me() {
        return Result.ok(SecurityUtils.getCurrentUserId());
    }
}
