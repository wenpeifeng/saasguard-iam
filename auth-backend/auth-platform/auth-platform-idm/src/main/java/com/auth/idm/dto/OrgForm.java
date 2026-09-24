package com.auth.idm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 组织新增/修改表单。
 */
@Data
public class OrgForm {

    /** 组织编码（租户内唯一） */
    @NotBlank(message = "组织编码不能为空")
    @Size(max = 64, message = "组织编码长度不能超过 64")
    private String code;

    /** 组织名称 */
    @NotBlank(message = "组织名称不能为空")
    @Size(max = 128, message = "组织名称长度不能超过 128")
    private String name;

    /** 上级组织 ID（0=根节点） */
    @NotNull(message = "上级组织不能为空")
    private Long parentId;

    /** 排序号 */
    private Integer sort = 0;

    /** 状态：1-启用 0-停用 */
    private Integer status = 1;
}
