package com.auth.app.controller;

import com.auth.app.dto.SyncUserVO;
import com.auth.app.service.AppAssignService;
import com.auth.idm.dto.BasicProfileForm;
import com.auth.idm.dto.ResetPwdForm;
import com.auth.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务间 API —— 业务系统（ERP/MES）后端调用，供其管理端做"应用级启停"
 * 与用户增量/全量同步（SCIM 2.0 风格）。
 * <p>
 * 认证方式：client_credentials 模式在 /oauth/token 换取 service token
 * （RS256 JWT，scope=service），由 auth-biz-oidc 的 ServiceTokenFilter 校验；
 * 本控制器要求 ROLE_SERVICE，且路径中的 clientId 必须与令牌中的 client_id 一致
 * （应用只能操作自己的用户列表）。
 */
@Tag(name = "服务间 API")
@RestController
@RequestMapping("/service")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SERVICE')")
public class ServiceApiController {

    private final AppAssignService assignService;

    /**
     * 本应用用户增量/全量同步（对账式：active=false 即业务系统侧应软删除）。
     *
     * @param updatedSince 增量游标（ISO-8601，如 2026-09-06T00:00:00；缺省=全量）
     */
    @Operation(summary = "用户同步")
    @GetMapping("/apps/{clientId}/users")
    public Result<List<SyncUserVO>> syncUsers(@PathVariable String clientId,
                                              @RequestParam(required = false) LocalDateTime updatedSince) {
        requireClientMatch(clientId);
        return Result.ok(assignService.syncUsers(clientId, updatedSince));
    }

    /**
     * 应用级启停（按工号定位；仅对在职员工可操作，离职/全局停用返回业务异常）。
     */
    @Operation(summary = "应用级启停")
    @PutMapping("/apps/{clientId}/users/{employeeNo}/status")
    public Result<Void> setAppStatus(@PathVariable String clientId, @PathVariable String employeeNo,
                                     @RequestBody StatusForm form) {
        requireClientMatch(clientId);
        assignService.setAppStatusByEmployeeNo(clientId, employeeNo, form.isEnabled());
        return Result.ok();
    }

    /** 子系统维护已分配员工的联系档案，事实源仍落在认证中心。 */
    @Operation(summary = "维护员工基础档案")
    @PutMapping("/apps/{clientId}/users/{employeeNo}/profile")
    public Result<Void> updateProfile(@PathVariable String clientId, @PathVariable String employeeNo,
                                      @Valid @RequestBody BasicProfileForm form) {
        requireClientMatch(clientId);
        assignService.updateProfileByEmployeeNo(clientId, employeeNo, form);
        return Result.ok();
    }

    /** 子系统管理员重置统一账号密码；种子账号由中心保护为只读。 */
    @Operation(summary = "重置统一账号密码")
    @PutMapping("/apps/{clientId}/users/{employeeNo}/password")
    public Result<Void> resetPassword(@PathVariable String clientId, @PathVariable String employeeNo,
                                      @Valid @RequestBody ResetPwdForm form) {
        requireClientMatch(clientId);
        assignService.resetPasswordByEmployeeNo(clientId, employeeNo, form);
        return Result.ok();
    }

    /** 令牌 client_id 与路径一致性校验（应用只能操作自己） */
    private void requireClientMatch(String clientId) {
        String tokenClientId = com.auth.framework.security.SecurityUtils.getCurrentClientId();
        if (tokenClientId == null || !tokenClientId.equals(clientId)) {
            throw new com.auth.common.exception.BusinessException(
                    "服务令牌与目标应用不匹配：token=%s path=%s", tokenClientId, clientId);
        }
    }

    /** 启停请求体 */
    @Data
    public static class StatusForm {
        /** true-启用 false-应用级禁用 */
        private boolean enabled;
    }
}
