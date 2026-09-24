import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, Empty, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Tag, Tree, Typography } from 'antd'
import { ApartmentOutlined, DeleteOutlined, EditOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import StatusTag from '@/components/StatusTag'
import { useSubmitting } from '@/hooks/useSubmitting'
import { fetchOrgTree, createOrg, updateOrg, deleteOrg, type OrgTreeNode, type OrgForm } from '@/api/idm'

const { Text } = Typography

/** 树节点搜索：命中名称/编码的节点及其祖先全部保留 */
function filterTree(nodes: OrgTreeNode[], keyword: string): OrgTreeNode[] {
  const lower = keyword.toLowerCase()
  return nodes.flatMap((n) => {
    const children = filterTree(n.children ?? [], keyword)
    const hit = n.name.toLowerCase().includes(lower) || n.code.toLowerCase().includes(lower)
    if (hit || children.length) {
      return [{ ...n, children }]
    }
    return []
  })
}

/** 统计节点总数（选中卡展示） */
function countNodes(nodes: OrgTreeNode[]): number {
  return nodes.reduce((acc, n) => acc + 1 + countNodes(n.children ?? []), 0)
}

/**
 * 组织架构页 —— 左树右卡布局：
 * 左侧组织树（可搜索、树连线、状态徽标、停用节点置灰），右侧选中节点详情卡 + 操作。
 * 新建/修改弹窗里上级组织也用树形选择。
 */
export default function OrgTree() {
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [selected, setSelected] = useState<number | null>(null)
  const [keyword, setKeyword] = useState('')
  const queryClient = useQueryClient()
  const [form] = Form.useForm<OrgForm>()

  const { data: tree = [] } = useQuery({ queryKey: ['orgs', 'tree'], queryFn: fetchOrgTree })
  const flat = useMemo(() => {
    const map: Record<number, { node: OrgTreeNode; parentId: number | null }> = {}
    const walk = (nodes: OrgTreeNode[], parentId: number | null) => {
      for (const n of nodes) {
        map[n.id] = { node: n, parentId }
        walk(n.children ?? [], n.id)
      }
    }
    walk(tree, null)
    return map
  }, [tree])

  const shown = keyword ? filterTree(tree, keyword) : tree
  const selectedEntry = selected != null ? flat[selected] : null

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['orgs', 'tree'] })

  const submitSave = useSubmitting(
    async (values: OrgForm) => {
      if (editingId) {
        await updateOrg(editingId, values)
      } else {
        await createOrg(values)
      }
    },
    { successText: '保存成功', onSuccess: () => { setModalOpen(false); invalidate() } },
  )

  const doDelete = useSubmitting(
    async (id: number) => {
      await deleteOrg(id)
    },
    { successText: '已删除', onSuccess: () => { setSelected(null); invalidate() } },
  )

  const openCreate = (parentId: number | null) => {
    setEditingId(null)
    form.resetFields()
    form.setFieldsValue({ parentId, sort: 0, status: 1 })
    setModalOpen(true)
  }

  const openEdit = (id: number) => {
    const entry = flat[id]
    if (!entry) return
    setEditingId(id)
    form.setFieldsValue({
      code: entry.node.code,
      name: entry.node.name,
      parentId: entry.parentId,
      sort: entry.node.sort,
      status: entry.node.status,
    })
    setModalOpen(true)
  }

  /** 树数据：标题 = 名称 + 编码 + 状态徽标；停用节点置灰 */
  const treeData = (nodes: OrgTreeNode[]): any[] =>
    nodes.map((n) => ({
      key: n.id,
      title: (
        <span style={{ opacity: n.status === 1 ? 1 : 0.45 }}>
          {n.name}
          <span style={{ color: '#94a3b8', fontSize: 12, marginLeft: 8 }}>{n.code}</span>
          {n.status !== 1 && (
            <Tag style={{ marginLeft: 8, transform: 'scale(0.85)' }}>停用</Tag>
          )}
        </span>
      ),
      children: n.children?.length ? treeData(n.children) : undefined,
    }))

  /** 上级组织选项（排除自身及后代，防成环；后端另有兜底校验） */
  const parentOptions = useMemo(() => {
    const excluded = new Set<number>()
    if (editingId != null) {
      excluded.add(editingId)
      const collectChildren = (nodes: OrgTreeNode[]) => {
        for (const n of nodes) {
          if (n.id === editingId) {
            const collect = (cs: OrgTreeNode[]) =>
              cs.forEach((c) => { excluded.add(c.id); collect(c.children ?? []) })
            collect(n.children ?? [])
          } else {
            collectChildren(n.children ?? [])
          }
        }
      }
      collectChildren(tree)
    }
    const walk = (nodes: OrgTreeNode[], prefix: string): { value: number; label: string }[] =>
      nodes.filter((n) => !excluded.has(n.id)).flatMap((n) => {
        const label = prefix + n.name
        return [{ value: n.id, label }, ...walk(n.children ?? [], `${label} / `)]
      })
    return walk(tree, '')
  }, [tree, editingId])

  return (
    <div>
      <PageHeader
        title="组织架构"
        subtitle={`集团组织树（主账号归属维度）· 共 ${countNodes(tree)} 个节点`}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate(null)}>
            新建根组织
          </Button>
        }
      />

      <div style={{ display: 'flex', gap: 14, alignItems: 'stretch' }}>
        {/* 左：组织树卡片 */}
        <Card style={{ flex: 1, borderRadius: 14 }} styles={{ body: { paddingTop: 14 } }}>
          <Input
            allowClear
            prefix={<SearchOutlined style={{ color: '#94a3b8' }} />}
            placeholder="搜索组织名称 / 编码"
            style={{ marginBottom: 14 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          {shown.length === 0 ? (
            <Empty description={keyword ? '无匹配组织' : '暂无组织'} style={{ padding: '48px 0' }} />
          ) : (
            <Tree
              blockNode
              showLine={{ showLeafIcon: false }}
              defaultExpandAll
              treeData={treeData(shown)}
              selectedKeys={selected != null ? [selected] : []}
              onSelect={(keys) => setSelected((keys[0] as number) ?? null)}
              style={{ fontSize: 14 }}
            />
          )}
        </Card>

        {/* 右：选中节点详情卡 */}
        <Card style={{ width: 340, borderRadius: 14 }} styles={{ body: { paddingTop: 18 } }}>
          {selectedEntry ? (
            <Space direction="vertical" size={14} style={{ width: '100%' }}>
              <Space>
                <ApartmentOutlined style={{ fontSize: 22, color: '#2563eb' }} />
                <span style={{ fontSize: 18, fontWeight: 650 }}>{selectedEntry.node.name}</span>
                <StatusTag value={selectedEntry.node.status} />
              </Space>
              <div style={{ display: 'grid', gridTemplateColumns: '64px 1fr', rowGap: 8, fontSize: 13 }}>
                <Text type="secondary">编码</Text>
                <Text>{selectedEntry.node.code}</Text>
                <Text type="secondary">上级组织</Text>
                <Text>{selectedEntry.parentId != null ? flat[selectedEntry.parentId]?.node.name ?? '-' : '（根节点）'}</Text>
                <Text type="secondary">排序号</Text>
                <Text>{selectedEntry.node.sort}</Text>
                <Text type="secondary">下级组织</Text>
                <Text>{(selectedEntry.node.children ?? []).length} 个</Text>
              </div>
              <Space wrap style={{ marginTop: 6 }}>
                <Button icon={<PlusOutlined />} onClick={() => openCreate(selectedEntry.node.id)}>
                  新建下级
                </Button>
                <Button icon={<EditOutlined />} onClick={() => openEdit(selectedEntry.node.id)}>
                  修改
                </Button>
                <Popconfirm
                  title={`删除「${selectedEntry.node.name}」？`}
                  description="仅允许删除无下级组织且无在职用户的节点"
                  okText="删除"
                  okButtonProps={{ danger: true }}
                  onConfirm={() => doDelete.run(selectedEntry.node.id)}
                >
                  <Button danger icon={<DeleteOutlined />}>删除</Button>
                </Popconfirm>
              </Space>
            </Space>
          ) : (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="点击左侧节点查看详情与操作"
              style={{ padding: '72px 0' }}
            />
          )}
        </Card>
      </div>

      {/* 新建/修改弹窗：上级组织改用树形选择 */}
      <Modal
        title={editingId ? `修改组织 - ${flat[editingId]?.node.name}` : '新建组织'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={submitSave.loading}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(v) => submitSave.run(v)}>
          <Form.Item name="parentId" label="上级组织" initialValue={null}>
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              options={parentOptions}
              placeholder="留空即为根组织"
            />
          </Form.Item>
          <Form.Item name="code" label="组织编码" rules={[{ required: true, message: '请输入编码' }]}>
            <Input placeholder="如 FACTORY-01" />
          </Form.Item>
          <Form.Item name="name" label="组织名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input />
          </Form.Item>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="sort" label="排序" initialValue={0} style={{ width: 120 }}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="status" label="状态" initialValue={1} style={{ width: 160 }}>
              <Select
                options={[
                  { value: 1, label: '启用' },
                  { value: 0, label: '停用' },
                ]}
              />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </div>
  )
}
