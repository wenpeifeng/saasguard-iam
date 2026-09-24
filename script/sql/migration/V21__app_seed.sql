-- ============================================================
-- V21__app_seed.sql
-- 演示数据：两家业务应用 + demo client（覆盖 OIDC 接入演示场景）。
-- 角色标签先插声明，再建分配（admin 拥有全部应用进门权）。
-- ============================================================

-- 应用注册（密钥均为明文 demo-secret 的 BCrypt 值，仅演示用，生产必须换）
INSERT INTO app_client (client_id, client_secret, app_name, redirect_uri, access_token_validity, consent_required, status, admin_user_id, remark, create_by)
VALUES
('demo-client', '$2a$10$05pEE8kxvRQ8dgJ9bTiDh.KAejCRAvigfXWGM3wu2TFVpcU6PXbYW', '演示客户端', 'http://localhost:9090/callback', 7200, 1, 1, NULL, 'OIDC 授权码+PKCE 全流程演示（脚本 script/demo/demo-client-flow.sh，密钥 demo-secret）', 1),
('erp-web',    '$2a$10$05pEE8kxvRQ8dgJ9bTiDh.KAejCRAvigfXWGM3wu2TFVpcU6PXbYW', 'ERP 系统',  'http://localhost:8080/sso/callback', 7200, 0, 1, NULL, '集团 ERP（../sf-erp，密钥 demo-secret 演示）', 1),
('mes-web',    '$2a$10$05pEE8kxvRQ8dgJ9bTiDh.KAejCRAvigfXWGM3wu2TFVpcU6PXbYW', 'MES 系统',  'http://localhost:8081/sso/callback', 7200, 0, 1, NULL, '制造执行系统（../sf-mes，密钥 demo-secret 演示）', 1);

-- 应用角色标签（本期即 xxx.admin 系统管理员标签）
INSERT INTO app_client_role (client_id, role_code, role_name, create_by)
VALUES
('demo-client', 'demo.admin', '演示客户端管理员', 1),
('erp-web', 'erp.admin', 'ERP 系统管理员', 1),
('mes-web', 'mes.admin', 'MES 系统管理员', 1);

-- admin（idm_user.id=1）分配全部应用并持有管理员标签
INSERT INTO app_user_assign (client_id, user_id, status, create_by)
VALUES
('demo-client', 1, 1, 1),
('erp-web', 1, 1, 1),
('mes-web', 1, 1, 1);

INSERT INTO app_user_role (client_id, user_id, role_code, create_by)
VALUES
('demo-client', 1, 'demo.admin', 1),
('erp-web', 1, 'erp.admin', 1),
('mes-web', 1, 'mes.admin', 1);
