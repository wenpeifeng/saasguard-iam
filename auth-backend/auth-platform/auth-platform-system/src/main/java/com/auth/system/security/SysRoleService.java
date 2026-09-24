package com.auth.system.security;

import com.auth.system.entity.SysRole;
import com.auth.system.entity.SysUserRole;
import com.auth.system.mapper.SysRoleMapper;
import com.auth.system.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 平台角色服务：按用户加载角色编码（登录过滤器与登录接口共用）。
 */
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    /**
     * 查询用户持有的平台角色编码列表。
     *
     * @param userId 用户 ID（idm_user.id）
     * @return 角色编码列表（如 SUPER_ADMIN），无角色返回空列表
     */
    public List<String> getRoleCodesByUserId(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream().map(SysRole::getCode).toList();
    }
}
