package com.auth.app.service.impl;

import com.auth.app.dto.AssignQuery;
import com.auth.app.dto.AssignVO;
import com.auth.app.dto.SyncUserVO;
import com.auth.app.entity.AppClient;
import com.auth.app.entity.AppUserAssign;
import com.auth.app.entity.AppUserRole;
import com.auth.app.mapper.AppUserAssignMapper;
import com.auth.app.mapper.AppUserRoleMapper;
import com.auth.app.service.AppAssignService;
import com.auth.app.service.AppClientService;
import com.auth.common.exception.BusinessException;
import com.auth.common.result.PageResult;
import com.auth.framework.mybatis.PageUtils;
import com.auth.idm.entity.IdmOrg;
import com.auth.idm.entity.IdmUser;
import com.auth.idm.mapper.IdmOrgMapper;
import com.auth.idm.mapper.IdmUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 应用分配服务实现 —— 进门权/应用级启停/角色标签/同步的唯一收口。
 * <p>
 * 离职是最强状态：分配、启停、角色操作全部先过"仅在职员工可操作"校验。
 */
@Service
@RequiredArgsConstructor
public class AppAssignServiceImpl implements AppAssignService {

    private final AppUserAssignMapper assignMapper;
    private final AppUserRoleMapper userRoleMapper;
    private final AppClientService clientService;
    private final IdmUserMapper userMapper;
    private final IdmOrgMapper orgMapper;

    @Override
    public PageResult<AssignVO> pageAssigned(String clientId, AssignQuery query) {
        requireClientEnabledOrAny(clientId);
        LambdaQueryWrapper<AppUserAssign> wrapper = new LambdaQueryWrapper<AppUserAssign>()
                .eq(AppUserAssign::getClientId, clientId)
                .eq(query.getStatus() != null, AppUserAssign::getStatus, query.getStatus())
                .in(query.getKeyword() != null && !query.getKeyword().isBlank(),
                        AppUserAssign::getUserId, matchUserIds(query.getKeyword()))
                .orderByDesc(AppUserAssign::getId);
        // 先分页取分配行，再补用户/组织/角色信息（避免跨表 join 与租户插件冲突）
        PageResult<AppUserAssign> page = PageUtils.of(assignMapper, wrapper, query, AppUserAssign.class, v -> v);
        return PageResult.of(enrich(clientId, page.getRecords()),
                page.getTotal(), page.getPageNum(), page.getPageSize());
    }

    /** 按关键字（姓名/工号/登录名）预筛用户 ID 集合，无匹配返回 -1 保证空结果 */
    private List<Long> matchUserIds(String keyword) {
        List<Long> ids = userMapper.selectList(new LambdaQueryWrapper<IdmUser>()
                        .like(IdmUser::getRealName, keyword)
                        .or().like(IdmUser::getEmployeeNo, keyword)
                        .or().like(IdmUser::getUsername, keyword)).stream()
                .map(IdmUser::getId).toList();
        return ids.isEmpty() ? List.of(-1L) : ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(String clientId, List<Long> userIds) {
        requireClientEnabledOrAny(clientId);
        for (Long userId : userIds == null ? List.<Long>of() : userIds) {
            IdmUser user = requireUser(userId);
            requireActiveUser(user);
            Long exists = assignMapper.selectCount(new LambdaQueryWrapper<AppUserAssign>()
                    .eq(AppUserAssign::getClientId, clientId)
                    .eq(AppUserAssign::getUserId, userId));
            if (exists > 0) {
                continue;
            }
            AppUserAssign assign = new AppUserAssign();
            assign.setClientId(clientId);
            assign.setUserId(userId);
            assign.setStatus(AppUserAssign.STATUS_ENABLED);
            assignMapper.insert(assign);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(String clientId, Long userId) {
        AppUserAssign assign = requireAssign(clientId, userId);
        assignMapper.deleteById(assign.getId());
        userRoleMapper.delete(new LambdaQueryWrapper<AppUserRole>()
                .eq(AppUserRole::getClientId, clientId)
                .eq(AppUserRole::getUserId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setAppStatus(String clientId, Long userId, boolean enabled) {
        AppUserAssign assign = requireAssign(clientId, userId);
        IdmUser user = requireUser(userId);
        // 前置校验收口：仅对在职员工可操作（离职/全局停用一律拒绝）
        if (user.getStatus() != IdmUser.STATUS_ACTIVE) {
            throw new BusinessException("仅在职员工可操作，当前状态：%s（离职/全局停用请在认证中心处置）",
                    user.getStatus());
        }
        if ((enabled ? AppUserAssign.STATUS_ENABLED : AppUserAssign.STATUS_DISABLED) == assign.getStatus()) {
            return;
        }
        AppUserAssign patch = new AppUserAssign();
        patch.setId(assign.getId());
        patch.setStatus(enabled ? AppUserAssign.STATUS_ENABLED : AppUserAssign.STATUS_DISABLED);
        assignMapper.updateById(patch);
    }

    @Override
    public void setAppStatusByEmployeeNo(String clientId, String employeeNo, boolean enabled) {
        IdmUser user = userMapper.selectOne(new LambdaQueryWrapper<IdmUser>()
                .eq(IdmUser::getEmployeeNo, employeeNo));
        if (user == null) {
            throw new BusinessException("工号不存在：%s", employeeNo);
        }
        setAppStatus(clientId, user.getId(), enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantRole(String clientId, Long userId, String roleCode) {
        requireAssign(clientId, userId);
        IdmUser user = requireUser(userId);
        if (user.getStatus() != IdmUser.STATUS_ACTIVE) {
            throw new BusinessException("仅在职员工可操作");
        }
        // 角色必须为应用已声明角色
        if (clientService.listRoles(clientId).stream().noneMatch(r -> r.getRoleCode().equals(roleCode))) {
            throw new BusinessException("角色未由应用声明：%s", roleCode);
        }
        Long exists = userRoleMapper.selectCount(new LambdaQueryWrapper<AppUserRole>()
                .eq(AppUserRole::getClientId, clientId)
                .eq(AppUserRole::getUserId, userId)
                .eq(AppUserRole::getRoleCode, roleCode));
        if (exists > 0) {
            return;
        }
        AppUserRole role = new AppUserRole();
        role.setClientId(clientId);
        role.setUserId(userId);
        role.setRoleCode(roleCode);
        userRoleMapper.insert(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeRole(String clientId, Long userId, String roleCode) {
        requireAssign(clientId, userId);
        userRoleMapper.delete(new LambdaQueryWrapper<AppUserRole>()
                .eq(AppUserRole::getClientId, clientId)
                .eq(AppUserRole::getUserId, userId)
                .eq(AppUserRole::getRoleCode, roleCode));
    }

    @Override
    public AssignVO checkLoginAccess(String clientId, Long userId) {
        AppClient client = clientService.loadEnabledClient(clientId);
        if (client == null) {
            return null;
        }
        AppUserAssign assign = assignMapper.selectOne(new LambdaQueryWrapper<AppUserAssign>()
                .eq(AppUserAssign::getClientId, clientId)
                .eq(AppUserAssign::getUserId, userId));
        if (assign == null || assign.getStatus() != AppUserAssign.STATUS_ENABLED) {
            return null;
        }
        List<AssignVO> vos = enrich(clientId, List.of(assign));
        return vos.isEmpty() ? null : vos.get(0);
    }

    @Override
    public List<SyncUserVO> syncUsers(String clientId, LocalDateTime updatedSince) {
        requireClientEnabledOrAny(clientId);
        LambdaQueryWrapper<AppUserAssign> wrapper = new LambdaQueryWrapper<AppUserAssign>()
                .eq(AppUserAssign::getClientId, clientId)
                .gt(updatedSince != null, AppUserAssign::getUpdateTime, updatedSince)
                .orderByAsc(AppUserAssign::getId);
        List<AppUserAssign> assigns = assignMapper.selectList(wrapper);
        if (assigns.isEmpty()) {
            return List.of();
        }
        Map<Long, IdmUser> users = userMapper.selectBatchIds(
                assigns.stream().map(AppUserAssign::getUserId).toList()).stream()
                .collect(Collectors.toMap(IdmUser::getId, Function.identity()));
        Map<Long, String> orgNames = orgMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(IdmOrg::getId, IdmOrg::getName));
        Map<Long, List<String>> rolesByUser = userRoleMapper.selectList(new LambdaQueryWrapper<AppUserRole>()
                        .eq(AppUserRole::getClientId, clientId)).stream()
                .collect(Collectors.groupingBy(AppUserRole::getUserId,
                        Collectors.mapping(AppUserRole::getRoleCode, Collectors.toList())));

        return assigns.stream().map(assign -> {
            IdmUser user = users.get(assign.getUserId());
            SyncUserVO vo = new SyncUserVO();
            vo.setUpdateTime(assign.getUpdateTime() != null ? assign.getUpdateTime() : assign.getCreateTime());
            if (user == null) {
                vo.setEmployeeNo("UNKNOWN-" + assign.getUserId());
                vo.setActive(false);
                vo.setInactiveReason("REVOKED");
                return vo;
            }
            vo.setEmployeeNo(user.getEmployeeNo());
            vo.setUsername(user.getUsername());
            vo.setRealName(user.getRealName());
            vo.setOrgName(orgNames.get(user.getOrgId()));
            vo.setPosition(user.getPosition());
            vo.setRoles(rolesByUser.getOrDefault(assign.getUserId(), List.of()));
            // active 合成：全局在职 且 应用级启用
            if (user.getStatus() == IdmUser.STATUS_LEFT) {
                vo.setActive(false);
                vo.setInactiveReason("LEFT");
            } else if (user.getStatus() == IdmUser.STATUS_SUSPENDED) {
                vo.setActive(false);
                vo.setInactiveReason("SUSPENDED");
            } else if (assign.getStatus() != AppUserAssign.STATUS_ENABLED) {
                vo.setActive(false);
                vo.setInactiveReason("APP_DISABLED");
            } else {
                vo.setActive(true);
            }
            return vo;
        }).toList();
    }

    @Override
    public Map<Long, List<UserAppSummary>> summariesByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<AppUserAssign> assigns = assignMapper.selectList(new LambdaQueryWrapper<AppUserAssign>()
                .in(AppUserAssign::getUserId, userIds));
        if (assigns.isEmpty()) {
            return Map.of();
        }
        // 应用名（app_client 为共享表，分页上限内一次取全）
        Map<String, String> appNames = clientService.page(
                new com.auth.framework.mybatis.PageQuery() {{ setPageSize(500); }}).getRecords().stream()
                .collect(Collectors.toMap(c -> c.getClientId(), c -> c.getAppName()));
        // 角色标签按（用户, 应用）精确归属（app_user_role.client_id 即归属应用）
        Map<String, List<String>> rolesByKey = userRoleMapper.selectList(new LambdaQueryWrapper<AppUserRole>()
                        .in(AppUserRole::getUserId, userIds)).stream()
                .collect(Collectors.groupingBy(r -> r.getUserId() + ":" + r.getClientId(),
                        Collectors.mapping(AppUserRole::getRoleCode, Collectors.toList())));

        Map<Long, List<UserAppSummary>> result = new java.util.LinkedHashMap<>();
        for (AppUserAssign assign : assigns) {
            UserAppSummary summary = new UserAppSummary();
            summary.setClientId(assign.getClientId());
            summary.setAppName(appNames.getOrDefault(assign.getClientId(), assign.getClientId()));
            summary.setAppStatus(assign.getStatus());
            summary.setRoles(rolesByKey.getOrDefault(assign.getUserId() + ":" + assign.getClientId(), List.of()));
            result.computeIfAbsent(assign.getUserId(), k -> new java.util.ArrayList<>()).add(summary);
        }
        return result;
    }

    /** 分配行补全用户/组织/角色信息 */
    private List<AssignVO> enrich(String clientId, List<AppUserAssign> assigns) {
        if (assigns.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = assigns.stream().map(AppUserAssign::getUserId).collect(Collectors.toSet());
        Map<Long, IdmUser> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(IdmUser::getId, Function.identity()));
        Map<Long, String> orgNames = orgMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(IdmOrg::getId, IdmOrg::getName));
        Map<Long, List<String>> rolesByUser = userRoleMapper.selectList(new LambdaQueryWrapper<AppUserRole>()
                        .eq(AppUserRole::getClientId, clientId)).stream()
                .collect(Collectors.groupingBy(AppUserRole::getUserId,
                        Collectors.mapping(AppUserRole::getRoleCode, Collectors.toList())));

        return assigns.stream().map(assign -> {
            AssignVO vo = new AssignVO();
            vo.setAssignId(assign.getId());
            vo.setUserId(assign.getUserId());
            vo.setAppStatus(assign.getStatus());
            vo.setAssignTime(assign.getCreateTime());
            vo.setUpdateTime(assign.getUpdateTime() != null ? assign.getUpdateTime() : assign.getCreateTime());
            vo.setRoles(rolesByUser.getOrDefault(assign.getUserId(), List.of()));
            IdmUser user = users.get(assign.getUserId());
            if (user != null) {
                vo.setEmployeeNo(user.getEmployeeNo());
                vo.setUsername(user.getUsername());
                vo.setRealName(user.getRealName());
                vo.setOrgName(orgNames.get(user.getOrgId()));
                vo.setPosition(user.getPosition());
                vo.setGlobalStatus(user.getStatus());
            }
            return vo;
        }).toList();
    }

    /** 应用存在（管理端查询允许查看停用应用） */
    private void requireClientEnabledOrAny(String clientId) {
        if (clientService.loadEnabledClient(clientId) == null) {
            // 停用应用仍可查看分配（管理场景），仅要求存在 —— 通过 get() 校验存在性
            clientService.get(clientId);
        }
    }

    private AppUserAssign requireAssign(String clientId, Long userId) {
        AppUserAssign assign = assignMapper.selectOne(new LambdaQueryWrapper<AppUserAssign>()
                .eq(AppUserAssign::getClientId, clientId)
                .eq(AppUserAssign::getUserId, userId));
        if (assign == null) {
            throw new BusinessException("用户未分配到该应用：userId=%s clientId=%s", userId, clientId);
        }
        return assign;
    }

    private IdmUser requireUser(Long userId) {
        IdmUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private void requireActiveUser(IdmUser user) {
        if (user.getStatus() != IdmUser.STATUS_ACTIVE) {
            throw new BusinessException("仅在职员工可分配应用，当前状态：%s", user.getStatus());
        }
    }
}
