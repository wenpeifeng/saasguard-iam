package com.auth.idm.service.impl;

import com.auth.common.exception.BusinessException;
import com.auth.idm.dto.OrgForm;
import com.auth.idm.dto.OrgTreeNode;
import com.auth.idm.entity.IdmOrg;
import com.auth.idm.entity.IdmUser;
import com.auth.idm.mapper.IdmOrgMapper;
import com.auth.idm.mapper.IdmUserMapper;
import com.auth.idm.service.OrgService;
import com.auth.idm.service.NumberService;
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
    private final NumberService numberService;

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
        lockTree();
        requireParent(form, null);
        IdmOrg org = new IdmOrg();
        org.setCode(numberService.next(form.getOrgType().prefix()));
        org.setOrgType(form.getOrgType());
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
        lockTree();
        IdmOrg current = orgMapper.selectById(id);
        if (current == null) throw new BusinessException("组织不存在");
        if (form.getCode() != null && !form.getCode().equals(current.getCode())) {
            throw new BusinessException("组织编码创建后不可修改");
        }
        requireParent(form, id);
        List<IdmOrg> children = orgMapper.selectList(new LambdaQueryWrapper<IdmOrg>().eq(IdmOrg::getParentId, id));
        if (children.stream().anyMatch(child -> !form.getOrgType().accepts(child.getOrgType()))) {
            throw new BusinessException("该类型与现有下级组织不兼容，请先调整下级组织");
        }
        if (form.getStatus() == 0 && (children.stream().anyMatch(child -> child.getStatus() == 1)
                || userMapper.selectCount(new LambdaQueryWrapper<IdmUser>().eq(IdmUser::getOrgId, id)
                    .ne(IdmUser::getStatus, IdmUser.STATUS_LEFT)) > 0)) {
            throw new BusinessException("请先转移在职员工并停用下级组织，再停用本组织");
        }
        IdmOrg org = new IdmOrg();
        org.setId(id);
        org.setOrgType(form.getOrgType());
        org.setName(form.getName());
        org.setParentId(form.getParentId());
        org.setSort(form.getSort());
        org.setStatus(form.getStatus());
        orgMapper.updateById(org);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        lockTree();
        requireExists(id);
        if (orgMapper.selectCount(new LambdaQueryWrapper<IdmOrg>().eq(IdmOrg::getParentId, id)) > 0) {
            throw new BusinessException("组织下存在子组织，不能删除");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<IdmUser>().eq(IdmUser::getOrgId, id)) > 0) {
            throw new BusinessException("组织下仍有用户，不能删除");
        }
        orgMapper.deleteById(id);
    }

    /** 组织规模较小，结构修改串行校验，避免两个并发挂靠请求相互形成环。 */
    private void lockTree() {
        orgMapper.selectList(new LambdaQueryWrapper<IdmOrg>().last("FOR UPDATE"));
    }

    /** 校验组织存在且启用（根 0 免验） */
    private void requireParent(OrgForm form, Long selfId) {
        Long parentId = form.getParentId();
        if (parentId == 0L) {
            if (!form.getOrgType().isRootType()) throw new BusinessException("部门、车间和班组必须选择上级组织");
            return;
        }
        java.util.Set<Long> visited = new java.util.HashSet<>();
        boolean directParent = true;
        while (parentId != 0L) {
            if (parentId.equals(selfId) || !visited.add(parentId)) throw new BusinessException("上级组织不能选择自己或自己的下级组织");
            IdmOrg parent = orgMapper.selectById(parentId);
            if (parent == null) throw new BusinessException("上级组织不存在");
            if (parent.getStatus() == 0) throw new BusinessException("上级组织已停用");
            if (directParent && !parent.getOrgType().accepts(form.getOrgType())) {
                throw new BusinessException("组织类型与上级不兼容");
            }
            directParent = false;
            parentId = parent.getParentId();
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
        node.setOrgType(org.getOrgType());
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
