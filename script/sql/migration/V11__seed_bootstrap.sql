-- ============================================================
-- V11__seed_bootstrap.sql
-- 种子数据：根组织 + 平台角色 + 引导超管（仅首次部署引导用，登录后应立即改密）。
-- 超管初始口令：Admin@123456（BCrypt $2y$，Spring Security 兼容）。
-- ============================================================

INSERT INTO idm_org (tenant_id, code, name, parent_id, sort, status)
VALUES (1000, 'GROUP', '集团总部', 0, 1, 1);

INSERT INTO sys_role (tenant_id, code, name, remark) VALUES
(1000, 'SUPER_ADMIN', '超级管理员', '认证中心平台最高权限，仅首次部署引导用'),
(1000, 'APP_ADMIN',   '应用管理员', '只能查看/操作自己应用的分配与用户列表'),
(1000, 'AUDITOR',     '审计员',     '只读审计日志');

INSERT INTO idm_user (tenant_id, employee_no, username, real_name, org_id, position, password, status, entry_date)
VALUES (1000, 'ADMIN000', 'admin', '超级管理员', 1, '平台管理员',
        '$2y$10$3pfoJnmGYE55JX1HhLoRquV/77vZcUgwFtOmddETbacbA0eaObqMC', 1, CURRENT_DATE);

INSERT INTO sys_user_role (tenant_id, user_id, role_id)
VALUES (1000, 1, 1);
