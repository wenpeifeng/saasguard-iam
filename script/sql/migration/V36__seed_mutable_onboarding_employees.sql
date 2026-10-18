-- ============================================================
-- V36: 可维护的入职员工演示账号。
--
-- 与只读系统种子账号分开：这些人员模拟正常“入职建号”，主档可由认证中心
-- 或已授权子系统维护；应用分配决定其是否出现在 ERP/MES 用户管理中。
-- 统一演示密码：123456（仅开发/演示环境）。
-- ============================================================

INSERT INTO idm_user (
    tenant_id, employee_no, username, real_name, org_id, position,
    email, phone, password, status, entry_date, account_source, immutable, create_by
)
SELECT 1000, seed.employee_no, seed.employee_no, seed.real_name, org.id, seed.position,
       seed.email, seed.phone,
       '$2y$10$D62d7woEXhpEJ1hdvxLk7uxdOw19ivCheW9pnXuVsTvcETkM9oRsm',
       1, DATE_SUB(CURRENT_DATE, INTERVAL seed.entry_days_ago DAY), 'MANUAL', 0, 1
FROM (
    SELECT 'E3101' AS employee_no, '周倩' AS real_name, 'DEMO-SUPPLY' AS org_code,
           '采购专员' AS position, 'zhou.qian@example.com' AS email, '13800003101' AS phone, 45 AS entry_days_ago
    UNION ALL
    SELECT 'E3102', '孙浩', 'DEMO-ASSY', '仓库班组长', 'sun.hao@example.com', '13800003102', 40
    UNION ALL
    SELECT 'E3103', '郑欣', 'DEMO-QUALITY', '质量工程师', 'zheng.xin@example.com', '13800003103', 35
    UNION ALL
    SELECT 'E3104', '胡杰', 'DEMO-FINANCE', '财务专员', 'hu.jie@example.com', '13800003104', 30
    UNION ALL
    SELECT 'E3105', '许晨', 'DEMO-ASSY', '车间操作员', 'xu.chen@example.com', '13800003105', 25
) seed
JOIN idm_org org
  ON org.tenant_id = 1000 AND org.code = seed.org_code AND org.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM idm_user u
    WHERE u.tenant_id = 1000 AND u.employee_no = seed.employee_no
);

INSERT INTO idm_user_event (tenant_id, user_id, event_type, detail, create_by, create_time)
SELECT 1000, u.id, 'CREATE',
       JSON_OBJECT('source', 'demo-onboarding', 'employeeNo', u.employee_no,
                   'realName', u.real_name, 'orgId', u.org_id, 'position', u.position),
       1, u.create_time
FROM idm_user u
WHERE u.tenant_id = 1000
  AND u.employee_no IN ('E3101', 'E3102', 'E3103', 'E3104', 'E3105')
  AND NOT EXISTS (
      SELECT 1 FROM idm_user_event e
      WHERE e.tenant_id = u.tenant_id AND e.user_id = u.id
        AND e.event_type = 'CREATE' AND e.deleted = 0
  );

-- ERP：采购、仓储、财务；MES：仓储、质量、车间。E3102 作为跨系统协同人员。
INSERT INTO app_user_assign (tenant_id, client_id, user_id, status, create_by)
SELECT 1000, mapping.client_id, u.id, 1, 1
FROM (
    SELECT 'E3101' AS employee_no, 'erp-web' AS client_id
    UNION ALL SELECT 'E3102', 'erp-web'
    UNION ALL SELECT 'E3104', 'erp-web'
    UNION ALL SELECT 'E3102', 'mes-web'
    UNION ALL SELECT 'E3103', 'mes-web'
    UNION ALL SELECT 'E3105', 'mes-web'
) mapping
JOIN idm_user u
  ON u.tenant_id = 1000 AND u.employee_no = mapping.employee_no AND u.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM app_user_assign a
    WHERE a.tenant_id = 1000 AND a.client_id = mapping.client_id
      AND a.user_id = u.id AND a.deleted = 0
);
