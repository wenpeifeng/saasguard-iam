package com.auth.system.mapper;

import com.auth.system.entity.IamTenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * SaaS 租户 Mapper。
 */
@Mapper
public interface IamTenantMapper extends BaseMapper<IamTenant> {
}
