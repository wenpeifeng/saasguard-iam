package com.auth.idm.service.impl;

import cn.hutool.json.JSONUtil;
import com.auth.common.exception.BusinessException;
import com.auth.common.result.PageResult;
import com.auth.framework.mybatis.PageUtils;
import com.auth.idm.dto.ResetPwdForm;
import com.auth.idm.dto.TransferForm;
import com.auth.idm.dto.UserForm;
import com.auth.idm.dto.UserQuery;
import com.auth.idm.dto.UserVO;
import com.auth.idm.entity.IdmOrg;
import com.auth.idm.entity.IdmUser;
import com.auth.idm.entity.IdmUserEvent;
import com.auth.idm.mapper.IdmOrgMapper;
import com.auth.idm.mapper.IdmUserEventMapper;
import com.auth.idm.mapper.IdmUserMapper;
import com.auth.idm.service.IdmUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 主账号服务实现 —— 生命周期状态机唯一收口。
 * <p>
 * 状态迁移合法性：
 * <pre>
 *   在职(1) --离职--▶ 离职(2，终态)
 *   在职(1) ←--恢复-- 全局停用(3)
 * </pre>
 * 离职后除查询外的一切写操作（转岗/状态变更/重置口令/应用级启停）都被拒绝。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdmUserServiceImpl implements IdmUserService {

    private final IdmUserMapper userMapper;
    private final IdmUserEventMapper eventMapper;
    private final IdmOrgMapper orgMapper;
    private final PasswordEncoder passwordEncoder;
    private final com.auth.system.security.JwtTokenProvider tokenProvider;

    @Override
    public PageResult<UserVO> page(UserQuery query) {
        LambdaQueryWrapper<IdmUser> wrapper = new LambdaQueryWrapper<IdmUser>()
                .and(query.getKeyword() != null && !query.getKeyword().isBlank(), w -> w
                        .like(IdmUser::getRealName, query.getKeyword())
                        .or().like(IdmUser::getEmployeeNo, query.getKeyword())
                        .or().like(IdmUser::getUsername, query.getKeyword()))
                .eq(query.getOrgId() != null, IdmUser::getOrgId, query.getOrgId())
                .eq(query.getStatus() != null, IdmUser::getStatus, query.getStatus())
                .orderByDesc(IdmUser::getId);
        Map<Long, String> orgNames = orgMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(IdmOrg::getId, IdmOrg::getName));
        return PageUtils.of(userMapper, wrapper, query, IdmUser.class, toVo(orgNames));
    }

    @Override
    public UserVO get(Long id) {
        IdmUser user = requireUser(id);
        IdmOrg org = user.getOrgId() != null ? orgMapper.selectById(user.getOrgId()) : null;
        Map<Long, String> orgNames = org != null ? Map.of(org.getId(), org.getName()) : Map.of();
        return toVo(orgNames).apply(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserForm form) {
        if (userMapper.selectCount(new LambdaQueryWrapper<IdmUser>()
                .eq(IdmUser::getEmployeeNo, form.getEmployeeNo())) > 0) {
            throw new BusinessException("工号已存在：%s", form.getEmployeeNo());
        }
        requireActiveOrg(form.getOrgId());
        IdmUser user = new IdmUser();
        user.setEmployeeNo(form.getEmployeeNo());
        // 登录名默认同工号（预留：后续可放开独立登录名）
        user.setUsername(form.getEmployeeNo());
        user.setRealName(form.getRealName());
        user.setOrgId(form.getOrgId());
        user.setPosition(form.getPosition());
        user.setEmail(form.getEmail());
        user.setPhone(form.getPhone());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setStatus(IdmUser.STATUS_ACTIVE);
        user.setEntryDate(form.getEntryDate() != null ? form.getEntryDate() : LocalDate.now());
        userMapper.insert(user);
        recordEvent(user.getId(), IdmUserEvent.TYPE_CREATE, JSONUtil.createObj()
                .set("employeeNo", user.getEmployeeNo())
                .set("orgId", user.getOrgId()).toString());
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserForm form) {
        IdmUser user = requireUser(id);
        requireNotLeft(user);
        IdmUser patch = new IdmUser();
        patch.setId(id);
        patch.setRealName(form.getRealName());
        patch.setPosition(form.getPosition());
        patch.setEmail(form.getEmail());
        patch.setPhone(form.getPhone());
        userMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long id, TransferForm form) {
        IdmUser user = requireUser(id);
        requireNotLeft(user);
        requireActiveUser(user);
        if (form.getTargetOrgId().equals(user.getOrgId())) {
            throw new BusinessException("目标组织与当前组织相同");
        }
        requireActiveOrg(form.getTargetOrgId());
        Long before = user.getOrgId();
        IdmUser patch = new IdmUser();
        patch.setId(id);
        patch.setOrgId(form.getTargetOrgId());
        userMapper.updateById(patch);
        recordEvent(id, IdmUserEvent.TYPE_TRANSFER, JSONUtil.createObj()
                .set("fromOrgId", before)
                .set("toOrgId", form.getTargetOrgId())
                .set("reason", form.getReason()).toString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leave(Long id) {
        IdmUser user = requireUser(id);
        if (user.getStatus() == IdmUser.STATUS_LEFT) {
            throw new BusinessException("用户已是离职状态（离职为终态，不可重复操作）");
        }
        IdmUser patch = new IdmUser();
        patch.setId(id);
        patch.setStatus(IdmUser.STATUS_LEFT);
        patch.setLeaveDate(LocalDate.now());
        userMapper.updateById(patch);
        recordEvent(id, IdmUserEvent.TYPE_LEAVE, null);
        // 一键全系统失效的令牌侧收口：删除该用户全部 Redis 在线会话键（含管理端与 OIDC 会话）
        long killed = tokenProvider.invalidateAllUserSessions(id);
        if (killed > 0) {
            log.info("[离职下线] userId={} 删除在线会话 {} 个", id, killed);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        IdmUser user = requireUser(id);
        if (user.getStatus() == IdmUser.STATUS_LEFT) {
            throw new BusinessException("用户已离职，离职是终态，不可变更状态");
        }
        if (status != IdmUser.STATUS_ACTIVE && status != IdmUser.STATUS_SUSPENDED) {
            throw new BusinessException("非法目标状态：%s", status);
        }
        if (status.equals(user.getStatus())) {
            return;
        }
        IdmUser patch = new IdmUser();
        patch.setId(id);
        patch.setStatus(status);
        userMapper.updateById(patch);
        recordEvent(id, IdmUserEvent.TYPE_STATUS_CHANGE, JSONUtil.createObj()
                .set("from", user.getStatus()).set("to", status).toString());
        // 全局停用即时生效：下线该用户全部在线会话；恢复在职不自动恢复会话（需重新登录）
        if (status == IdmUser.STATUS_SUSPENDED) {
            tokenProvider.invalidateAllUserSessions(id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, ResetPwdForm form) {
        IdmUser user = requireUser(id);
        requireNotLeft(user);
        IdmUser patch = new IdmUser();
        patch.setId(id);
        patch.setPassword(passwordEncoder.encode(form.getPassword()));
        userMapper.updateById(patch);
        recordEvent(id, IdmUserEvent.TYPE_RESET_PWD, null);
    }

    /** 加载用户，不存在即抛业务异常 */
    private IdmUser requireUser(Long id) {
        IdmUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    /** 离职终态校验：离职后一切写操作被拒绝 */
    private void requireNotLeft(IdmUser user) {
        if (user.getStatus() == IdmUser.STATUS_LEFT) {
            throw new BusinessException("用户已离职，不允许操作");
        }
    }

    /** 仅在职用户可操作（全局停用状态需先恢复） */
    private void requireActiveUser(IdmUser user) {
        if (user.getStatus() != IdmUser.STATUS_ACTIVE) {
            throw new BusinessException("仅在职员工可操作，当前状态：%s", user.getStatus());
        }
    }

    /** 组织存在且启用 */
    private void requireActiveOrg(Long orgId) {
        IdmOrg org = orgMapper.selectById(orgId);
        if (org == null) {
            throw new BusinessException("组织不存在");
        }
        if (org.getStatus() == 0) {
            throw new BusinessException("组织已停用：%s", org.getName());
        }
    }

    /** 写生命周期事件流水（操作人由审计填充器自动补齐） */
    private void recordEvent(Long userId, String type, String detail) {
        IdmUserEvent event = new IdmUserEvent();
        event.setUserId(userId);
        event.setEventType(type);
        event.setDetail(detail);
        eventMapper.insert(event);
    }

    /** 实体 → VO 转换器（口令永不出现） */
    private Function<IdmUser, UserVO> toVo(Map<Long, String> orgNames) {
        return user -> {
            UserVO vo = new UserVO();
            vo.setId(user.getId());
            vo.setEmployeeNo(user.getEmployeeNo());
            vo.setUsername(user.getUsername());
            vo.setRealName(user.getRealName());
            vo.setOrgId(user.getOrgId());
            vo.setOrgName(orgNames.get(user.getOrgId()));
            vo.setPosition(user.getPosition());
            vo.setEmail(user.getEmail());
            vo.setPhone(user.getPhone());
            vo.setStatus(user.getStatus());
            vo.setEntryDate(user.getEntryDate());
            vo.setLeaveDate(user.getLeaveDate());
            vo.setCreateTime(user.getCreateTime());
            return vo;
        };
    }
}
