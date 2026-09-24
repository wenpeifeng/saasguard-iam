package com.auth.idm.mapper;

import com.auth.idm.entity.IdmUserEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账号生命周期事件流水 Mapper。
 */
@Mapper
public interface IdmUserEventMapper extends BaseMapper<IdmUserEvent> {
}
