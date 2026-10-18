package com.auth.system.service.impl;

import com.auth.common.exception.BusinessException;
import com.auth.common.result.PageResult;
import com.auth.framework.mybatis.PageUtils;
import com.auth.framework.security.SecurityUtils;
import com.auth.system.dto.TenantForm;
import com.auth.system.dto.TenantQuery;
import com.auth.system.dto.TenantVO;
import com.auth.system.dto.TenantUpdateForm;
import com.auth.system.entity.IamTenant;
import com.auth.system.event.TenantCreatedEvent;
import com.auth.system.mapper.IamTenantMapper;
import com.auth.system.service.TenantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * SaaS 租户服务实现。
 */
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private static final String DEFAULT_TENANT_CODE = "demo";

    private final IamTenantMapper tenantMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PageResult<TenantVO> page(TenantQuery query) {
        LambdaQueryWrapper<IamTenant> wrapper = new LambdaQueryWrapper<IamTenant>()
                .and(query.getKeyword() != null && !query.getKeyword().isBlank(), w -> w
                        .like(IamTenant::getCode, query.getKeyword())
                        .or().like(IamTenant::getName, query.getKeyword())
                        .or().like(IamTenant::getDomain, query.getKeyword()))
                .eq(query.getStatus() != null, IamTenant::getStatus, query.getStatus())
                .eq(query.getPlanCode() != null && !query.getPlanCode().isBlank(),
                        IamTenant::getPlanCode, query.getPlanCode())
                .orderByDesc(IamTenant::getCreateTime);
        return PageUtils.of(tenantMapper, wrapper, query, IamTenant.class, TenantVO::from);
    }

    @Override
    public TenantVO get(Long id) {
        return TenantVO.from(requireTenant(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TenantForm form) {
        long duplicate = tenantMapper.selectCount(new LambdaQueryWrapper<IamTenant>()
                .eq(IamTenant::getCode, form.getCode()));
        if (duplicate > 0) {
            throw new BusinessException("租户代码已存在");
        }
        if (form.getDomain() != null && !form.getDomain().isBlank()
                && tenantMapper.selectCount(new LambdaQueryWrapper<IamTenant>()
                .eq(IamTenant::getDomain, form.getDomain())) > 0) {
            throw new BusinessException("租户域名已被使用");
        }
        IamTenant tenant = new IamTenant();
        tenant.setTenantId(0L);
        apply(tenant, form);
        tenant.setStatus(1);
        tenant.setCreateBy(SecurityUtils.getCurrentUserId());
        tenant.setCreateTime(LocalDateTime.now());
        tenant.setUpdateBy(SecurityUtils.getCurrentUserId());
        tenant.setUpdateTime(LocalDateTime.now());
        tenant.setDeleted(0);
        tenantMapper.insert(tenant);
        eventPublisher.publishEvent(new TenantCreatedEvent(tenant.getId(), tenant.getName(),
                form.getAdminUsername(), form.getAdminRealName(), form.getAdminPassword()));
        return tenant.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TenantUpdateForm form) {
        requireTenant(id);
        if (form.getDomain() != null && !form.getDomain().isBlank()
                && tenantMapper.selectCount(new LambdaQueryWrapper<IamTenant>()
                .eq(IamTenant::getDomain, form.getDomain())
                .ne(IamTenant::getId, id)) > 0) {
            throw new BusinessException("租户域名已被使用");
        }
        IamTenant patch = new IamTenant();
        patch.setId(id);
        applyUpdate(patch, form);
        patch.setUpdateBy(SecurityUtils.getCurrentUserId());
        patch.setUpdateTime(LocalDateTime.now());
        tenantMapper.updateById(patch);
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("租户状态仅支持 0 或 1");
        }
        IamTenant existing = requireTenant(id);
        if (DEFAULT_TENANT_CODE.equals(existing.getCode()) && status == 0) {
            throw new BusinessException("演示租户不可停用");
        }
        IamTenant patch = new IamTenant();
        patch.setId(id);
        patch.setStatus(status);
        patch.setUpdateBy(SecurityUtils.getCurrentUserId());
        patch.setUpdateTime(LocalDateTime.now());
        tenantMapper.updateById(patch);
    }

    @Override
    public IamTenant requireActive(String code) {
        String resolved = code == null || code.isBlank() ? DEFAULT_TENANT_CODE : code.trim().toLowerCase();
        IamTenant tenant = tenantMapper.selectOne(new LambdaQueryWrapper<IamTenant>()
                .eq(IamTenant::getCode, resolved));
        if (tenant == null) {
            throw new BusinessException("租户不存在");
        }
        validateActive(tenant);
        return tenant;
    }

    @Override
    public IamTenant requireActive(Long id) {
        IamTenant tenant = requireTenant(id);
        validateActive(tenant);
        return tenant;
    }

    @Override
    public boolean isActive(Long id) {
        if (id == null) {
            return false;
        }
        IamTenant tenant = tenantMapper.selectById(id);
        return tenant != null
                && Integer.valueOf(1).equals(tenant.getStatus())
                && (tenant.getExpiresAt() == null || tenant.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    /** 统一校验租户服务状态，避免认证与业务入口出现不同口径。 */
    private void validateActive(IamTenant tenant) {
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            throw new BusinessException("租户已停用，请联系平台管理员");
        }
        if (tenant.getExpiresAt() != null && tenant.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("租户服务已到期，请联系平台管理员");
        }
    }

    /** 查询存在的租户。 */
    private IamTenant requireTenant(Long id) {
        IamTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BusinessException("租户不存在");
        }
        return tenant;
    }

    /** 从表单复制可维护字段。 */
    private void apply(IamTenant tenant, TenantForm form) {
        tenant.setCode(form.getCode());
        tenant.setName(form.getName());
        tenant.setDomain(form.getDomain());
        tenant.setPlanCode(form.getPlanCode());
        tenant.setLogoUrl(form.getLogoUrl());
        tenant.setPrimaryColor(form.getPrimaryColor() == null ? "#3157D5" : form.getPrimaryColor());
        tenant.setLocale(form.getLocale() == null ? "zh-CN" : form.getLocale());
        tenant.setTimezone(form.getTimezone() == null ? "Asia/Shanghai" : form.getTimezone());
        tenant.setMaxUsers(form.getMaxUsers() == null ? 0 : form.getMaxUsers());
        tenant.setExpiresAt(form.getExpiresAt());
    }

    /** 复制创建后仍允许维护的租户配置。 */
    private void applyUpdate(IamTenant tenant, TenantUpdateForm form) {
        tenant.setName(form.getName());
        tenant.setDomain(form.getDomain());
        tenant.setPlanCode(form.getPlanCode());
        tenant.setLogoUrl(form.getLogoUrl());
        tenant.setPrimaryColor(form.getPrimaryColor());
        tenant.setLocale(form.getLocale());
        tenant.setTimezone(form.getTimezone());
        tenant.setMaxUsers(form.getMaxUsers());
        tenant.setExpiresAt(form.getExpiresAt());
    }
}
