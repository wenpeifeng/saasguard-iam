-- ============================================================
-- V10__idm_init.sql
-- 身份主数据（idm 模块）：组织树 + 主账号（生命周期状态机）。
-- 设计要点：
--   1. 匹配/关联属性用不可变工号 employee_no（SCIM externalId 思路），不用邮箱；
--   2. 主账号状态机：1-在职 → 2-离职（终态，最强状态）/ 3-全局停用（可回到 1）；
--   3. 租户维度：表隔离（tenant_id 参与唯一键），本期恒为默认租户 1000。
-- 版本段：idm 用 V10x。
-- ============================================================

-- 组织表（树形，parent_id 自关联）
CREATE TABLE idm_org (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户 ID',
    code        VARCHAR(64)  NOT NULL COMMENT '组织编码',
    name        VARCHAR(128) NOT NULL COMMENT '组织名称',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '上级组织 ID（0=根节点）',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序号（同级内升序）',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-停用',
    create_by   BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-在用 1-已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_idm_org_code (tenant_id, code),
    KEY idx_idm_org_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '组织（树形主数据）';

-- 主账号表（认证中心统一身份，全集团唯一账号）
CREATE TABLE idm_user (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id    BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户 ID',
    employee_no  VARCHAR(64)  NOT NULL COMMENT '工号（不可变，跨系统匹配/关联的唯一键，SCIM externalId 思路）',
    username     VARCHAR(64)  NOT NULL COMMENT '登录名（默认同工号，可改）',
    real_name    VARCHAR(64)  NOT NULL COMMENT '姓名',
    org_id       BIGINT       NOT NULL COMMENT '组织 ID（idm_org.id，转岗即改此列并留痕）',
    position     VARCHAR(64)  NULL COMMENT '岗位',
    email        VARCHAR(128) NULL COMMENT '邮箱（仅联系用，不作匹配键）',
    phone        VARCHAR(32)  NULL COMMENT '手机号（仅联系用）',
    password     VARCHAR(128) NOT NULL COMMENT '登录口令（BCrypt）',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '全局状态：1-在职 2-离职（终态）3-全局停用',
    entry_date   DATE         NULL COMMENT '入职日期',
    leave_date   DATE         NULL COMMENT '离职日期（离职时写入）',
    create_by    BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by    BIGINT       NULL COMMENT '更新人',
    update_time  DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-在用 1-已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_idm_user_employee_no (tenant_id, employee_no),
    UNIQUE KEY uk_idm_user_username (tenant_id, username),
    KEY idx_idm_user_org (org_id),
    KEY idx_idm_user_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '主账号（生命周期全部收口在认证中心）';

-- 账号生命周期事件流水（转岗/离职/状态变更留痕，追加型表只插不改）
CREATE TABLE idm_user_event (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户 ID',
    user_id     BIGINT       NOT NULL COMMENT '用户 ID（idm_user.id）',
    event_type  VARCHAR(32)  NOT NULL COMMENT '事件类型：CREATE/TRANSFER/LEAVE/STATUS_CHANGE/RESET_PWD',
    detail      VARCHAR(512) NULL COMMENT '事件明细（JSON：如转岗前后组织）',
    create_by   BIGINT       NOT NULL DEFAULT 0 COMMENT '操作人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
    update_by   BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-在用 1-已删',
    PRIMARY KEY (id),
    KEY idx_idm_user_event_user (user_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '账号生命周期事件流水';
