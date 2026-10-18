-- 演示账号口令统一为 123456，降低本地演示和验收成本。
-- BCrypt 值与 V31 种子超管相同；仅用于开发/演示环境，生产环境必须改密。
UPDATE idm_user
SET password = '$2y$10$D62d7woEXhpEJ1hdvxLk7uxdOw19ivCheW9pnXuVsTvcETkM9oRsm',
    update_by = 1,
    update_time = CURRENT_TIMESTAMP
WHERE tenant_id = 1000
  AND employee_no IN (
      'E2001', 'E2002', 'E2003', 'E2004', 'E2005', 'E2006', 'E2007', 'E2008',
      'E3001', 'E3002', 'E3003', 'E3004', 'E3005', 'E3006'
  )
  AND deleted = 0;
