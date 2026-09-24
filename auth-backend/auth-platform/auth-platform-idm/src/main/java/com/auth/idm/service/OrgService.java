package com.auth.idm.service;

import com.auth.idm.dto.OrgForm;
import com.auth.idm.dto.OrgTreeNode;

import java.util.List;

/**
 * 组织服务：树形主数据维护（组织属于 idm 主数据，与业务系统数据权限无关）。
 */
public interface OrgService {

    /**
     * 查询组织树（全量，前端整树展示/选择）。
     *
     * @return 根节点列表（森林）
     */
    List<OrgTreeNode> tree();

    /**
     * 新增组织。
     *
     * @param form 组织表单
     * @return 新组织 ID
     */
    Long create(OrgForm form);

    /**
     * 修改组织（名称/排序/状态/上级）。
     *
     * @param id   组织 ID
     * @param form 组织表单
     */
    void update(Long id, OrgForm form);

    /**
     * 删除组织（逻辑删除）。
     *
     * @param id 组织 ID
     * @throws com.auth.common.exception.BusinessException 组织下仍有用户或有子组织
     */
    void delete(Long id);
}
