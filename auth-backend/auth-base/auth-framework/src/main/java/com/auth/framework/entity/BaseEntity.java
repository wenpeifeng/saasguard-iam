package com.auth.framework.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类 —— 所有数据表实体的公共字段。
 * <p>
 * 建表规范：业务表统一包含 id/tenant_id/create_by/create_time/update_by/update_time/deleted 七列，
 * 审计四列由 {@link com.auth.framework.config.MybatisPlusConfig} 的审计填充器自动写入，
 * tenant_id 同样自动填充（取 {@link com.auth.framework.context.TenantContextHolder}），
 * deleted 为逻辑删除标记（0 在用 / 1 已删），MP 查询自动过滤已删数据。
 * <p>
 * 租户语义：本期为"预留租户"模型 —— 机制（列 + 插件 + 唯一索引含 tenant_id）就位，
 * 但登录流程不暴露租户，所有数据落在默认租户 1000（与 ERP 对齐），未来子公司/
 * 外部企业接入时放开租户识别即可。平台级共享表（应用注册/平台 RBAC 等）见
 * {@link com.auth.framework.config.TenantConfig#TENANT_SHARED_TABLES}，同样必须含 tenant_id 列（默认 1000）。
 */
@Data
public abstract class BaseEntity implements Serializable {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID（商业隔离维度，租户插件自动 WHERE tenant_id=?），MP 插入自动填充，默认 1000 */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 创建人（用户 ID），MP 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建时间，MP 自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新人（用户 ID），MP 自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 更新时间，MP 自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：0-在用 1-已删，标注 @TableLogic 后 MP 查询自动过滤已删、删除自动转为 UPDATE */
    @TableLogic
    private Integer deleted;
}
