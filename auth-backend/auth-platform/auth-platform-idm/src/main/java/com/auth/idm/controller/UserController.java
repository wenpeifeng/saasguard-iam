package com.auth.idm.controller;

import com.auth.common.result.PageResult;
import com.auth.common.result.Result;
import com.auth.idm.dto.ResetPwdForm;
import com.auth.idm.dto.TransferForm;
import com.auth.idm.dto.UserForm;
import com.auth.idm.dto.UserQuery;
import com.auth.idm.dto.UserVO;
import com.auth.idm.service.IdmUserService;
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

/**
 * 主账号接口 —— 生命周期全部收口于此。
 * <p>
 * 权限：平台超管维护主账号；P2 起应用管理员经应用维度视图访问（另设接口）。
 */
@Tag(name = "主账号管理")
@RestController
@RequestMapping("/idm/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserController {

    private final IdmUserService userService;

    /**
     * 主账号分页查询。
     */
    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageResult<UserVO>> page(UserQuery query) {
        return Result.ok(userService.page(query));
    }

    /**
     * 用户详情。
     */
    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public Result<UserVO> get(@PathVariable Long id) {
        return Result.ok(userService.get(id));
    }

    /**
     * 入职建号。
     */
    @Operation(summary = "入职建号")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody UserForm form) {
        return Result.ok(userService.create(form));
    }

    /**
     * 修改基本信息（工号不可改）。
     */
    @Operation(summary = "修改基本信息")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserForm form) {
        userService.update(id, form);
        return Result.ok();
    }

    /**
     * 转岗（改组织，事件留痕）。
     */
    @Operation(summary = "转岗")
    @PutMapping("/{id}/transfer")
    public Result<Void> transfer(@PathVariable Long id, @Valid @RequestBody TransferForm form) {
        userService.transfer(id, form);
        return Result.ok();
    }

    /**
     * 离职（终态，一键全系统失效）。
     */
    @Operation(summary = "离职")
    @PutMapping("/{id}/leave")
    public Result<Void> leave(@PathVariable Long id) {
        userService.leave(id);
        return Result.ok();
    }

    /**
     * 全局状态变更（在职 ⇄ 全局停用）。
     */
    @Operation(summary = "全局状态变更")
    @PutMapping("/{id}/status/{status}")
    public Result<Void> changeStatus(@PathVariable Long id, @PathVariable Integer status) {
        userService.changeStatus(id, status);
        return Result.ok();
    }

    /**
     * 重置口令。
     */
    @Operation(summary = "重置口令")
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPwdForm form) {
        userService.resetPassword(id, form);
        return Result.ok();
    }

    /**
     * 删除账号（逻辑删除，仅 extreme 场景，正常应走离职）。
     */
    @Operation(summary = "删除账号")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        throw new UnsupportedOperationException("主账号不提供物理删除入口，请使用离职流程");
    }
}
