-- ============================================================
-- V33__seed_onboarding_demo_accounts.sql
-- 可登录的入职演示账号：随 Flyway 自动初始化，确保新环境首次启动即可演示。
--
-- 统一口令：User@123456（BCrypt，仅开发/演示环境使用）
-- 正式链路：认证中心入职建号 -> 分配 ERP/MES -> OIDC 统一登录 -> 子系统 JIT 建档。
-- 说明：erp.admin / mes.admin 映射为对应子系统的本地 super_admin；
--       未持有 admin 标签的普通用户登录后，再由子系统管理员分配业务角色。
-- ============================================================

-- 1. 演示组织：集团总部 > 智造科技有限公司 > 华东智能工厂/职能部门。
INSERT INTO idm_org (tenant_id, code, name, org_type, parent_id, sort, status, create_by)
SELECT 1000, 'DEMO-COMPANY', '智造科技有限公司', 'COMPANY', root.id, 10, 1, 1
FROM idm_org root
WHERE root.tenant_id = 1000 AND root.code = 'GROUP' AND root.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM idm_org x
      WHERE x.tenant_id = 1000 AND x.code = 'DEMO-COMPANY'
  );

INSERT INTO idm_org (tenant_id, code, name, org_type, parent_id, sort, status, create_by)
SELECT 1000, 'DEMO-FACTORY', '华东智能工厂', 'FACTORY', company.id, 10, 1, 1
FROM idm_org company
WHERE company.tenant_id = 1000 AND company.code = 'DEMO-COMPANY' AND company.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM idm_org x
      WHERE x.tenant_id = 1000 AND x.code = 'DEMO-FACTORY'
  );

INSERT INTO idm_org (tenant_id, code, name, org_type, parent_id, sort, status, create_by)
SELECT 1000, child.code, child.name, child.org_type, parent.id, child.sort_no, 1, 1
FROM (
    SELECT 'DEMO-SUPPLY' AS code, '供应链中心' AS name, 'DEPARTMENT' AS org_type,
           'DEMO-COMPANY' AS parent_code, 20 AS sort_no
    UNION ALL
    SELECT 'DEMO-FINANCE', '财务中心', 'DEPARTMENT', 'DEMO-COMPANY', 30
    UNION ALL
    SELECT 'DEMO-ASSY', '总装车间', 'WORKSHOP', 'DEMO-FACTORY', 10
    UNION ALL
    SELECT 'DEMO-QUALITY', '质量管理部', 'DEPARTMENT', 'DEMO-FACTORY', 20
) child
JOIN idm_org parent
  ON parent.tenant_id = 1000 AND parent.code = child.parent_code AND parent.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM idm_org x
    WHERE x.tenant_id = 1000 AND x.code = child.code
);

-- 2. 入职主账号。用户名默认同工号，全部处于在职状态。
-- BCrypt 值对应明文 User@123456。
INSERT INTO idm_user (
    tenant_id, employee_no, username, real_name, org_id, position,
    email, password, status, entry_date, create_by
)
SELECT 1000, seed.employee_no, seed.employee_no, seed.real_name, org.id, seed.position,
       seed.email, '$2a$10$OvOpOieYWMS35BHU23vG1.8dqvSHoXk4a2FNE92Miox5n6VioWNBm',
       1, DATE_SUB(CURRENT_DATE, INTERVAL seed.entry_days_ago DAY), 1
FROM (
    SELECT 'E3001' AS employee_no, '张伟' AS real_name, 'DEMO-FACTORY' AS org_code,
           '生产计划主管' AS position, 'zhang.wei@example.com' AS email, 180 AS entry_days_ago
    UNION ALL
    SELECT 'E3002', '李娜', 'DEMO-FACTORY', 'MES 系统管理员', 'li.na@example.com', 160
    UNION ALL
    SELECT 'E3003', '王强', 'DEMO-FINANCE', 'ERP 系统管理员', 'wang.qiang@example.com', 140
    UNION ALL
    SELECT 'E3004', '赵敏', 'DEMO-SUPPLY', '仓储主管', 'zhao.min@example.com', 120
    UNION ALL
    SELECT 'E3005', '陈晨', 'DEMO-QUALITY', '质量工程师', 'chen.chen@example.com', 100
    UNION ALL
    SELECT 'E3006', '刘洋', 'DEMO-SUPPLY', '采购专员', 'liu.yang@example.com', 80
) seed
JOIN idm_org org
  ON org.tenant_id = 1000 AND org.code = seed.org_code AND org.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM idm_user x
    WHERE x.tenant_id = 1000 AND x.employee_no = seed.employee_no
);

-- 3. 补入真实“入职建号”生命周期事件，便于认证中心演示追溯。
INSERT INTO idm_user_event (tenant_id, user_id, event_type, detail, create_by, create_time)
SELECT 1000, u.id, 'CREATE',
       JSON_OBJECT(
           'source', 'flyway-demo-onboarding',
           'employeeNo', u.employee_no,
           'realName', u.real_name,
           'orgId', u.org_id,
           'position', u.position
       ),
       1, u.create_time
FROM idm_user u
WHERE u.tenant_id = 1000 AND u.employee_no IN ('E3001', 'E3002', 'E3003', 'E3004', 'E3005', 'E3006')
  AND NOT EXISTS (
      SELECT 1 FROM idm_user_event e
      WHERE e.tenant_id = u.tenant_id AND e.user_id = u.id
        AND e.event_type = 'CREATE' AND e.deleted = 0
  );

-- 4. 应用分配（进门权）。
-- E3001/E3004: ERP + MES；E3002/E3005: 仅 MES；E3003/E3006: 仅 ERP。
INSERT INTO app_user_assign (tenant_id, client_id, user_id, status, create_by)
SELECT 1000, mapping.client_id, u.id, 1, 1
FROM (
    SELECT 'E3001' AS employee_no, 'erp-web' AS client_id
    UNION ALL SELECT 'E3001', 'mes-web'
    UNION ALL SELECT 'E3002', 'mes-web'
    UNION ALL SELECT 'E3003', 'erp-web'
    UNION ALL SELECT 'E3004', 'erp-web'
    UNION ALL SELECT 'E3004', 'mes-web'
    UNION ALL SELECT 'E3005', 'mes-web'
    UNION ALL SELECT 'E3006', 'erp-web'
) mapping
JOIN idm_user u
  ON u.tenant_id = 1000 AND u.employee_no = mapping.employee_no AND u.deleted = 0
JOIN app_client app
  ON app.tenant_id = 1000 AND app.client_id = mapping.client_id AND app.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM app_user_assign a
    WHERE a.tenant_id = 1000 AND a.client_id = mapping.client_id AND a.user_id = u.id
);

-- 5. 子系统管理员标签。两个账号可分别进入 ERP/MES 内部管理中心。
INSERT INTO app_user_role (tenant_id, client_id, user_id, role_code, create_by)
SELECT 1000, mapping.client_id, u.id, mapping.role_code, 1
FROM (
    SELECT 'E3002' AS employee_no, 'mes-web' AS client_id, 'mes.admin' AS role_code
    UNION ALL
    SELECT 'E3003', 'erp-web', 'erp.admin'
) mapping
JOIN idm_user u
  ON u.tenant_id = 1000 AND u.employee_no = mapping.employee_no AND u.deleted = 0
JOIN app_client_role role_def
  ON role_def.tenant_id = 1000
 AND role_def.client_id = mapping.client_id
 AND role_def.role_code = mapping.role_code
 AND role_def.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM app_user_role r
    WHERE r.tenant_id = 1000 AND r.client_id = mapping.client_id
      AND r.user_id = u.id AND r.role_code = mapping.role_code
);
