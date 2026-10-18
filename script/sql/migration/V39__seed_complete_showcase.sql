-- ============================================================
-- V39__seed_complete_showcase.sql
-- 完整开源演示基线：让新克隆环境首次启动后，管理端每个核心列表都有可浏览数据。
--
-- 覆盖：租户、组织、主账号、生命周期、应用、角色标签、应用分配、登录日志、操作日志。
-- 所有姓名、组织、域名、联系方式均为虚构数据；演示账号统一口令 123456。
-- SEED 账号 immutable=1，防止体验过程中破坏公共演示剧本。
-- ============================================================

-- -----------------------------------------------------------------------------
-- 1. 租户目录：已有 demo / acme-demo，再补完整演示租户与停用租户。
-- -----------------------------------------------------------------------------
INSERT INTO iam_tenant
    (tenant_id, code, name, domain, plan_code, status, primary_color, locale, timezone, max_users, expires_at)
SELECT 0, 'northstar-demo', '北辰数智科技（演示）', 'northstar.localhost', 'ENTERPRISE', 1,
       '#6D4AFF', 'zh-CN', 'Asia/Shanghai', 500, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 3 YEAR)
WHERE NOT EXISTS (SELECT 1 FROM iam_tenant WHERE code = 'northstar-demo');

INSERT INTO iam_tenant
    (tenant_id, code, name, domain, plan_code, status, primary_color, locale, timezone, max_users, expires_at)
SELECT 0, 'legacy-demo', '远航供应链（已停用演示）', 'legacy.localhost', 'FREE', 0,
       '#64748B', 'zh-CN', 'Asia/Shanghai', 20, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)
WHERE NOT EXISTS (SELECT 1 FROM iam_tenant WHERE code = 'legacy-demo');

-- -----------------------------------------------------------------------------
-- 2. 组织树：补齐默认租户，并为 acme-demo / northstar-demo 建立完整层级。
-- -----------------------------------------------------------------------------
INSERT INTO idm_org (tenant_id, code, name, org_type, parent_id, sort, status, create_by)
SELECT 1000, seed.code, seed.name, seed.org_type, parent.id, seed.sort_no, 1, 1
FROM (
    SELECT 'DEMO-RD' AS code, '研发中心' AS name, 'DEPARTMENT' AS org_type, 'DEMO-COMPANY' AS parent_code, 40 AS sort_no
    UNION ALL SELECT 'DEMO-HR', '人力资源中心', 'DEPARTMENT', 'DEMO-COMPANY', 50
    UNION ALL SELECT 'DEMO-DIGITAL', '数字化中心', 'DEPARTMENT', 'DEMO-COMPANY', 60
    UNION ALL SELECT 'DEMO-WAREHOUSE', '仓储运营组', 'TEAM', 'DEMO-SUPPLY', 10
    UNION ALL SELECT 'DEMO-ASSY-A', '总装一班', 'TEAM', 'DEMO-ASSY', 10
    UNION ALL SELECT 'DEMO-ASSY-B', '总装二班', 'TEAM', 'DEMO-ASSY', 20
) seed
JOIN idm_org parent
  ON parent.tenant_id = 1000 AND parent.code = seed.parent_code AND parent.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM idm_org x WHERE x.tenant_id = 1000 AND x.code = seed.code
);

-- ACME：集团 > 公司 > 工厂 > 部门/车间。
INSERT INTO idm_org (tenant_id, code, name, org_type, parent_id, sort, status, create_by)
SELECT t.id, 'ACME-COMPANY', '艾克米精密制造有限公司', 'COMPANY', root.id, 10, 1, 0
FROM iam_tenant t
JOIN idm_org root ON root.tenant_id = t.id AND root.code = 'GROUP' AND root.deleted = 0
WHERE t.code = 'acme-demo'
  AND NOT EXISTS (SELECT 1 FROM idm_org x WHERE x.tenant_id = t.id AND x.code = 'ACME-COMPANY');

INSERT INTO idm_org (tenant_id, code, name, org_type, parent_id, sort, status, create_by)
SELECT t.id, 'ACME-FACTORY', '苏州智能工厂', 'FACTORY', company.id, 10, 1, 0
FROM iam_tenant t
JOIN idm_org company ON company.tenant_id = t.id AND company.code = 'ACME-COMPANY' AND company.deleted = 0
WHERE t.code = 'acme-demo'
  AND NOT EXISTS (SELECT 1 FROM idm_org x WHERE x.tenant_id = t.id AND x.code = 'ACME-FACTORY');

INSERT INTO idm_org (tenant_id, code, name, org_type, parent_id, sort, status, create_by)
SELECT t.id, seed.code, seed.name, seed.org_type, parent.id, seed.sort_no, 1, 0
FROM iam_tenant t
JOIN (
    SELECT 'ACME-SUPPLY' AS code, '供应链部' AS name, 'DEPARTMENT' AS org_type, 'ACME-COMPANY' AS parent_code, 20 AS sort_no
    UNION ALL SELECT 'ACME-FINANCE', '财务部', 'DEPARTMENT', 'ACME-COMPANY', 30
    UNION ALL SELECT 'ACME-ASSY', '精密装配车间', 'WORKSHOP', 'ACME-FACTORY', 10
    UNION ALL SELECT 'ACME-QUALITY', '质量保证部', 'DEPARTMENT', 'ACME-FACTORY', 20
) seed
JOIN idm_org parent ON parent.tenant_id = t.id AND parent.code = seed.parent_code AND parent.deleted = 0
WHERE t.code = 'acme-demo'
  AND NOT EXISTS (SELECT 1 FROM idm_org x WHERE x.tenant_id = t.id AND x.code = seed.code);

-- 北辰：完整租户根组织与业务层级。
INSERT INTO idm_org (tenant_id, code, name, org_type, parent_id, sort, status, create_by)
SELECT t.id, 'GROUP', '北辰数智科技集团', 'GROUP', 0, 1, 1, 0
FROM iam_tenant t
WHERE t.code = 'northstar-demo'
  AND NOT EXISTS (SELECT 1 FROM idm_org x WHERE x.tenant_id = t.id AND x.code = 'GROUP');

INSERT INTO idm_org (tenant_id, code, name, org_type, parent_id, sort, status, create_by)
SELECT t.id, 'NS-COMPANY', '北辰工业软件有限公司', 'COMPANY', root.id, 10, 1, 0
FROM iam_tenant t
JOIN idm_org root ON root.tenant_id = t.id AND root.code = 'GROUP' AND root.deleted = 0
WHERE t.code = 'northstar-demo'
  AND NOT EXISTS (SELECT 1 FROM idm_org x WHERE x.tenant_id = t.id AND x.code = 'NS-COMPANY');

INSERT INTO idm_org (tenant_id, code, name, org_type, parent_id, sort, status, create_by)
SELECT t.id, 'NS-FACTORY', '南京示范工厂', 'FACTORY', company.id, 10, 1, 0
FROM iam_tenant t
JOIN idm_org company ON company.tenant_id = t.id AND company.code = 'NS-COMPANY' AND company.deleted = 0
WHERE t.code = 'northstar-demo'
  AND NOT EXISTS (SELECT 1 FROM idm_org x WHERE x.tenant_id = t.id AND x.code = 'NS-FACTORY');

INSERT INTO idm_org (tenant_id, code, name, org_type, parent_id, sort, status, create_by)
SELECT t.id, seed.code, seed.name, seed.org_type, parent.id, seed.sort_no, 1, 0
FROM iam_tenant t
JOIN (
    SELECT 'NS-RD' AS code, '产品研发中心' AS name, 'DEPARTMENT' AS org_type, 'NS-COMPANY' AS parent_code, 20 AS sort_no
    UNION ALL SELECT 'NS-SUPPLY', '供应链中心', 'DEPARTMENT', 'NS-COMPANY', 30
    UNION ALL SELECT 'NS-ASSY', '柔性装配车间', 'WORKSHOP', 'NS-FACTORY', 10
    UNION ALL SELECT 'NS-QUALITY', '质量工程部', 'DEPARTMENT', 'NS-FACTORY', 20
) seed
JOIN idm_org parent ON parent.tenant_id = t.id AND parent.code = seed.parent_code AND parent.deleted = 0
WHERE t.code = 'northstar-demo'
  AND NOT EXISTS (SELECT 1 FROM idm_org x WHERE x.tenant_id = t.id AND x.code = seed.code);

-- -----------------------------------------------------------------------------
-- 3. 人员主档：覆盖在职、全局停用、离职，联系方式均使用保留示例域名。
-- -----------------------------------------------------------------------------
INSERT INTO idm_user (
    tenant_id, employee_no, username, real_name, org_id, position, email, phone, password,
    status, entry_date, leave_date, account_source, immutable, create_by
)
SELECT 1000, seed.employee_no, seed.employee_no, seed.real_name, org.id, seed.position,
       seed.email, seed.phone, '$2y$10$D62d7woEXhpEJ1hdvxLk7uxdOw19ivCheW9pnXuVsTvcETkM9oRsm',
       seed.status, DATE_SUB(CURRENT_DATE, INTERVAL seed.entry_days DAY),
       CASE WHEN seed.status = 2 THEN DATE_SUB(CURRENT_DATE, INTERVAL 15 DAY) ELSE NULL END,
       'SEED', 1, 1
FROM (
    SELECT 'E3201' AS employee_no, '林悦' AS real_name, 'DEMO-HR' AS org_code, '招聘经理' AS position,
           'lin.yue@example.com' AS email, '13900003201' AS phone, 1 AS status, 365 AS entry_days
    UNION ALL SELECT 'E3202', '郭宇', 'DEMO-WAREHOUSE', '库存控制专员', 'guo.yu@example.com', '13900003202', 1, 320
    UNION ALL SELECT 'E3203', '唐静', 'DEMO-RD', '产品经理', 'tang.jing@example.com', '13900003203', 1, 280
    UNION ALL SELECT 'E3204', '何俊', 'DEMO-DIGITAL', '集成架构师', 'he.jun@example.com', '13900003204', 1, 240
    UNION ALL SELECT 'E3205', '宋佳', 'DEMO-FINANCE', '成本会计', 'song.jia@example.com', '13900003205', 1, 210
    UNION ALL SELECT 'E3206', '邓超', 'DEMO-ASSY-A', '生产班组长', 'deng.chao@example.com', '13900003206', 1, 190
    UNION ALL SELECT 'E3207', '徐宁', 'DEMO-DIGITAL', '实施顾问', 'xu.ning@example.com', '13900003207', 3, 160
    UNION ALL SELECT 'E3208', '韩雪', 'DEMO-SUPPLY', '供应商开发专员', 'han.xue@example.com', '13900003208', 2, 420
) seed
JOIN idm_org org ON org.tenant_id = 1000 AND org.code = seed.org_code AND org.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM idm_user u WHERE u.tenant_id = 1000 AND u.employee_no = seed.employee_no
);

INSERT INTO idm_user (
    tenant_id, employee_no, username, real_name, org_id, position, email, phone, password,
    status, entry_date, leave_date, account_source, immutable, create_by
)
SELECT t.id, seed.employee_no, seed.username, seed.real_name, org.id, seed.position,
       seed.email, seed.phone, '$2y$10$D62d7woEXhpEJ1hdvxLk7uxdOw19ivCheW9pnXuVsTvcETkM9oRsm',
       seed.status, DATE_SUB(CURRENT_DATE, INTERVAL seed.entry_days DAY),
       CASE WHEN seed.status = 2 THEN DATE_SUB(CURRENT_DATE, INTERVAL 20 DAY) ELSE NULL END,
       'SEED', 1, 0
FROM iam_tenant t
JOIN (
    SELECT 'ACME1001' AS employee_no, 'acme_zhang' AS username, '张岚' AS real_name, 'ACME-FACTORY' AS org_code,
           '工厂经理' AS position, 'zhang.lan@acme.example.com' AS email, '13700001001' AS phone, 1 AS status, 500 AS entry_days
    UNION ALL SELECT 'ACME1002', 'acme_li', '李博', 'ACME-SUPPLY', '采购主管', 'li.bo@acme.example.com', '13700001002', 1, 430
    UNION ALL SELECT 'ACME1003', 'acme_wang', '王薇', 'ACME-QUALITY', '质量工程师', 'wang.wei@acme.example.com', '13700001003', 1, 360
    UNION ALL SELECT 'ACME1004', 'acme_zhao', '赵凯', 'ACME-ASSY', '生产计划员', 'zhao.kai@acme.example.com', '13700001004', 1, 300
    UNION ALL SELECT 'ACME1005', 'acme_chen', '陈璐', 'ACME-FINANCE', '财务主管', 'chen.lu@acme.example.com', '13700001005', 1, 260
    UNION ALL SELECT 'ACME1006', 'acme_sun', '孙磊', 'ACME-ASSY', '设备工程师', 'sun.lei@acme.example.com', '13700001006', 3, 220
    UNION ALL SELECT 'ACME1007', 'acme_zhou', '周颖', 'ACME-SUPPLY', '供应商质量专员', 'zhou.ying@acme.example.com', '13700001007', 2, 600
) seed
JOIN idm_org org ON org.tenant_id = t.id AND org.code = seed.org_code AND org.deleted = 0
WHERE t.code = 'acme-demo'
  AND NOT EXISTS (SELECT 1 FROM idm_user u WHERE u.tenant_id = t.id AND u.employee_no = seed.employee_no);

INSERT INTO idm_user (
    tenant_id, employee_no, username, real_name, org_id, position, email, phone, password,
    status, entry_date, account_source, immutable, create_by
)
SELECT t.id, seed.employee_no, seed.username, seed.real_name, org.id, seed.position,
       seed.email, seed.phone, '$2y$10$D62d7woEXhpEJ1hdvxLk7uxdOw19ivCheW9pnXuVsTvcETkM9oRsm',
       1, DATE_SUB(CURRENT_DATE, INTERVAL seed.entry_days DAY), 'SEED', 1, 0
FROM iam_tenant t
JOIN (
    SELECT 'NS0001' AS employee_no, 'northstar_admin' AS username, '顾言' AS real_name, 'GROUP' AS org_code,
           '租户管理员' AS position, 'gu.yan@northstar.example.com' AS email, '13600002001' AS phone, 720 AS entry_days
    UNION ALL SELECT 'NS1001', 'ns_lu', '陆明', 'NS-RD', 'IAM 产品经理', 'lu.ming@northstar.example.com', '13600002002', 520
    UNION ALL SELECT 'NS1002', 'ns_qin', '秦川', 'NS-RD', '后端架构师', 'qin.chuan@northstar.example.com', '13600002003', 460
    UNION ALL SELECT 'NS1003', 'ns_fang', '方婷', 'NS-SUPPLY', '供应链顾问', 'fang.ting@northstar.example.com', '13600002004', 390
    UNION ALL SELECT 'NS1004', 'ns_cao', '曹睿', 'NS-ASSY', '车间主管', 'cao.rui@northstar.example.com', '13600002005', 330
    UNION ALL SELECT 'NS1005', 'ns_shen', '沈菲', 'NS-QUALITY', '质量经理', 'shen.fei@northstar.example.com', '13600002006', 280
    UNION ALL SELECT 'NS1006', 'ns_yang', '杨帆', 'NS-FACTORY', '数字化工程师', 'yang.fan@northstar.example.com', '13600002007', 200
) seed
JOIN idm_org org ON org.tenant_id = t.id AND org.code = seed.org_code AND org.deleted = 0
WHERE t.code = 'northstar-demo'
  AND NOT EXISTS (SELECT 1 FROM idm_user u WHERE u.tenant_id = t.id AND u.employee_no = seed.employee_no);

-- 北辰首位租户管理员角色。
INSERT INTO sys_user_role (tenant_id, user_id, role_id, create_by)
SELECT t.id, u.id, role_def.id, 0
FROM iam_tenant t
JOIN idm_user u ON u.tenant_id = t.id AND u.username = 'northstar_admin' AND u.deleted = 0
JOIN sys_role role_def ON role_def.code = 'TENANT_ADMIN' AND role_def.deleted = 0
WHERE t.code = 'northstar-demo'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role r WHERE r.user_id = u.id AND r.role_id = role_def.id);

-- 所有新演示人员都有建档轨迹；停用、离职人员额外提供状态事件。
INSERT INTO idm_user_event (tenant_id, user_id, event_type, detail, create_by, create_time)
SELECT u.tenant_id, u.id, 'CREATE',
       JSON_OBJECT('source', 'V39_COMPLETE_SHOWCASE', 'employeeNo', u.employee_no,
                   'realName', u.real_name, 'position', u.position),
       0, u.create_time
FROM idm_user u
WHERE u.employee_no IN (
    'E3201','E3202','E3203','E3204','E3205','E3206','E3207','E3208',
    'ACME1001','ACME1002','ACME1003','ACME1004','ACME1005','ACME1006','ACME1007',
    'NS0001','NS1001','NS1002','NS1003','NS1004','NS1005','NS1006'
)
  AND NOT EXISTS (
      SELECT 1 FROM idm_user_event e
      WHERE e.tenant_id = u.tenant_id AND e.user_id = u.id AND e.event_type = 'CREATE' AND e.deleted = 0
  );

INSERT INTO idm_user_event (tenant_id, user_id, event_type, detail, create_by, create_time)
SELECT u.tenant_id, u.id,
       CASE WHEN u.status = 2 THEN 'LEAVE' ELSE 'STATUS_CHANGE' END,
       JSON_OBJECT('source', 'V39_COMPLETE_SHOWCASE', 'status', u.status,
                   'reason', CASE WHEN u.status = 2 THEN '演示：个人发展离职' ELSE '演示：安全复核暂时停用' END),
       0, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 10 DAY)
FROM idm_user u
WHERE u.employee_no IN ('E3207', 'E3208', 'ACME1006', 'ACME1007')
  AND NOT EXISTS (
      SELECT 1 FROM idm_user_event e
      WHERE e.tenant_id = u.tenant_id AND e.user_id = u.id
        AND e.event_type = CASE WHEN u.status = 2 THEN 'LEAVE' ELSE 'STATUS_CHANGE' END
        AND e.detail LIKE '%V39_COMPLETE_SHOWCASE%'
  );

-- -----------------------------------------------------------------------------
-- 4. 应用目录、角色与分配：每个可进入租户都有多应用、多人员、多状态。
-- -----------------------------------------------------------------------------
INSERT INTO app_client (
    tenant_id, client_id, client_secret, app_name, redirect_uri, access_token_validity,
    consent_required, status, admin_user_id, remark, create_by
)
SELECT t.id, seed.client_id,
       '$2a$10$05pEE8kxvRQ8dgJ9bTiDh.KAejCRAvigfXWGM3wu2TFVpcU6PXbYW',
       seed.app_name, seed.redirect_uri, seed.validity, seed.consent_required, seed.status,
       admin_user.id, seed.remark, admin_user.id
FROM iam_tenant t
JOIN (
    SELECT 'demo' AS tenant_code, 'wms-web' AS client_id, 'WMS 仓储管理系统' AS app_name,
           'http://localhost:5175/sso/callback' AS redirect_uri, 7200 AS validity, 0 AS consent_required, 1 AS status,
           'admin' AS admin_username, '仓储与库存业务演示，客户端密钥 demo-secret' AS remark
    UNION ALL SELECT 'demo', 'hr-portal', '人力资源门户', 'http://localhost:5176/sso/callback', 3600, 1, 1,
           'admin', '员工自助与人事业务演示，客户端密钥 demo-secret'
    UNION ALL SELECT 'demo', 'analytics-console', '经营分析平台', 'http://localhost:5177/sso/callback', 1800, 1, 0,
           'admin', '停用应用状态演示，客户端密钥 demo-secret'
    UNION ALL SELECT 'acme-demo', 'acme-mes', '艾克米 MES', 'http://acme.localhost:5173/sso/callback', 7200, 0, 1,
           'acme_admin', '生产执行应用演示，客户端密钥 demo-secret'
    UNION ALL SELECT 'acme-demo', 'acme-wms', '艾克米 WMS', 'http://acme.localhost:5175/sso/callback', 7200, 0, 1,
           'acme_admin', '仓储应用演示，客户端密钥 demo-secret'
    UNION ALL SELECT 'acme-demo', 'acme-hr', '艾克米员工门户', 'http://acme.localhost:5176/sso/callback', 3600, 1, 1,
           'acme_admin', '员工门户演示，客户端密钥 demo-secret'
    UNION ALL SELECT 'northstar-demo', 'northstar-portal', '北辰 SaaS 门户', 'http://northstar.localhost:3000/sso/callback', 7200, 0, 1,
           'northstar_admin', '北辰统一门户，客户端密钥 demo-secret'
    UNION ALL SELECT 'northstar-demo', 'northstar-devops', '北辰研发效能平台', 'http://northstar.localhost:3001/sso/callback', 3600, 1, 1,
           'northstar_admin', '研发工具集成演示，客户端密钥 demo-secret'
    UNION ALL SELECT 'northstar-demo', 'northstar-factory', '北辰数字工厂', 'http://northstar.localhost:3002/sso/callback', 7200, 0, 1,
           'northstar_admin', '工厂业务系统演示，客户端密钥 demo-secret'
) seed ON seed.tenant_code = t.code
JOIN idm_user admin_user
  ON admin_user.tenant_id = t.id AND admin_user.username = seed.admin_username AND admin_user.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM app_client app WHERE app.tenant_id = t.id AND app.client_id = seed.client_id
);

INSERT INTO app_client_role (tenant_id, client_id, role_code, role_name, create_by)
SELECT t.id, seed.client_id, seed.role_code, seed.role_name, admin_user.id
FROM iam_tenant t
JOIN (
    SELECT 'demo' AS tenant_code, 'wms-web' AS client_id, 'wms.admin' AS role_code, 'WMS 系统管理员' AS role_name, 'admin' AS admin_username
    UNION ALL SELECT 'demo', 'hr-portal', 'hr.admin', 'HR 门户管理员', 'admin'
    UNION ALL SELECT 'demo', 'analytics-console', 'analytics.admin', '分析平台管理员', 'admin'
    UNION ALL SELECT 'acme-demo', 'acme-mes', 'acme.mes.admin', 'MES 管理员', 'acme_admin'
    UNION ALL SELECT 'acme-demo', 'acme-wms', 'acme.wms.admin', 'WMS 管理员', 'acme_admin'
    UNION ALL SELECT 'acme-demo', 'acme-hr', 'acme.hr.admin', '员工门户管理员', 'acme_admin'
    UNION ALL SELECT 'northstar-demo', 'northstar-portal', 'northstar.portal.admin', '门户管理员', 'northstar_admin'
    UNION ALL SELECT 'northstar-demo', 'northstar-devops', 'northstar.devops.admin', '研发效能管理员', 'northstar_admin'
    UNION ALL SELECT 'northstar-demo', 'northstar-factory', 'northstar.factory.admin', '数字工厂管理员', 'northstar_admin'
) seed ON seed.tenant_code = t.code
JOIN idm_user admin_user
  ON admin_user.tenant_id = t.id AND admin_user.username = seed.admin_username AND admin_user.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM app_client_role r
    WHERE r.tenant_id = t.id AND r.client_id = seed.client_id AND r.role_code = seed.role_code
);

INSERT INTO app_user_assign (tenant_id, client_id, user_id, status, create_by)
SELECT t.id, seed.client_id, u.id, seed.status, admin_user.id
FROM iam_tenant t
JOIN (
    SELECT 'demo' AS tenant_code, 'wms-web' AS client_id, 'admin' AS username, 1 AS status, 'admin' AS admin_username
    UNION ALL SELECT 'demo', 'wms-web', 'E3004', 1, 'admin'
    UNION ALL SELECT 'demo', 'wms-web', 'E3102', 1, 'admin'
    UNION ALL SELECT 'demo', 'wms-web', 'E3202', 1, 'admin'
    UNION ALL SELECT 'demo', 'wms-web', 'E3206', 0, 'admin'
    UNION ALL SELECT 'demo', 'hr-portal', 'admin', 1, 'admin'
    UNION ALL SELECT 'demo', 'hr-portal', 'E3201', 1, 'admin'
    UNION ALL SELECT 'demo', 'hr-portal', 'E3203', 1, 'admin'
    UNION ALL SELECT 'demo', 'hr-portal', 'E3204', 1, 'admin'
    UNION ALL SELECT 'demo', 'analytics-console', 'admin', 1, 'admin'
    UNION ALL SELECT 'demo', 'analytics-console', 'E3003', 1, 'admin'
    UNION ALL SELECT 'demo', 'analytics-console', 'E3104', 1, 'admin'
    UNION ALL SELECT 'demo', 'analytics-console', 'E3205', 1, 'admin'
    UNION ALL SELECT 'acme-demo', 'acme-portal', 'acme_zhang', 1, 'acme_admin'
    UNION ALL SELECT 'acme-demo', 'acme-portal', 'acme_li', 1, 'acme_admin'
    UNION ALL SELECT 'acme-demo', 'acme-mes', 'acme_admin', 1, 'acme_admin'
    UNION ALL SELECT 'acme-demo', 'acme-mes', 'acme_zhang', 1, 'acme_admin'
    UNION ALL SELECT 'acme-demo', 'acme-mes', 'acme_wang', 1, 'acme_admin'
    UNION ALL SELECT 'acme-demo', 'acme-mes', 'acme_zhao', 1, 'acme_admin'
    UNION ALL SELECT 'acme-demo', 'acme-wms', 'acme_admin', 1, 'acme_admin'
    UNION ALL SELECT 'acme-demo', 'acme-wms', 'acme_li', 1, 'acme_admin'
    UNION ALL SELECT 'acme-demo', 'acme-wms', 'acme_zhao', 1, 'acme_admin'
    UNION ALL SELECT 'acme-demo', 'acme-hr', 'acme_admin', 1, 'acme_admin'
    UNION ALL SELECT 'acme-demo', 'acme-hr', 'acme_chen', 1, 'acme_admin'
    UNION ALL SELECT 'northstar-demo', 'northstar-portal', 'northstar_admin', 1, 'northstar_admin'
    UNION ALL SELECT 'northstar-demo', 'northstar-portal', 'ns_lu', 1, 'northstar_admin'
    UNION ALL SELECT 'northstar-demo', 'northstar-portal', 'ns_fang', 1, 'northstar_admin'
    UNION ALL SELECT 'northstar-demo', 'northstar-devops', 'northstar_admin', 1, 'northstar_admin'
    UNION ALL SELECT 'northstar-demo', 'northstar-devops', 'ns_lu', 1, 'northstar_admin'
    UNION ALL SELECT 'northstar-demo', 'northstar-devops', 'ns_qin', 1, 'northstar_admin'
    UNION ALL SELECT 'northstar-demo', 'northstar-factory', 'northstar_admin', 1, 'northstar_admin'
    UNION ALL SELECT 'northstar-demo', 'northstar-factory', 'ns_cao', 1, 'northstar_admin'
    UNION ALL SELECT 'northstar-demo', 'northstar-factory', 'ns_shen', 1, 'northstar_admin'
    UNION ALL SELECT 'northstar-demo', 'northstar-factory', 'ns_yang', 1, 'northstar_admin'
) seed ON seed.tenant_code = t.code
JOIN idm_user u ON u.tenant_id = t.id AND u.username = seed.username AND u.deleted = 0
JOIN idm_user admin_user ON admin_user.tenant_id = t.id AND admin_user.username = seed.admin_username AND admin_user.deleted = 0
JOIN app_client app ON app.tenant_id = t.id AND app.client_id = seed.client_id AND app.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM app_user_assign a
    WHERE a.tenant_id = t.id AND a.client_id = seed.client_id AND a.user_id = u.id
);

INSERT INTO app_user_role (tenant_id, client_id, user_id, role_code, create_by)
SELECT t.id, seed.client_id, u.id, seed.role_code, admin_user.id
FROM iam_tenant t
JOIN (
    SELECT 'demo' AS tenant_code, 'wms-web' AS client_id, 'admin' AS username, 'wms.admin' AS role_code, 'admin' AS admin_username
    UNION ALL SELECT 'demo', 'hr-portal', 'admin', 'hr.admin', 'admin'
    UNION ALL SELECT 'demo', 'analytics-console', 'admin', 'analytics.admin', 'admin'
    UNION ALL SELECT 'acme-demo', 'acme-mes', 'acme_admin', 'acme.mes.admin', 'acme_admin'
    UNION ALL SELECT 'acme-demo', 'acme-wms', 'acme_admin', 'acme.wms.admin', 'acme_admin'
    UNION ALL SELECT 'acme-demo', 'acme-hr', 'acme_admin', 'acme.hr.admin', 'acme_admin'
    UNION ALL SELECT 'northstar-demo', 'northstar-portal', 'northstar_admin', 'northstar.portal.admin', 'northstar_admin'
    UNION ALL SELECT 'northstar-demo', 'northstar-devops', 'northstar_admin', 'northstar.devops.admin', 'northstar_admin'
    UNION ALL SELECT 'northstar-demo', 'northstar-factory', 'northstar_admin', 'northstar.factory.admin', 'northstar_admin'
) seed ON seed.tenant_code = t.code
JOIN idm_user u ON u.tenant_id = t.id AND u.username = seed.username AND u.deleted = 0
JOIN idm_user admin_user ON admin_user.tenant_id = t.id AND admin_user.username = seed.admin_username AND admin_user.deleted = 0
JOIN app_client_role role_def
  ON role_def.tenant_id = t.id AND role_def.client_id = seed.client_id
 AND role_def.role_code = seed.role_code AND role_def.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM app_user_role r
    WHERE r.tenant_id = t.id AND r.client_id = seed.client_id
      AND r.user_id = u.id AND r.role_code = seed.role_code
);

-- -----------------------------------------------------------------------------
-- 5. 登录日志：成功/失败、管理端/OIDC、多应用、多租户。
-- -----------------------------------------------------------------------------
INSERT INTO audit_login_log
    (tenant_id, user_id, username, login_type, client_id, success, fail_reason, ip, user_agent, create_time)
SELECT t.id, u.id, seed.username, seed.login_type, seed.client_id, seed.success, seed.fail_reason,
       seed.ip, 'SaaSGuard-Showcase/1.0', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL seed.minutes_ago MINUTE)
FROM iam_tenant t
JOIN (
    SELECT 'demo' AS tenant_code, 'admin' AS username, 'ADMIN' AS login_type, NULL AS client_id, 1 AS success,
           NULL AS fail_reason, '127.0.0.1' AS ip, 12 AS minutes_ago
    UNION ALL SELECT 'demo', 'E3002', 'OIDC', 'mes-web', 1, NULL, '192.0.2.12', 28
    UNION ALL SELECT 'demo', 'E3003', 'OIDC', 'erp-web', 1, NULL, '192.0.2.18', 46
    UNION ALL SELECT 'demo', 'E3202', 'OIDC', 'wms-web', 1, NULL, '192.0.2.24', 70
    UNION ALL SELECT 'demo', 'E3207', 'ADMIN', NULL, 0, '账号已全局停用（演示）', '192.0.2.31', 95
    UNION ALL SELECT 'demo', 'unknown_demo', 'ADMIN', NULL, 0, '账号或密码错误（演示）', '192.0.2.45', 130
    UNION ALL SELECT 'acme-demo', 'acme_admin', 'ADMIN', NULL, 1, NULL, '198.51.100.10', 18
    UNION ALL SELECT 'acme-demo', 'acme_zhang', 'OIDC', 'acme-mes', 1, NULL, '198.51.100.21', 42
    UNION ALL SELECT 'acme-demo', 'acme_li', 'OIDC', 'acme-wms', 1, NULL, '198.51.100.22', 68
    UNION ALL SELECT 'acme-demo', 'acme_sun', 'OIDC', 'acme-mes', 0, '账号已全局停用（演示）', '198.51.100.23', 110
    UNION ALL SELECT 'northstar-demo', 'northstar_admin', 'ADMIN', NULL, 1, NULL, '203.0.113.10', 20
    UNION ALL SELECT 'northstar-demo', 'ns_lu', 'OIDC', 'northstar-portal', 1, NULL, '203.0.113.21', 50
    UNION ALL SELECT 'northstar-demo', 'ns_qin', 'OIDC', 'northstar-devops', 1, NULL, '203.0.113.22', 75
    UNION ALL SELECT 'northstar-demo', 'ns_cao', 'OIDC', 'northstar-factory', 1, NULL, '203.0.113.23', 100
) seed ON seed.tenant_code = t.code
LEFT JOIN idm_user u ON u.tenant_id = t.id AND u.username = seed.username AND u.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM audit_login_log l
    WHERE l.tenant_id = t.id AND l.user_agent = 'SaaSGuard-Showcase/1.0'
      AND l.username = seed.username AND COALESCE(l.client_id, '') = COALESCE(seed.client_id, '')
);

-- -----------------------------------------------------------------------------
-- 6. 操作日志：覆盖租户、人员、组织、应用与授权操作，敏感字段保持脱敏。
-- -----------------------------------------------------------------------------
INSERT INTO audit_op_log
    (tenant_id, user_id, username, method, uri, status_code, detail, ip, cost_ms, create_time)
SELECT t.id, u.id, seed.username, seed.method, seed.uri, seed.status_code, seed.detail,
       seed.ip, seed.cost_ms, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL seed.minutes_ago MINUTE)
FROM iam_tenant t
JOIN (
    SELECT 'demo' AS tenant_code, 'admin' AS username, 'POST' AS method, '/api/idm/users' AS uri, 200 AS status_code,
           '{"demo":true,"action":"入职建号","employeeNo":"E3206","password":"***"}' AS detail,
           '127.0.0.1' AS ip, 86 AS cost_ms, 180 AS minutes_ago
    UNION ALL SELECT 'demo', 'admin', 'PUT', '/api/idm/users/3207/status/3', 200,
           '{"demo":true,"action":"全局停用","reason":"安全复核"}', '127.0.0.1', 32, 160
    UNION ALL SELECT 'demo', 'admin', 'POST', '/api/apps/wms-web/assignments', 200,
           '{"demo":true,"action":"分配WMS","userCount":4}', '127.0.0.1', 45, 140
    UNION ALL SELECT 'demo', 'admin', 'PUT', '/api/apps/analytics-console/status/0', 200,
           '{"demo":true,"action":"停用应用"}', '127.0.0.1', 29, 120
    UNION ALL SELECT 'demo', 'admin', 'PUT', '/api/idm/orgs/999999', 400,
           '{"demo":true,"action":"修改组织","result":"层级校验失败"}', '127.0.0.1', 18, 105
    UNION ALL SELECT 'acme-demo', 'acme_admin', 'POST', '/api/idm/users', 200,
           '{"demo":true,"action":"入职建号","employeeNo":"ACME1004","password":"***"}', '198.51.100.10', 74, 170
    UNION ALL SELECT 'acme-demo', 'acme_admin', 'POST', '/api/apps/acme-mes/assignments', 200,
           '{"demo":true,"action":"分配MES","userCount":3}', '198.51.100.10', 40, 145
    UNION ALL SELECT 'acme-demo', 'acme_admin', 'PUT', '/api/idm/users/1006/status/3', 200,
           '{"demo":true,"action":"全局停用","reason":"长期休假"}', '198.51.100.10', 25, 115
    UNION ALL SELECT 'northstar-demo', 'northstar_admin', 'POST', '/api/apps/northstar-devops/assignments', 200,
           '{"demo":true,"action":"分配研发效能平台","userCount":2}', '203.0.113.10', 37, 150
    UNION ALL SELECT 'northstar-demo', 'northstar_admin', 'PUT', '/api/idm/users/1002', 200,
           '{"demo":true,"action":"更新员工档案","phone":"***"}', '203.0.113.10', 31, 125
) seed ON seed.tenant_code = t.code
LEFT JOIN idm_user u ON u.tenant_id = t.id AND u.username = seed.username AND u.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM audit_op_log l
    WHERE l.tenant_id = t.id AND l.uri = seed.uri AND l.detail = seed.detail
);
