package com.auth.app.mapper;

import com.auth.app.entity.AppUserAssign;
import org.apache.ibatis.annotations.Mapper;

/** AppUserAssign Mapper */
@Mapper
public interface AppUserAssignMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<AppUserAssign> {
    /** 恢复已移除的分配；保留唯一关系，避免重复分配触发唯一键冲突。 */
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine = "true")
    @org.apache.ibatis.annotations.Update("UPDATE app_user_assign SET deleted=0,status=1,create_time=NOW(),update_time=NOW(),update_by=#{actor} WHERE tenant_id=#{tenantId} AND client_id=#{clientId} AND user_id=#{userId} AND deleted=1")
    int restore(long tenantId, String clientId, Long userId, Long actor);

    /** 同步包含移除记录与员工生命周期变更，时间边界包含等号，消费端按工号幂等处理。 */
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine = "true")
    @org.apache.ibatis.annotations.Select("""
        <script>
        SELECT a.* FROM app_user_assign a
        JOIN idm_user u ON u.id=a.user_id AND u.tenant_id=a.tenant_id
        LEFT JOIN idm_org o ON o.id=u.org_id AND o.tenant_id=a.tenant_id
        WHERE a.tenant_id=#{tenantId} AND a.client_id=#{clientId}
        <if test="since != null">
          AND (COALESCE(a.update_time,a.create_time) &gt;= #{since}
            OR COALESCE(u.update_time,u.create_time) &gt;= #{since}
            OR COALESCE(o.update_time,o.create_time) &gt;= #{since})
        </if>
        ORDER BY a.id
        </script>
        """)
    java.util.List<AppUserAssign> forSync(long tenantId, String clientId, java.time.LocalDateTime since);

    /** 分配/移除/角色变更均推进同步时间。 */
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine = "true")
    @org.apache.ibatis.annotations.Update("UPDATE app_user_assign SET update_time=NOW(),update_by=#{actor} WHERE tenant_id=#{tenantId} AND client_id=#{clientId} AND user_id=#{userId}")
    int touch(long tenantId, String clientId, Long userId, Long actor);
}
