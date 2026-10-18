-- ============================================================
-- V35: 认证中心种子账号来源标识与只读保护。
--
-- 目标：
--   1) 所有可登录主账号统一在认证中心可见；
--   2) 系统/演示种子账号保留稳定剧本，不允许修改主档、转岗、离职、
--      全局启停或重置密码；
--   3) 应用分配和子系统业务角色仍是独立授权，不属于主档修改。
-- ============================================================

ALTER TABLE `idm_user`
    ADD COLUMN `account_source` VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
        COMMENT '账号来源:MANUAL人工建号/SEED系统演示种子'
        AFTER `leave_date`,
    ADD COLUMN `immutable` TINYINT NOT NULL DEFAULT 0
        COMMENT '主档只读:1是 0否'
        AFTER `account_source`;

-- 引导管理员与随项目初始化的入职演示账号均为稳定种子。
-- E200x 由可选演示脚本创建；存在时一并收口为只读，不存在不产生影响。
UPDATE `idm_user`
SET `account_source` = 'SEED', `immutable` = 1
WHERE `tenant_id` = 1000
  AND `employee_no` IN (
      'ADMIN000',
      'E2001', 'E2002', 'E2003', 'E2004', 'E2005', 'E2006', 'E2007', 'E2008',
      'E3001', 'E3002', 'E3003', 'E3004', 'E3005', 'E3006'
  )
  AND `deleted` = 0;
