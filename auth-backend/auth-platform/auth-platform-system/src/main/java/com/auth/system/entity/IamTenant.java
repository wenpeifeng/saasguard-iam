package com.auth.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SaaS 租户实体（iam_tenant）—— 平台级租户目录与品牌、配额、有效期配置。
 */
@Data
@TableName("iam_tenant")
public class IamTenant {

    /** 主键，也是租户隔离字段 tenant_id 的取值 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 平台级数据标记，固定为 0 */
    private Long tenantId;

    /** 租户代码，用于登录与开放接口定位，创建后不可修改 */
    private String code;

    /** 租户名称 */
    private String name;

    /** 自定义登录域名，可为空 */
    private String domain;

    /** 套餐代码：FREE、STANDARD、ENTERPRISE */
    private String planCode;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 品牌 Logo 地址 */
    private String logoUrl;

    /** 品牌主色，十六进制颜色 */
    private String primaryColor;

    /** 默认语言 */
    private String locale;

    /** 默认时区 */
    private String timezone;

    /** 最大用户数，0 表示不限制 */
    private Integer maxUsers;

    /** 服务到期时间，空表示长期有效 */
    private LocalDateTime expiresAt;

    /** 创建人用户 ID */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人用户 ID */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标志：0-正常 1-已删除 */
    @TableLogic
    private Integer deleted;
}
