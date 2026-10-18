package com.auth.system.service;

import com.auth.common.result.PageResult;
import com.auth.system.dto.TenantForm;
import com.auth.system.dto.TenantQuery;
import com.auth.system.dto.TenantVO;
import com.auth.system.dto.TenantUpdateForm;
import com.auth.system.entity.IamTenant;

/**
 * SaaS 租户管理服务。
 */
public interface TenantService {

    /** 分页查询租户。 */
    PageResult<TenantVO> page(TenantQuery query);

    /** 查询租户详情。 */
    TenantVO get(Long id);

    /** 创建租户并初始化根组织与租户管理员。 */
    Long create(TenantForm form);

    /** 修改租户配置。 */
    void update(Long id, TenantUpdateForm form);

    /** 启用或停用租户。 */
    void changeStatus(Long id, Integer status);

    /** 按登录代码解析并校验可用租户。 */
    IamTenant requireActive(String code);

    /** 按租户 ID 加载并校验可用租户。 */
    IamTenant requireActive(Long id);

    /** 判断租户当前是否允许认证和访问。 */
    boolean isActive(Long id);
}
