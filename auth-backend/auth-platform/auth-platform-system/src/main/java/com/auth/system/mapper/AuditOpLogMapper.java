package com.auth.system.mapper;

import com.auth.system.entity.AuditOpLog;
import org.apache.ibatis.annotations.Mapper;

/** 操作日志 Mapper */
@Mapper
public interface AuditOpLogMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<AuditOpLog> {
}
