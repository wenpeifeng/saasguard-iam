package com.auth.app.mapper;

import com.auth.app.entity.AppUserRole;
import org.apache.ibatis.annotations.Mapper;

/** AppUserRole Mapper */
@Mapper
public interface AppUserRoleMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<AppUserRole> {
    /** 恢复已移除角色，历史逻辑删除行不阻碍重新授予。 */
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine = "true")
    @org.apache.ibatis.annotations.Update("UPDATE app_user_role SET deleted=0,update_time=NOW(),update_by=#{actor} WHERE tenant_id=#{tenantId} AND client_id=#{clientId} AND user_id=#{userId} AND role_code=#{roleCode} AND deleted=1")
    int restore(long tenantId, String clientId, Long userId, String roleCode, Long actor);
}
