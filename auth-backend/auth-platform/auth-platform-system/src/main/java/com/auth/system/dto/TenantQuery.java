package com.auth.system.dto;

import com.auth.framework.mybatis.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户分页查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantQuery extends PageQuery {

    /** 代码、名称或域名关键字 */
    private String keyword;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 套餐代码 */
    private String planCode;
}
