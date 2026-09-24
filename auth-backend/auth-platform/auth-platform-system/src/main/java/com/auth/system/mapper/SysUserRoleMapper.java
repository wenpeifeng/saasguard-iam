package com.auth.system.mapper;

import com.auth.system.entity.SysUserRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-平台角色关联 Mapper。
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
