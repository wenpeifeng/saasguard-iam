package com.auth.idm.controller;

import com.auth.common.result.Result;
import com.auth.idm.dto.OrgForm;
import com.auth.idm.dto.OrgTreeNode;
import com.auth.idm.service.OrgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * 组织接口（树形主数据维护）。
 */
@Tag(name = "组织管理")
@RestController
@RequestMapping("/idm/orgs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class OrgController {

    private final OrgService orgService;

    /**
     * 组织树查询。
     */
    @Operation(summary = "组织树")
    @GetMapping("/tree")
    public Result<List<OrgTreeNode>> tree() {
        return Result.ok(orgService.tree());
    }

    /**
     * 新增组织。
     */
    @Operation(summary = "新增组织")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody OrgForm form) {
        return Result.ok(orgService.create(form));
    }

    /**
     * 修改组织。
     */
    @Operation(summary = "修改组织")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody OrgForm form) {
        orgService.update(id, form);
        return Result.ok();
    }

    /**
     * 删除组织（逻辑删除，须无用户无子组织）。
     */
    @Operation(summary = "删除组织")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        orgService.delete(id);
        return Result.ok();
    }
}
