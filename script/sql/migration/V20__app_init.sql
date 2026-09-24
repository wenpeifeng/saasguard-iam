-- ============================================================
-- V20__app_init.sql
-- 应用模块（auth-biz-app）：应用注册 / 角色标签声明 / 用户×应用分配。
-- 设计要点（物业门禁模型）：
--   1. 认证中心只管"人（主账号生命周期）、门（应用分配）、店长（admin 标签）"；
--      各业务系统内部的角色→菜单/按钮/数据权限由业务系统自管；
--   2. 应用级禁用只影响"该用户×该应用"，不动全局状态；
--   3. 前置校验：仅在职员工可被分配/启停；离职用户一切应用级操作被拒绝。
-- 版本段：app 用 V20x。
-- ============================================================

-- 应用注册表（OAuth2 client / OIDC relying party）
CREATE TABLE app_client (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id             BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户 ID',
    client_id             VARCHAR(64)  NOT NULL COMMENT '客户端标识（对外协议层 ID，如 erp-web）',
    client_secret         VARCHAR(128) NOT NULL COMMENT '客户端密钥（BCrypt；client_credentials 换服务令牌用）',
    app_name              VARCHAR(64)  NOT NULL COMMENT '应用名称（如 ERP 系统）',
    redirect_uri          VARCHAR(512) NOT NULL COMMENT '回调地址（授权码投递点，OIDC /authorize 的 redirect_uri 必须精确匹配）',
    access_token_validity INT          NOT NULL DEFAULT 7200 COMMENT '访问令牌有效期（秒）',
    consent_required      TINYINT      NOT NULL DEFAULT 0 COMMENT '登录时是否展示授权确认页：1-需要 0-跳过',
    status                TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-停用（停用后该应用所有登录被拒）',
    admin_user_id         BIGINT       NULL COMMENT '应用管理员（idm_user.id，应用管理员委托视图用；空=仅平台超管可管）',
    remark                VARCHAR(255) NULL COMMENT '备注',
    create_by             BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人',
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by             BIGINT       NULL COMMENT '更新人',
    update_time           DATETIME     NULL COMMENT '更新时间',
    deleted               TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-在用 1-已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_client_client_id (tenant_id, client_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用注册（OAuth2 client）';

-- 应用声明的角色标签（本期即 xxx.admin 系统管理员标签；升级路径：应用注册时声明完整角色清单，中心分配普通角色）
CREATE TABLE app_client_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户 ID',
    client_id   VARCHAR(64)  NOT NULL COMMENT '所属应用（app_client.client_id）',
    role_code   VARCHAR(64)  NOT NULL COMMENT '角色标签（如 erp.admin，签发进该应用 access token 的 roles claim）',
    role_name   VARCHAR(64)  NOT NULL COMMENT '角色名称（如 ERP 系统管理员）',
    create_by   BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-在用 1-已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_client_role (tenant_id, client_id, role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用声明的角色标签';

-- 用户×应用分配（进门权 + 应用级启停）
CREATE TABLE app_user_assign (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户 ID',
    client_id   VARCHAR(64)  NOT NULL COMMENT '应用（app_client.client_id）',
    user_id     BIGINT       NOT NULL COMMENT '用户（idm_user.id）',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '应用级状态：1-启用 0-应用级禁用（业务系统管理员可调，仅影响本应用）',
    create_by   BIGINT       NOT NULL DEFAULT 0 COMMENT '分配人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
    update_by   BIGINT       NULL COMMENT '更新人（应用级启停操作人）',
    update_time DATETIME     NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-在用 1-已回收',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_assign (tenant_id, client_id, user_id),
    KEY idx_app_user_assign_user (user_id),
    KEY idx_app_user_assign_client (client_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户×应用分配（分配后才能登录该应用）';

-- 用户×应用×角色标签（本期即 xxx.admin 系统管理员标签；
-- 升级路径：应用在 app_client_role 声明完整角色清单后，此表即可承载普通角色分配，token roles claim 无需改动）
CREATE TABLE app_user_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户 ID',
    client_id   VARCHAR(64)  NOT NULL COMMENT '应用（app_client.client_id）',
    user_id     BIGINT       NOT NULL COMMENT '用户（idm_user.id）',
    role_code   VARCHAR(64)  NOT NULL COMMENT '角色标签（必须为应用已声明角色，如 erp.admin）',
    create_by   BIGINT       NOT NULL DEFAULT 0 COMMENT '分配人',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
    update_by   BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-在用 1-已回收',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_role (tenant_id, client_id, user_id, role_code),
    KEY idx_app_user_role_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户×应用×角色标签';
