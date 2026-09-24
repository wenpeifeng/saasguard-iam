-- ============================================================
-- V1__system_init.sql
-- 平台层（system 模块）：认证中心自身的管理 RBAC 表。
-- 平台级角色（超级管理员/应用管理员/审计员）为平台概念，租户插件按共享表处理
-- （见 auth-framework TenantConfig.TENANT_SHARED_TABLES），tenant_id 列仅为统一模型保留。
-- 版本段：system 用 V1x。
-- ============================================================

-- 平台角色表
CREATE TABLE sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户 ID（预留，平台角色全租户共享）',
    code        VARCHAR(64)  NOT NULL COMMENT '角色编码（SUPER_ADMIN/APP_ADMIN/AUDITOR）',
    name        VARCHAR(64)  NOT NULL COMMENT '角色名称',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_by   BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-在用 1-已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (tenant_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '平台角色（认证中心自身 RBAC）';

-- 用户-角色关联表（user 指向 idm_user）
CREATE TABLE sys_user_role (
    id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT   NOT NULL DEFAULT 1000 COMMENT '租户 ID（预留）',
    user_id     BIGINT   NOT NULL COMMENT '用户 ID（idm_user.id）',
    role_id     BIGINT   NOT NULL COMMENT '角色 ID（sys_role.id）',
    create_by   BIGINT   NOT NULL DEFAULT 0 COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT   NULL COMMENT '更新人',
    update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-在用 1-已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role (user_id, role_id),
    KEY idx_sys_user_role_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户-平台角色关联';
