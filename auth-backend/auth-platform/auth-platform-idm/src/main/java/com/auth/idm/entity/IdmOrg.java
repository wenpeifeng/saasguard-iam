package com.auth.idm.entity;

import com.auth.framework.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织实体（idm_org，树形主数据）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("idm_org")
public class IdmOrg extends BaseEntity {

    /** 组织编码（租户内唯一） */
    private String code;

    /** 组织名称 */
    private String name;

    /** 上级组织 ID（0=根节点） */
    private Long parentId;

    /** 排序号（同级内升序） */
    private Integer sort;

    /** 状态：1-启用 0-停用 */
    private Integer status;
}
