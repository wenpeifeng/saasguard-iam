package com.auth.oidc.mapper;

import com.auth.oidc.entity.TokenKey;
import org.apache.ibatis.annotations.Mapper;

/** OIDC 签名密钥 Mapper */
@Mapper
public interface TokenKeyMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<TokenKey> {
}
