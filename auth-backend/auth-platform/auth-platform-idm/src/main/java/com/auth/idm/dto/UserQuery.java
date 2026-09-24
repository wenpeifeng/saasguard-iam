package com.auth.idm.dto;

import com.auth.framework.mybatis.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 主账号分页查询对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQuery extends PageQuery {

    /** 关键字（姓名/工号/登录名模糊匹配） */
    private String keyword;

    /** 组织 ID（含子组织，null=全部） */
    private Long orgId;

    /** 全局状态：1-在职 2-离职 3-全局停用（null=全部） */
    private Integer status;
}
