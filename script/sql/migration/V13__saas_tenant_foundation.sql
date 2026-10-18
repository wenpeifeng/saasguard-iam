-- ============================================================
-- V13__saas_tenant_foundation.sql
-- SaaS 租户底座：租户目录、品牌、套餐、容量与有效期。
-- iam_tenant 是平台级共享目录；其他身份、组织、应用、授权与审计表继续按 tenant_id 隔离。
-- ============================================================

CREATE TABLE iam_tenant (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键，也是租户隔离字段 tenant_id 的取值',
    tenant_id       BIGINT       NOT NULL DEFAULT 0 COMMENT '平台级数据标记，固定为 0',
    code            VARCHAR(32)  NOT NULL COMMENT '租户代码，用于登录与开放接口定位，创建后不可修改',
    name            VARCHAR(128) NOT NULL COMMENT '租户名称',
    domain          VARCHAR(255) NULL COMMENT '自定义登录域名',
    plan_code       VARCHAR(32)  NOT NULL DEFAULT 'STANDARD' COMMENT '套餐代码：FREE、STANDARD、ENTERPRISE',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-停用',
    logo_url        VARCHAR(512) NULL COMMENT '品牌 Logo 地址',
    primary_color   VARCHAR(16)  NOT NULL DEFAULT '#3157D5' COMMENT '品牌主色，十六进制颜色',
    locale          VARCHAR(16)  NOT NULL DEFAULT 'zh-CN' COMMENT '默认语言',
    timezone        VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '默认时区',
    max_users       INT          NOT NULL DEFAULT 0 COMMENT '最大用户数，0 表示不限制',
    expires_at      DATETIME     NULL COMMENT '服务到期时间，空表示长期有效',
    create_by       BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人用户 id',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       BIGINT       NULL COMMENT '更新人用户 id',
    update_time     DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志：0-正常 1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_tenant_code (code),
    UNIQUE KEY uk_iam_tenant_domain (domain),
    KEY idx_iam_tenant_status (status, expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'SaaS 租户目录与品牌、套餐、配额配置';

-- 保留 1000 作为开箱即用演示租户，兼容已有所有初始化数据。
INSERT INTO iam_tenant
    (id, tenant_id, code, name, plan_code, status, primary_color, locale, timezone, max_users)
VALUES
    (1000, 0, 'demo', 'SaaSGuard 演示组织', 'ENTERPRISE', 1, '#3157D5', 'zh-CN', 'Asia/Shanghai', 0);

-- 租户管理员只能管理本租户；平台超级管理员可跨租户切换数据视角。
INSERT INTO sys_role (tenant_id, code, name, remark)
SELECT 1000, 'TENANT_ADMIN', '租户管理员', '管理本租户的组织、用户、应用与授权'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'TENANT_ADMIN' AND deleted = 0);
