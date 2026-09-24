import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Drawer, Dropdown, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, SearchOutlined, TeamOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import PageTable from '@/components/PageTable'
import StatusTag from '@/components/StatusTag'
import { useDebounce } from '@/hooks/useDebounce'
import { useSubmitting } from '@/hooks/useSubmitting'
import { type UserVO } from '@/api/idm'
import {
  createApp, updateApp, changeAppStatus, resetAppSecret,
  assignUsers, revokeAssignment, setAssignStatus,
  grantAssignRole, revokeAssignRole,
  type AppClientVO, type AppClientForm,
} from '@/api/app'

/**
 * 应用管理页 —— "物业门禁"模型中的"门"。
 * 注册/修改应用（角色标签整体替换）、启停、重置密钥（明文仅一次）、
 * 分配管理抽屉（进门权/应用级启停/角色标签）。
 */
export default function AppList() {
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<AppClientVO | null>(null)
  const [secretModal, setSecretModal] = useState<{ appName: string; secret: string } | null>(null)
  const [assignClient, setAssignClient] = useState<AppClientVO | null>(null)
  const queryClient = useQueryClient()
  const { message, modal } = App.useApp()
  const [form] = Form.useForm<AppClientForm>()

  const invalidateApps = () => queryClient.invalidateQueries({ queryKey: ['apps'] })

  const submitSave = useSubmitting(
    async (values: AppClientForm) => {
      if (editing) {
        await updateApp(editing.clientId, values)
      } else {
        const resp = await createApp(values)
        setSecretModal({ appName: values.appName, secret: resp.clientSecret })
      }
    },
    { successText: '保存成功', onSuccess: () => { setModalOpen(false); invalidateApps() } },
  )

  const doToggle = useSubmitting(
    async (app: AppClientVO) => {
      await changeAppStatus(app.clientId, app.status === 1 ? 0 : 1)
    },
    { successText: '状态已变更', onSuccess: invalidateApps },
  )

  const confirmResetSecret = (app: AppClientVO) => {
    modal.confirm({
      title: `重置「${app.appName}」的客户端密钥？`,
      content: '重置后旧密钥立即失效，业务系统需同步更新配置。',
      okText: '重置',
      onOk: async () => {
        const resp = await resetAppSecret(app.clientId)
        setSecretModal({ appName: app.appName, secret: resp.clientSecret })
      },
    })
  }

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ accessTokenValidity: 7200, consentRequired: false, roles: [] })
    setModalOpen(true)
  }

  const openEdit = (app: AppClientVO) => {
    setEditing(app)
    form.setFieldsValue({
      clientId: app.clientId,
      appName: app.appName,
      redirectUri: app.redirectUri,
      accessTokenValidity: app.accessTokenValidity,
      consentRequired: app.consentRequired,
      adminUserId: app.adminUserId ?? undefined,
      remark: app.remark ?? undefined,
      roles: app.roles,
    })
    setModalOpen(true)
  }

  const columns: ColumnsType<AppClientVO> = [
    { title: 'client_id', dataIndex: 'clientId', width: 130 },
    { title: '应用名称', dataIndex: 'appName', width: 130 },
    { title: '回调地址', dataIndex: 'redirectUri', ellipsis: true },
    {
      title: '角色标签', dataIndex: 'roles', width: 160,
      render: (roles: { roleCode: string }[]) =>
        roles?.length ? roles.map((r) => <Tag key={r.roleCode}>{r.roleCode}</Tag>) : '-',
    },
    { title: '令牌有效期', dataIndex: 'accessTokenValidity', width: 100, render: (v) => `${v}s` },
    { title: '授权确认页', dataIndex: 'consentRequired', width: 100, render: (v) => (v ? '需要' : '跳过') },
    { title: '状态', dataIndex: 'status', width: 80, render: (v) => <StatusTag value={v} /> },
    {
      title: '操作', key: 'action', width: 170,
      render: (_, app) => (
        <Space>
          <Button size="small" icon={<TeamOutlined />} onClick={() => setAssignClient(app)}>
            分配
          </Button>
          <Dropdown
            menu={{
              items: [
                { key: 'edit', label: '修改', onClick: () => openEdit(app) },
                {
                  key: 'toggle', label: app.status === 1 ? '停用' : '启用',
                  onClick: () => doToggle.run(app),
                },
                { key: 'secret', label: '重置密钥', onClick: () => confirmResetSecret(app) },
              ],
            }}
            trigger={['click']}
          >
            <Button size="small">更多</Button>
          </Dropdown>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="应用管理"
        subtitle="应用注册（OAuth2 client）：client_id/密钥/回调地址/令牌有效期/角色标签；停用后该应用所有登录被拒"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            注册应用
          </Button>
        }
      />
      <PageTable<AppClientVO>
        title="应用列表"
        url="/apps"
        queryKey={['apps']}
        columns={columns}
      />

      {/* 注册/修改弹窗 */}
      <Modal
        title={editing ? `修改应用 - ${editing.appName}` : '注册应用'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={submitSave.loading}
        destroyOnClose
        width={640}
      >
        <Form form={form} layout="vertical" onFinish={(v) => submitSave.run(v)}>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item
              name="clientId" label="client_id" style={{ width: 200 }}
              rules={[{ required: true, message: '请输入 client_id' }]}
              extra="创建后不可改"
            >
              <Input disabled={!!editing} placeholder="如 erp-web" />
            </Form.Item>
            <Form.Item
              name="appName" label="应用名称" style={{ width: 200 }}
              rules={[{ required: true, message: '请输入应用名称' }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              name="accessTokenValidity" label="令牌有效期(秒)" style={{ width: 140 }}
              rules={[{ required: true, message: '必填' }]}
            >
              <InputNumber min={60} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item
            name="redirectUri" label="回调地址"
            rules={[{ required: true, message: '请输入回调地址' }]}
            extra="授权码投递点，/oauth/authorize 的 redirect_uri 必须精确匹配"
          >
            <Input placeholder="http://localhost:8080/sso/callback" />
          </Form.Item>
          <Form.Item name="consentRequired" label="授权确认页" valuePropName="checked">
            <Switch checkedChildren="需要" unCheckedChildren="跳过" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input />
          </Form.Item>
          <Form.Item
            name="roles" label="角色标签（整体替换，本期即 xxx.admin 系统管理员标签）"
          >
            <Select
              mode="tags"
              placeholder="输入角色标签回车添加，如 erp.admin"
              open={false}
              tokenSeparators={[' ', ',']}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 密钥一次性展示 */}
      <Modal
        title={`「${secretModal?.appName}」客户端密钥`}
        open={!!secretModal}
        onCancel={() => setSecretModal(null)}
        footer={<Button type="primary" onClick={() => setSecretModal(null)}>我已保存</Button>}
      >
        <p style={{ color: '#faad14' }}>密钥仅本次可见（库内只存 BCrypt），请立即复制保存到安全位置：</p>
        <Input.Search
          readOnly
          value={secretModal?.secret}
          enterButton="复制"
          onSearch={() => {
            navigator.clipboard?.writeText(secretModal?.secret ?? '')
            message.success('已复制')
          }}
        />
      </Modal>

      {/* 分配管理抽屉 */}
      {assignClient && (
        <AssignDrawer
          app={assignClient}
          onClose={() => setAssignClient(null)}
        />
      )}
    </div>
  )
}

/**
 * 分配管理抽屉：已分配用户列表（= 业务系统管理端的用户列表）
 * + 分配/回收/应用级启停/角色标签授予回收。
 */
function AssignDrawer({ app, onClose }: { app: AppClientVO; onClose: () => void }) {
  const [keyword, setKeyword] = useState('')
  const [assignOpen, setAssignOpen] = useState(false)
  const [assignKeyword, setAssignKeyword] = useState('')
  const debouncedKw = useDebounce(keyword)
  const debouncedAssignKw = useDebounce(assignKeyword)
  const queryClient = useQueryClient()

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['apps', app.clientId, 'assignments'] })

  const roleOptions = app.roles.map((r) => ({ value: r.roleCode, label: `${r.roleName}（${r.roleCode}）` }))

  const doAssign = useSubmitting(
    async (userIds: number[]) => {
      await assignUsers(app.clientId, userIds)
    },
    { successText: '分配成功', onSuccess: () => { setAssignOpen(false); invalidate() } },
  )

  const doRevoke = useSubmitting(
    async (userId: number) => {
      await revokeAssignment(app.clientId, userId)
    },
    { successText: '已回收', onSuccess: invalidate },
  )

  const doToggleStatus = useSubmitting(
    async (userId: number, enabled: boolean) => {
      await setAssignStatus(app.clientId, userId, enabled)
    },
    { successText: '应用级状态已变更', onSuccess: invalidate },
  )

  const doRole = useSubmitting(
    async (userId: number, roleCode: string, grant: boolean) => {
      if (grant) {
        await grantAssignRole(app.clientId, userId, roleCode)
      } else {
        await revokeAssignRole(app.clientId, userId, roleCode)
      }
    },
    { successText: '角色标签已更新', onSuccess: invalidate },
  )

  const columns: ColumnsType<import('@/api/app').AssignVO> = [
    { title: '工号', dataIndex: 'employeeNo', width: 100 },
    { title: '姓名', dataIndex: 'realName', width: 90 },
    { title: '组织', dataIndex: 'orgName', ellipsis: true, width: 110 },
    { title: '全局状态', dataIndex: 'globalStatus', width: 90, render: (v) => <StatusTag value={v} map="user" /> },
    { title: '应用级', dataIndex: 'appStatus', width: 80, render: (v) => <StatusTag value={v} /> },
    {
      title: '角色标签', dataIndex: 'roles', width: 150,
      render: (roles: string[]) => roles?.length ? roles.map((r) => <Tag key={r} color="blue">{r}</Tag>) : '-',
    },
    {
      title: '操作', key: 'action', width: 220,
      render: (_, row) => {
        const globalOk = row.globalStatus === 1
        return (
          <Space wrap>
            <Button
              size="small" disabled={!globalOk}
              onClick={() => doToggleStatus.run(row.userId, row.appStatus !== 1)}
            >
              {row.appStatus === 1 ? '应用级禁用' : '应用级启用'}
            </Button>
            <Select
              size="small" style={{ width: 130 }} placeholder="角色标签"
              allowClear
              value={row.roles?.[0]}
              options={roleOptions}
              disabled={!globalOk}
              onChange={(val) => {
                if (val) doRole.run(row.userId, val, true)
                else if (row.roles?.[0]) doRole.run(row.userId, row.roles[0], false)
              }}
            />
            <Popconfirm
              title={`回收「${row.realName}」对本应用的进门权？`}
              onConfirm={() => doRevoke.run(row.userId)}
              disabled={!globalOk}
            >
              <Button size="small" danger disabled={!globalOk}>回收</Button>
            </Popconfirm>
          </Space>
        )
      },
    },
  ]

  return (
    <Drawer
      title={`分配管理 - ${app.appName}（${app.clientId}）`}
      open
      width={960}
      onClose={onClose}
      destroyOnClose
    >
      <PageTable<import('@/api/app').AssignVO>
        title={`已分配用户（即 ${app.appName} 管理端可见的用户列表）`}
        url={`/apps/${app.clientId}/assignments`}
        queryKey={['apps', app.clientId, 'assignments', debouncedKw]}
        params={{ keyword: debouncedKw || undefined }}
        columns={columns}
        toolbar={
          <Space>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="姓名 / 工号"
              style={{ width: 200 }}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setAssignOpen(true)}>
              分配用户
            </Button>
          </Space>
        }
      />

      {/* 分配用户弹窗：搜索在职用户多选 */}
      <Modal
        title={`分配用户到「${app.appName}」`}
        open={assignOpen}
        onCancel={() => setAssignOpen(false)}
        footer={null}
        destroyOnClose
        width={640}
      >
        <AssignUserPicker
          keyword={debouncedAssignKw}
          onSearch={setAssignKeyword}
          onSubmit={(ids) => doAssign.run(ids)}
          loading={doAssign.loading}
        />
      </Modal>
    </Drawer>
  )
}

/** 在职用户搜索多选（仅状态=在职 可分配） */
function AssignUserPicker({
  keyword, onSearch, onSubmit, loading,
}: {
  keyword: string
  onSearch: (kw: string) => void
  onSubmit: (ids: number[]) => void
  loading: boolean
}) {
  const [selected, setSelected] = useState<number[]>([])
  const { data } = useQuery({
    queryKey: ['idm', 'users', 'assignable', keyword],
    queryFn: () =>
      import('@/api/idm').then((m) =>
        m.pageUsers({ pageNum: 1, pageSize: 20, keyword: keyword || undefined, status: 1 }),
      ),
  })
  const options = (data?.records ?? []).map((u: UserVO) => ({
    value: u.id,
    label: `${u.realName}（${u.employeeNo} / ${u.orgName ?? '-'}）`,
  }))
  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Select
        mode="multiple"
        showSearch
        filterOption={false}
        placeholder="搜索姓名/工号后选择（仅在职员工）"
        options={options}
        value={selected}
        onSearch={onSearch}
        onChange={setSelected}
        style={{ width: '100%' }}
        notFoundContent={keyword ? '无匹配在职员工' : '输入关键字搜索'}
      />
      <Button type="primary" block loading={loading} disabled={!selected.length} onClick={() => onSubmit(selected)}>
        分配所选 {selected.length || ''} 人
      </Button>
    </Space>
  )
}
