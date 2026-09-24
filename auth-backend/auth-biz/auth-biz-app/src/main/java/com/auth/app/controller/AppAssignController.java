package com.auth.app.controller;

import com.auth.app.dto.AssignQuery;
import com.auth.app.dto.AssignVO;
import com.auth.app.service.AppAssignService;
import com.auth.common.result.PageResult;
import com.auth.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 应用分配接口（管理端：分配/回收/应用级启停/角色标签）。
 * 业务系统侧的应用级启停走 /service/**（服务间 API），不走本接口。
 */
@Tag(name = "应用分配")
@RestController
@RequestMapping("/apps/{clientId}")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AppAssignController {

    private final AppAssignService assignService;

    /**
     * 已分配用户分页（= 业务系统管理端的用户列表）。
     */
    @Operation(summary = "已分配用户分页")
    @GetMapping("/assignments")
    public Result<PageResult<AssignVO>> page(@PathVariable String clientId, AssignQuery query) {
        return Result.ok(assignService.pageAssigned(clientId, query));
    }

    /**
     * 分配用户进门权（批量）。
     */
    @Operation(summary = "分配用户")
    @PostMapping("/assignments")
    public Result<Void> assign(@PathVariable String clientId,
                               @Valid @RequestBody BatchUserForm form) {
        assignService.assign(clientId, form.getUserIds());
        return Result.ok();
    }

    /**
     * 回收进门权（连带回收该应用角色标签）。
     */
    @Operation(summary = "回收用户")
    @DeleteMapping("/assignments/{userId}")
    public Result<Void> revoke(@PathVariable String clientId, @PathVariable Long userId) {
        assignService.revoke(clientId, userId);
        return Result.ok();
    }

    /**
     * 应用级启停（管理端入口；仅影响本应用）。
     */
    @Operation(summary = "应用级启停")
    @PutMapping("/assignments/{userId}/status/{enabled}")
    public Result<Void> setAppStatus(@PathVariable String clientId, @PathVariable Long userId,
                                     @PathVariable boolean enabled) {
        assignService.setAppStatus(clientId, userId, enabled);
        return Result.ok();
    }

    /**
     * 授予角色标签（如 erp.admin）。
     */
    @Operation(summary = "授予角色标签")
    @PutMapping("/assignments/{userId}/roles/{roleCode}")
    public Result<Void> grantRole(@PathVariable String clientId, @PathVariable Long userId,
                                  @PathVariable String roleCode) {
        assignService.grantRole(clientId, userId, roleCode);
        return Result.ok();
    }

    /**
     * 收回角色标签。
     */
    @Operation(summary = "收回角色标签")
    @DeleteMapping("/assignments/{userId}/roles/{roleCode}")
    public Result<Void> revokeRole(@PathVariable String clientId, @PathVariable Long userId,
                                   @PathVariable String roleCode) {
        assignService.revokeRole(clientId, userId, roleCode);
        return Result.ok();
    }

    /** 批量用户表单 */
    @Data
    public static class BatchUserForm {
        /** 用户 ID 列表 */
        @NotEmpty(message = "用户列表不能为空")
        private List<Long> userIds;
    }
}
