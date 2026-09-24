-- ============================================================
-- V30__token_audit_init.sql
-- 令牌与审计（auth-biz-oidc + 审计日志）：OIDC RS256 签名密钥 + 4A 第一层审计（登录/操作日志）。
--   1. token_key：OIDC RS256 密钥对（首次启动自动生成入库，JWKS 发布公钥）；
--   2. audit_login_log：登录日志（成功/失败、IP、UA、登录类型：管理端/OIDC）；
--   3. audit_op_log：管理端关键操作日志（写操作统一切面落库）。
-- 追加型审计表只插不改不删。
-- 版本段：token/audit 用 V30x。
-- ============================================================

-- OIDC 签名密钥（RS256 密钥对，PEM 文本；kid 与 JWKS 对应）
CREATE TABLE token_key (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    kid         VARCHAR(64)  NOT NULL COMMENT '密钥 ID（JWKS kid，与 JWT header.kid 对应）',
    algorithm   VARCHAR(16)  NOT NULL DEFAULT 'RS256' COMMENT '算法（固定 RS256）',
    public_key  TEXT         NOT NULL COMMENT '公钥 PEM（X.509 SubjectPublicKeyInfo）',
    private_key TEXT         NOT NULL COMMENT '私钥 PEM（PKCS#8，生产环境建议换 KMS）',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-签发中 0-已轮换（仅验签）',
    create_by   BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人（0=系统自动生成）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    update_by   BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_token_key_kid (kid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'OIDC 签名密钥（RS256）';

-- 登录日志（追加型：成功/失败、IP、UA、登录类型）
CREATE TABLE audit_login_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户 ID',
    user_id     BIGINT       NULL COMMENT '用户 ID（账号不存在时为空）',
    username    VARCHAR(64)  NOT NULL COMMENT '尝试登录的账号名（登录名或工号）',
    login_type  VARCHAR(16)  NOT NULL DEFAULT 'ADMIN' COMMENT '登录类型：ADMIN-管理端 OIDC-统一登录页',
    client_id   VARCHAR(64)  NULL COMMENT 'OIDC 登录对应的应用（管理端为空）',
    success     TINYINT      NOT NULL COMMENT '结果：1-成功 0-失败',
    fail_reason VARCHAR(255) NULL COMMENT '失败原因（密码错误/已离职/已停用/应用未分配等）',
    ip          VARCHAR(64)  NULL COMMENT '来源 IP',
    user_agent  VARCHAR(512) NULL COMMENT 'User-Agent',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    PRIMARY KEY (id),
    KEY idx_audit_login_user (user_id, create_time),
    KEY idx_audit_login_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录日志（追加型审计）';

-- 管理端关键操作日志（写操作统一切面落库；追加型）
CREATE TABLE audit_op_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT       NOT NULL DEFAULT 1000 COMMENT '租户 ID',
    user_id     BIGINT       NULL COMMENT '操作人 ID（认证中心管理端账号）',
    username    VARCHAR(64)  NULL COMMENT '操作人用户名',
    method      VARCHAR(8)   NOT NULL COMMENT 'HTTP 方法（POST/PUT/DELETE）',
    uri         VARCHAR(255) NOT NULL COMMENT '请求 URI（模块归属判定依据）',
    status_code INT          NOT NULL COMMENT '响应状态码（200 成功 / 4xx 5xx 失败）',
    detail      VARCHAR(512) NULL COMMENT '操作明细（请求体摘要，敏感字段脱敏）',
    ip          VARCHAR(64)  NULL COMMENT '来源 IP',
    cost_ms     BIGINT       NULL COMMENT '耗时（毫秒）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_audit_op_user (user_id, create_time),
    KEY idx_audit_op_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理端操作日志（追加型审计）';
