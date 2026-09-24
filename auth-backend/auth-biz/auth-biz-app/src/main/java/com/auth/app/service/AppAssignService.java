package com.auth.app.service;

import com.auth.app.dto.AssignQuery;
import com.auth.app.dto.AssignVO;
import com.auth.app.dto.SyncUserVO;
import com.auth.common.result.PageResult;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 应用分配服务 —— 用户×应用进门权 + 应用级启停 + 角色标签分配 + 同步。
 * <p>
 * 前置校验收口：分配/启停仅对在职员工可操作；离职/全局停用用户一律拒绝
 * （离职是最强状态，业务系统侧的启停请求也会被这里拒绝）。
 */
public interface AppAssignService {

    /** 已分配用户分页（业务系统管理端的用户列表） */
    PageResult<AssignVO> pageAssigned(String clientId, AssignQuery query);

    /**
     * 按用户 ID 批量查"已分配应用 + 角色标签"（主账号列表页的分配概览列用）。
     *
     * @return userId → 该用户在各应用的分配摘要（应用名/角色标签/应用级状态）
     */
    Map<Long, List<UserAppSummary>> summariesByUserIds(List<Long> userIds);

    /** 用户×应用分配摘要（列表页展示用） */
    @Data
    class UserAppSummary {
        private String clientId;
        private String appName;
        /** 应用级状态：1-启用 0-应用级禁用 */
        private Integer appStatus;
        /** 该应用角色标签（如 erp.admin） */
        private List<String> roles;
    }

    /** 分配用户进门权（批量；已存在的跳过） */
    void assign(String clientId, List<Long> userIds);

    /** 回收进门权（逻辑删除分配与该应用角色标签） */
    void revoke(String clientId, Long userId);

    /**
     * 应用级启停（业务系统管理端调用核心）：仅影响本应用。
     *
     * @param enabled true-启用 false-应用级禁用
     */
    void setAppStatus(String clientId, Long userId, boolean enabled);

    /** 按工号做应用级启停（服务间 API 入口，同样受在职校验约束） */
    void setAppStatusByEmployeeNo(String clientId, String employeeNo, boolean enabled);

    /** 授予角色标签（必须为应用已声明角色） */
    void grantRole(String clientId, Long userId, String roleCode);

    /** 收回角色标签 */
    void revokeRole(String clientId, Long userId, String roleCode);

    /**
     * 用户在本应用的登录资格校验（OIDC /authorize 前置）。
     *
     * @return 应用级启用时返回 AssignVO；未分配/应用级禁用/全局不可登录返回 null
     */
    AssignVO checkLoginAccess(String clientId, Long userId);

    /**
     * 用户增量/全量同步（SCIM 风格对账）。
     *
     * @param updatedSince 增量游标（空=全量）
     */
    List<SyncUserVO> syncUsers(String clientId, LocalDateTime updatedSince);
}
