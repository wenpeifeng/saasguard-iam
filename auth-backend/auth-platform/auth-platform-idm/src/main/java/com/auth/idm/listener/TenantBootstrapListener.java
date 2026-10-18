package com.auth.idm.listener;

import com.auth.common.exception.BusinessException;
import com.auth.framework.context.TenantContextHolder;
import com.auth.idm.entity.IdmOrg;
import com.auth.idm.entity.IdmUser;
import com.auth.idm.entity.IdmUserEvent;
import com.auth.idm.entity.OrgType;
import com.auth.idm.mapper.IdmOrgMapper;
import com.auth.idm.mapper.IdmUserMapper;
import com.auth.idm.mapper.IdmUserEventMapper;
import com.auth.system.entity.SysRole;
import com.auth.system.entity.SysUserRole;
import com.auth.system.event.TenantCreatedEvent;
import com.auth.system.mapper.SysRoleMapper;
import com.auth.system.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 新租户身份域初始化器：创建根组织、首位租户管理员并授予租户管理员角色。
 */
@Component
@RequiredArgsConstructor
public class TenantBootstrapListener {

    private final IdmOrgMapper orgMapper;
    private final IdmUserMapper userMapper;
    private final IdmUserEventMapper userEventMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 在租户创建事务内完成身份基础数据初始化，任一步失败则整体回滚。
     *
     * @param event 租户创建事件
     */
    @EventListener
    public void bootstrap(TenantCreatedEvent event) {
        TenantContextHolder.runWithTenant(event.tenantId(), () -> {
            IdmOrg root = new IdmOrg();
            root.setCode("ROOT");
            root.setName(event.tenantName());
            root.setParentId(0L);
            root.setSort(1);
            root.setStatus(1);
            root.setOrgType(OrgType.GROUP);
            root.setTenantId(event.tenantId());
            orgMapper.insert(root);

            IdmUser admin = new IdmUser();
            admin.setTenantId(event.tenantId());
            admin.setEmployeeNo("ADMIN000");
            admin.setUsername(event.adminUsername());
            admin.setRealName(event.adminRealName());
            admin.setOrgId(root.getId());
            admin.setPosition("租户管理员");
            admin.setPassword(passwordEncoder.encode(event.adminPassword()));
            admin.setStatus(IdmUser.STATUS_ACTIVE);
            admin.setEntryDate(LocalDate.now());
            admin.setAccountSource("TENANT_BOOTSTRAP");
            // 动态租户管理员属于正常人员主档；仅 Flyway 种子账号使用 immutable 保护。
            admin.setImmutable(false);
            userMapper.insert(admin);

            IdmUserEvent created = new IdmUserEvent();
            created.setUserId(admin.getId());
            created.setEventType(IdmUserEvent.TYPE_CREATE);
            created.setDetail(cn.hutool.json.JSONUtil.createObj()
                    .set("source", "TENANT_BOOTSTRAP")
                    .set("employeeNo", admin.getEmployeeNo())
                    .set("orgId", root.getId())
                    .toString());
            userEventMapper.insert(created);

            SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getCode, "TENANT_ADMIN"));
            if (role == null) {
                throw new BusinessException("系统缺少 TENANT_ADMIN 平台角色");
            }
            SysUserRole relation = new SysUserRole();
            relation.setTenantId(event.tenantId());
            relation.setUserId(admin.getId());
            relation.setRoleId(role.getId());
            userRoleMapper.insert(relation);
        });
    }
}
