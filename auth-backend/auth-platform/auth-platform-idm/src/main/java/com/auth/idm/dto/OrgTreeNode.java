package com.auth.idm.dto;

import lombok.Data;

import java.util.List;

/**
 * 组织树节点 VO。
 */
@Data
public class OrgTreeNode {

    /** 组织 ID */
    private Long id;

    /** 组织编码 */
    private String code;

    /** 组织名称 */
    private String name;

    /** 上级组织 ID */
    private Long parentId;

    /** 排序号 */
    private Integer sort;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 下级组织 */
    private List<OrgTreeNode> children;
}
