package com.auth.idm.service.impl;

import com.auth.common.exception.BusinessException;
import com.auth.idm.dto.OrgForm;
import com.auth.idm.dto.OrgTreeNode;
import com.auth.idm.entity.IdmOrg;
import com.auth.idm.entity.IdmUser;
import com.auth.idm.mapper.IdmOrgMapper;
import com.auth.idm.mapper.IdmUserMapper;
import com.auth.idm.service.OrgService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 组织服务实现：树查询按 parentId=0 起在内存组装（组织为小体量主数据，无需递归 SQL）。
 */
@Service
@RequiredArgsConstructor
public class OrgServiceImpl implements OrgService {

    private final IdmOrgMapper orgMapper;
    private final IdmUserMapper userMapper;

    @Override
    public List<OrgTreeNode> tree() {
        List<IdmOrg> all = orgMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, List<OrgTreeNode>> byParent = all.stream()
                .map(this::toNode)
                .collect(Collectors.groupingBy(OrgTreeNode::getParentId));
        List<OrgTreeNode> roots = byParent.getOrDefault(0L, List.of());
        sortRecursive(roots, byParent);
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OrgForm form) {
        requireUniqueCode(form.getCode(), null);
        requireParent(form.getParentId());
        IdmOrg org = new IdmOrg();
        org.setCode(form.getCode());
        org.setName(form.getName());
        org.setParentId(form.getParentId());
        org.setSort(form.getSort());
        org.setStatus(form.getStatus());
        orgMapper.insert(org);
        return org.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, OrgForm form) {
        requireExists(id);
        requireUniqueCode(form.getCode(), id);
        // 上级不能选自己（否则树成环）
        if (form.getParentId().equals(id)) {
            throw new BusinessException("上级组织不能选择自己");
        }
        requireParent(form.getParentId());
        IdmOrg org = new IdmOrg();
        org.setId(id);
        org.setCode(form.getCode());
        org.setName(form.getName());
        org.setParentId(form.getParentId());
        org.setSort(form.getSort());
        org.setStatus(form.getStatus());
        orgMapper.updateById(org);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireExists(id);
        if (orgMapper.selectCount(new LambdaQueryWrapper<IdmOrg>().eq(IdmOrg::getParentId, id)) > 0) {
            throw new BusinessException("组织下存在子组织，不能删除");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<IdmUser>().eq(IdmUser::getOrgId, id)) > 0) {
            throw new BusinessException("组织下仍有用户，不能删除");
        }
        orgMapper.deleteById(id);
    }

    /** 校验编码唯一（排除自身） */
    private void requireUniqueCode(String code, Long excludeId) {
        Long cnt = orgMapper.selectCount(new LambdaQueryWrapper<IdmOrg>()
                .eq(IdmOrg::getCode, code).ne(excludeId != null, IdmOrg::getId, excludeId));
        if (cnt > 0) {
            throw new BusinessException("组织编码已存在：%s", code);
        }
    }

    /** 校验组织存在且启用（根 0 免验） */
    private void requireParent(Long parentId) {
        if (parentId == 0L) {
            return;
        }
        IdmOrg parent = orgMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException("上级组织不存在");
        }
        if (parent.getStatus() == 0) {
            throw new BusinessException("上级组织已停用");
        }
    }

    private void requireExists(Long id) {
        if (orgMapper.selectById(id) == null) {
            throw new BusinessException("组织不存在");
        }
    }

    private OrgTreeNode toNode(IdmOrg org) {
        OrgTreeNode node = new OrgTreeNode();
        node.setId(org.getId());
        node.setCode(org.getCode());
        node.setName(org.getName());
        node.setParentId(org.getParentId());
        node.setSort(org.getSort());
        node.setStatus(org.getStatus());
        return node;
    }

    /**
     * 同级按 sort 升序，递归挂 children。
     * 空列表（getOrDefault 默认 List.of()，不可变）直接返回，不可对其排序。
     */
    private void sortRecursive(List<OrgTreeNode> nodes, Map<Long, List<OrgTreeNode>> byParent) {
        if (nodes.isEmpty()) {
            return;
        }
        nodes.sort(Comparator.comparing(OrgTreeNode::getSort, Comparator.nullsLast(Integer::compareTo)));
        for (OrgTreeNode node : nodes) {
            List<OrgTreeNode> children = byParent.getOrDefault(node.getId(), List.of());
            sortRecursive(children, byParent);
            node.setChildren(children);
        }
    }
}
