package com.auth.idm.service;

import com.auth.framework.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 持久化业务编号；与建档共享事务，按租户和前缀加行锁，历史已删除编码也不复用。 */
@Service
@RequiredArgsConstructor
public class NumberService {
    private final JdbcTemplate jdbc;

    /** 生成 EMP 员工号或组织类型前缀编号，调用方必须已开启事务。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(String prefix) {
        long tenantId = TenantContextHolder.getTenantId();
        jdbc.update("INSERT IGNORE INTO idm_number_sequence(tenant_id, sequence_key) VALUES (?, ?)", tenantId, prefix);
        Long value = jdbc.queryForObject("SELECT current_value FROM idm_number_sequence WHERE tenant_id=? AND sequence_key=? FOR UPDATE",
                Long.class, tenantId, prefix);
        String code;
        do {
            code = prefix + "-" + String.format(java.util.Locale.ROOT, "%06d", ++value);
        } while (exists(tenantId, prefix, code));
        jdbc.update("UPDATE idm_number_sequence SET current_value=? WHERE tenant_id=? AND sequence_key=?", value, tenantId, prefix);
        return code;
    }

    private boolean exists(long tenantId, String prefix, String code) {
        if ("EMP".equals(prefix)) {
            return jdbc.queryForObject("SELECT COUNT(*) FROM idm_user WHERE tenant_id=? AND (employee_no=? OR username=?)",
                    Long.class, tenantId, code, code) > 0;
        }
        return jdbc.queryForObject("SELECT COUNT(*) FROM idm_org WHERE tenant_id=? AND code=?", Long.class, tenantId, code) > 0;
    }
}
