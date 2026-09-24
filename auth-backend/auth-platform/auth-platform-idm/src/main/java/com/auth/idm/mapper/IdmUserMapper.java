package com.auth.idm.mapper;

import com.auth.idm.entity.IdmUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 主账号 Mapper。
 */
@Mapper
public interface IdmUserMapper extends BaseMapper<IdmUser> {
}
