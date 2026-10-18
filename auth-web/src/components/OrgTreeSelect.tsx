import { useQuery } from '@tanstack/react-query'
import { TreeSelect } from 'antd'
import type { TreeSelectProps } from 'antd'
import { fetchOrgTree, type OrgTreeNode } from '@/api/idm'

type DefaultOptionType = NonNullable<TreeSelectProps['treeData']>[number]

/** 后端组织树 → TreeSelect 数据（searchText 供搜索：名称+编码） */
function toTreeData(nodes: OrgTreeNode[], disableDisabled: boolean, ancestorDisabled = false): DefaultOptionType[] {
  return nodes.map((n) => ({
    value: n.id,
    title: n.name,
    searchText: `${n.name} ${n.code}`,
    disabled: disableDisabled && (ancestorDisabled || n.status !== 1),
    children: n.children?.length ? toTreeData(n.children, disableDisabled, ancestorDisabled || n.status !== 1) : undefined,
  }))
}

/**
 * 组织树选择器 —— 全站统一（主账号筛选/建号/转岗等场景）。
 * 直接呈现层级结构（可搜索、带树连线），而不是平铺下拉。
 *
 * @param disableDisabled 禁选"已停用"组织（建号/转岗应禁选）
 */
export default function OrgTreeSelect({
  value,
  onChange,
  placeholder = '请选择组织',
  allowClear = false,
  disableDisabled = false,
  disabled = false,
  className,
}: {
  value?: number | null
  onChange?: (value: number | null) => void
  placeholder?: string
  allowClear?: boolean
  disableDisabled?: boolean
  disabled?: boolean
  className?: string
}) {
  const { data: tree } = useQuery({ queryKey: ['orgs', 'tree'], queryFn: fetchOrgTree })
  return (
    <TreeSelect
      disabled={disabled}
      showSearch
      treeNodeFilterProp="searchText"
      allowClear={allowClear}
      value={value ?? undefined}
      onChange={(v) => onChange?.(v ?? null)}
      placeholder={placeholder}
      treeData={toTreeData(tree ?? [], disableDisabled)}
      treeDefaultExpandAll
      treeLine
      className={['control-fill', className].filter(Boolean).join(' ')}
      popupMatchSelectWidth={false}
    />
  )
}
