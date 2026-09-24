package com.auth.app.controller;

import com.auth.app.dto.ClientForm;
import com.auth.app.dto.ClientSecretVO;
import com.auth.app.dto.ClientVO;
import com.auth.app.service.AppAssignService;
import com.auth.app.service.AppClientService;
import com.auth.common.result.PageResult;
import com.auth.common.result.Result;
import com.auth.framework.mybatis.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用注册接口（平台超管维护"门"）。
 * client_secret 仅创建/重置时明文返回一次，库内 BCrypt。
 */
@Tag(name = "应用注册")
@RestController
@RequestMapping("/apps")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AppClientController {

    private final AppClientService clientService;
    private final AppAssignService assignService;

    /**
     * 应用分页列表。
     */
    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageResult<ClientVO>> page(PageQuery query) {
        return Result.ok(clientService.page(query));
    }

    /**
     * 应用详情（含角色声明）。
     */
    @Operation(summary = "详情")
    @GetMapping("/{clientId}")
    public Result<ClientVO> get(@PathVariable String clientId) {
        return Result.ok(clientService.get(clientId));
    }

    /**
     * 注册应用（响应含明文密钥，仅此一次）。
     */
    @Operation(summary = "注册应用")
    @PostMapping
    public Result<ClientSecretVO> create(@Valid @RequestBody ClientForm form) {
        return Result.ok(clientService.create(form));
    }

    /**
     * 修改应用（client_id 不可改，角色声明整体替换）。
     */
    @Operation(summary = "修改应用")
    @PutMapping("/{clientId}")
    public Result<Void> update(@PathVariable String clientId, @Valid @RequestBody ClientForm form) {
        clientService.update(clientId, form);
        return Result.ok();
    }

    /**
     * 启用/停用应用。
     */
    @Operation(summary = "启停应用")
    @PutMapping("/{clientId}/status/{status}")
    public Result<Void> changeStatus(@PathVariable String clientId, @PathVariable Integer status) {
        clientService.changeStatus(clientId, status);
        return Result.ok();
    }

    /**
     * 重置密钥（响应含新明文密钥，仅此一次）。
     */
    @Operation(summary = "重置密钥")
    @PutMapping("/{clientId}/secret")
    public Result<ClientSecretVO> resetSecret(@PathVariable String clientId) {
        return Result.ok(clientService.resetSecret(clientId));
    }

    /**
     * 按用户 ID 批量查"已分配应用 + 角色标签"（主账号列表页的分配概览列）。
     */
    @Operation(summary = "用户分配摘要（批量）")
    @GetMapping("/user-summaries")
    public Result<java.util.Map<Long, java.util.List<AppAssignService.UserAppSummary>>> userSummaries(
            @org.springframework.web.bind.annotation.RequestParam java.util.List<Long> userIds) {
        return Result.ok(assignService.summariesByUserIds(userIds));
    }
}
