-- ============================================================
-- V37__seed_saas_demo_tenant.sql
-- 第二租户演示数据：验证租户间的组织、人员、应用与授权均可独立初始化。
-- 所有演示账号密码统一为 123456；生产环境必须禁用或改密。
-- ============================================================

INSERT INTO iam_tenant
    (id, tenant_id, code, name, domain, plan_code, status, primary_color, locale, timezone, max_users)
VALUES
    (2000, 0, 'acme-demo', '艾克米智造（演示）', 'acme.localhost', 'STANDARD', 1,
     '#087F6A', 'zh-CN', 'Asia/Shanghai', 200);

INSERT INTO idm_org
    (id, tenant_id, code, name, org_type, parent_id, sort, status, create_by)
VALUES
    (200000, 2000, 'GROUP', '艾克米智造（演示）', 'GROUP', 0, 1, 1, 0);

INSERT INTO idm_user
    (id, tenant_id, employee_no, username, real_name, org_id, position, password, status,
     entry_date, account_source, immutable, create_by)
VALUES
    (200000, 2000, 'ACME0001', 'acme_admin', '陈晓（租户管理员）', 200000, '租户管理员',
     '$2y$10$D62d7woEXhpEJ1hdvxLk7uxdOw19ivCheW9pnXuVsTvcETkM9oRsm', 1,
     CURRENT_DATE, 'SEED', 1, 0);

INSERT INTO sys_user_role (tenant_id, user_id, role_id, create_by)
SELECT 2000, 200000, id, 0
FROM sys_role
WHERE code = 'TENANT_ADMIN' AND deleted = 0
LIMIT 1;

INSERT INTO idm_user_event (tenant_id, user_id, event_type, detail, create_by)
VALUES (2000, 200000, 'CREATE', '{"source":"V37_SAAS_DEMO_SEED"}', 0);

INSERT INTO app_client
    (id, tenant_id, client_id, client_secret, app_name, redirect_uri, access_token_validity,
     consent_required, status, admin_user_id, remark, create_by)
VALUES
    (200000, 2000, 'acme-portal',
     '$2a$10$05pEE8kxvRQ8dgJ9bTiDh.KAejCRAvigfXWGM3wu2TFVpcU6PXbYW',
     '艾克米业务门户', 'http://acme.localhost:3000/sso/callback', 7200,
     0, 1, 200000, '第二租户 OIDC 与隔离能力演示，客户端密钥 demo-secret', 0);

INSERT INTO app_client_role (tenant_id, client_id, role_code, role_name, create_by)
VALUES (2000, 'acme-portal', 'acme.admin', '艾克米门户管理员', 0);

INSERT INTO app_user_assign (tenant_id, client_id, user_id, status, create_by)
VALUES (2000, 'acme-portal', 200000, 1, 0);

INSERT INTO app_user_role (tenant_id, client_id, user_id, role_code, create_by)
VALUES (2000, 'acme-portal', 200000, 'acme.admin', 0);
