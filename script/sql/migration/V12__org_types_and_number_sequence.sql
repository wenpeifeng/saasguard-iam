-- 组织分类与持久化编号。历史编码不改写；未知历史节点标记 OTHER，待管理员确认分类。
ALTER TABLE idm_org ADD COLUMN org_type VARCHAR(24) NOT NULL DEFAULT 'OTHER'
    COMMENT 'GROUP/COMPANY/FACTORY/DEPARTMENT/WORKSHOP/TEAM/OTHER' AFTER name;
UPDATE idm_org SET org_type = 'GROUP' WHERE parent_id = 0;

CREATE TABLE idm_number_sequence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1000,
    sequence_key VARCHAR(32) NOT NULL,
    current_value BIGINT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idm_number_sequence (tenant_id, sequence_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织与员工编号流水；事务行锁保证并发唯一';
