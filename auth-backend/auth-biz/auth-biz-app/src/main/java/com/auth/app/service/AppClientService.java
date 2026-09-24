package com.auth.app.service;

import com.auth.app.dto.ClientForm;
import com.auth.app.dto.ClientSecretVO;
import com.auth.app.dto.ClientVO;
import com.auth.common.result.PageResult;
import com.auth.framework.mybatis.PageQuery;

import java.util.List;

/**
 * 应用注册服务 —— "物业门禁"模型中的"门"。
 */
public interface AppClientService {

    /** 分页查询应用 */
    PageResult<ClientVO> page(PageQuery query);

    /** 应用详情（含角色声明） */
    ClientVO get(String clientId);

    /**
     * 注册应用（角色声明整体写入）。
     *
     * @return 应用信息 + 明文密钥（仅此一次返回）
     */
    ClientSecretVO create(ClientForm form);

    /** 修改应用（client_id 不可改；角色声明整体替换） */
    void update(String clientId, ClientForm form);

    /** 启用/停用应用（停用后该应用所有登录被拒） */
    void changeStatus(String clientId, int status);

    /**
     * 重置密钥。
     *
     * @return 新明文密钥（仅此一次返回）
     */
    ClientSecretVO resetSecret(String clientId);

    /** 校验客户端密钥（client_credentials / 授权码换令牌用） */
    boolean verifySecret(String clientId, String secret);

    /** 按 client_id 加载应用（不存在/已停用返回 null 由调用方处置） */
    com.auth.app.entity.AppClient loadEnabledClient(String clientId);

    /** 应用声明的角色标签清单 */
    List<ClientVO.RoleVO> listRoles(String clientId);
}
