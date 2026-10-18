package com.auth.system.controller;

import com.auth.common.result.PageResult;
import com.auth.common.result.Result;
import com.auth.system.dto.TenantForm;
import com.auth.system.dto.TenantQuery;
import com.auth.system.dto.TenantVO;
import com.auth.system.dto.TenantUpdateForm;
import com.auth.system.service.TenantService;
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
 * SaaS 租户管理接口，仅平台超级管理员可访问。
 */
@Tag(name = "SaaS 租户")
@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TenantController {

    private final TenantService tenantService;

    /** 分页查询租户。 */
    @Operation(summary = "分页查询租户")
    @GetMapping
    public Result<PageResult<TenantVO>> page(TenantQuery query) {
        return Result.ok(tenantService.page(query));
    }

    /** 查询租户详情。 */
    @Operation(summary = "租户详情")
    @GetMapping("/{id}")
    public Result<TenantVO> get(@PathVariable Long id) {
        return Result.ok(tenantService.get(id));
    }

    /** 创建租户并初始化首位管理员。 */
    @Operation(summary = "创建租户")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody TenantForm form) {
        return Result.ok(tenantService.create(form));
    }

    /** 修改租户品牌、套餐与配额。 */
    @Operation(summary = "修改租户")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TenantUpdateForm form) {
        tenantService.update(id, form);
        return Result.ok();
    }

    /** 启用或停用租户。 */
    @Operation(summary = "租户启停")
    @PutMapping("/{id}/status/{status}")
    public Result<Void> changeStatus(@PathVariable Long id, @PathVariable Integer status) {
        tenantService.changeStatus(id, status);
        return Result.ok();
    }
}
