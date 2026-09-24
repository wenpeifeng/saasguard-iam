package com.auth.app.dto;

import com.auth.framework.mybatis.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应用分配分页查询条件（业务系统管理端的用户列表 = 已分配到本应用的用户）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AssignQuery extends PageQuery {

    /** 姓名/工号/登录名关键字 */
    private String keyword;

    /** 应用级状态过滤：1-启用 0-应用级禁用 */
    private Integer status;
}
