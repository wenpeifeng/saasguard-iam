package com.auth.app.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.auth.app.dto.ClientForm;
import com.auth.app.dto.ClientSecretVO;
import com.auth.app.dto.ClientVO;
import com.auth.app.entity.AppClient;
import com.auth.app.entity.AppClientRole;
import com.auth.app.mapper.AppClientMapper;
import com.auth.app.mapper.AppClientRoleMapper;
import com.auth.app.mapper.AppUserRoleMapper;
import com.auth.app.service.AppClientService;
import com.auth.common.exception.BusinessException;
import com.auth.common.result.PageResult;
import com.auth.framework.mybatis.PageQuery;
import com.auth.framework.mybatis.PageUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 应用注册服务实现。角色声明采用"整体替换"语义（以表单清单为准，差集增删）。
 */
@Service
@RequiredArgsConstructor
public class AppClientServiceImpl implements AppClientService {

    private final AppClientMapper clientMapper;
    private final AppClientRoleMapper roleMapper;
    private final AppUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<ClientVO> page(PageQuery query) {
        LambdaQueryWrapper<AppClient> wrapper = new LambdaQueryWrapper<AppClient>()
                .orderByDesc(AppClient::getId);
        return PageUtils.of(clientMapper, wrapper, query, AppClient.class,
                c -> ClientVO.from(c, listRoleEntities(c.getClientId())));
    }

    @Override
    public ClientVO get(String clientId) {
        return ClientVO.from(requireClient(clientId), listRoleEntities(clientId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClientSecretVO create(ClientForm form) {
        if (clientMapper.selectCount(new LambdaQueryWrapper<AppClient>()
                .eq(AppClient::getClientId, form.getClientId())) > 0) {
            throw new BusinessException("client_id 已存在：%s", form.getClientId());
        }
        String plainSecret = RandomUtil.randomString(32);
        AppClient client = new AppClient();
        client.setClientId(form.getClientId());
        client.setAppName(form.getAppName());
        client.setRedirectUri(form.getRedirectUri());
        client.setAccessTokenValidity(form.getAccessTokenValidity());
        client.setConsentRequired(Boolean.TRUE.equals(form.getConsentRequired()) ? 1 : 0);
        client.setStatus(AppClient.STATUS_ENABLED);
        client.setAdminUserId(form.getAdminUserId());
        client.setRemark(form.getRemark());
        client.setClientSecret(passwordEncoder.encode(plainSecret));
        clientMapper.insert(client);
        replaceRoles(form.getClientId(), form.getRoles());
        ClientSecretVO vo = new ClientSecretVO();
        vo.setClient(get(form.getClientId()));
        vo.setClientSecret(plainSecret);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String clientId, ClientForm form) {
        AppClient client = requireClient(clientId);
        // client_id 不可改：忽略表单中的 clientId 字段
        AppClient patch = new AppClient();
        patch.setId(client.getId());
        patch.setAppName(form.getAppName());
        patch.setRedirectUri(form.getRedirectUri());
        patch.setAccessTokenValidity(form.getAccessTokenValidity());
        patch.setConsentRequired(Boolean.TRUE.equals(form.getConsentRequired()) ? 1 : 0);
        patch.setAdminUserId(form.getAdminUserId());
        patch.setRemark(form.getRemark());
        clientMapper.updateById(patch);
        replaceRoles(clientId, form.getRoles());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(String clientId, int status) {
        if (status != AppClient.STATUS_ENABLED && status != AppClient.STATUS_DISABLED) {
            throw new BusinessException("非法目标状态：%s", status);
        }
        AppClient patch = new AppClient();
        patch.setId(requireClient(clientId).getId());
        patch.setStatus(status);
        clientMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClientSecretVO resetSecret(String clientId) {
        AppClient client = requireClient(clientId);
        String plainSecret = RandomUtil.randomString(32);
        AppClient patch = new AppClient();
        patch.setId(client.getId());
        patch.setClientSecret(passwordEncoder.encode(plainSecret));
        clientMapper.updateById(patch);
        ClientSecretVO vo = new ClientSecretVO();
        vo.setClient(get(clientId));
        vo.setClientSecret(plainSecret);
        return vo;
    }

    @Override
    public boolean verifySecret(String clientId, String secret) {
        AppClient client = loadEnabledClient(clientId);
        return client != null && secret != null && passwordEncoder.matches(secret, client.getClientSecret());
    }

    @Override
    public AppClient loadEnabledClient(String clientId) {
        AppClient client = clientMapper.selectOne(new LambdaQueryWrapper<AppClient>()
                .eq(AppClient::getClientId, clientId));
        if (client == null || client.getStatus() != AppClient.STATUS_ENABLED) {
            return null;
        }
        return client;
    }

    @Override
    public List<ClientVO.RoleVO> listRoles(String clientId) {
        return listRoleEntities(clientId).stream().map(r -> {
            ClientVO.RoleVO vo = new ClientVO.RoleVO();
            vo.setRoleCode(r.getRoleCode());
            vo.setRoleName(r.getRoleName());
            return vo;
        }).toList();
    }

    /** 应用必须存在 */
    private AppClient requireClient(String clientId) {
        AppClient client = clientMapper.selectOne(new LambdaQueryWrapper<AppClient>()
                .eq(AppClient::getClientId, clientId));
        if (client == null) {
            throw new BusinessException("应用不存在：%s", clientId);
        }
        return client;
    }

    /** 角色声明整体替换（差集增删；被删角色同步清理用户授权） */
    private void replaceRoles(String clientId, List<ClientForm.RoleItem> roles) {
        List<AppClientRole> existing = listRoleEntities(clientId);
        List<ClientForm.RoleItem> target = roles == null ? List.of() : roles;
        // 删除：库里有但表单没有（同步回收用户侧授权）
        for (AppClientRole role : existing) {
            if (target.stream().noneMatch(t -> t.getRoleCode().equals(role.getRoleCode()))) {
                roleMapper.deleteById(role.getId());
                userRoleMapper.delete(new LambdaQueryWrapper<com.auth.app.entity.AppUserRole>()
                        .eq(com.auth.app.entity.AppUserRole::getClientId, clientId)
                        .eq(com.auth.app.entity.AppUserRole::getRoleCode, role.getRoleCode()));
            }
        }
        // 插入：表单有但库里没有
        for (ClientForm.RoleItem item : target) {
            if (existing.stream().noneMatch(e -> e.getRoleCode().equals(item.getRoleCode()))) {
                AppClientRole role = new AppClientRole();
                role.setClientId(clientId);
                role.setRoleCode(item.getRoleCode());
                role.setRoleName(item.getRoleName());
                roleMapper.insert(role);
            } else {
                // 名称可能变更：原地更新
                AppClientRole old = existing.stream()
                        .filter(e -> e.getRoleCode().equals(item.getRoleCode())).findFirst().orElseThrow();
                AppClientRole patch = new AppClientRole();
                patch.setId(old.getId());
                patch.setRoleName(item.getRoleName());
                roleMapper.updateById(patch);
            }
        }
    }

    private List<AppClientRole> listRoleEntities(String clientId) {
        return roleMapper.selectList(new LambdaQueryWrapper<AppClientRole>()
                .eq(AppClientRole::getClientId, clientId).orderByAsc(AppClientRole::getId));
    }
}
