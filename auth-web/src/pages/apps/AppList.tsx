import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App,
  Button,
  Checkbox,
  Dropdown,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, SearchOutlined, TeamOutlined, SettingOutlined, StopOutlined, CheckCircleOutlined, UserDeleteOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import PageTable from '@/components/PageTable'
import StatusTag from '@/components/StatusTag'
import FilterBar from '@/components/FilterBar'
import StandardModal from '@/components/StandardModal'
import { useDebounce } from '@/hooks/useDebounce'
import { useSubmitting } from '@/hooks/useSubmitting'
import { pageUsers, type UserVO } from '@/api/idm'
import './AppList.css'
import {
  createApp, updateApp, changeAppStatus, resetAppSecret,
  assignUsers, revokeAssignment, setAssignStatus,
  grantAssignRole, revokeAssignRole,
  fetchUserSummaries, pageAssignments,
  type AppClientVO, type AppClientForm, type AssignVO,
} from '@/api/app'

/**
 * 应用管理页 —— "物业门禁"模型中的"门"。
 * 注册/修改应用（角色标签整体替换）、启停、重置密钥（明文仅一次）、
 * 用户列表与分配入口分离：用户列表只管理已分配用户，分配弹框展示全部用户并支持批量选择。
 */
export default function AppList() {
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<AppClientVO | null>(null)
  const [secretModal, setSecretModal] = useState<{ appName: string; secret: string } | null>(null)
  const [assignedUsersClient, setAssignedUsersClient] = useState<AppClientVO | null>(null)
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
      title: '用户列表', key: 'users', width: 120,
      render: (_, app) => (
        <AssignedUserLink app={app} onClick={() => setAssignedUsersClient(app)} />
      ),
    },
    {
      title: '操作', key: 'action', width: 190,
      render: (_, app) => (
        <Space>
          <Button size="small" icon={<TeamOutlined />} onClick={() => setAssignClient(app)}>
            分配用户
          </Button>
          <Dropdown
            menu={{
              items: [
                { key: 'edit', label: '修改', onClick: () => openEdit(app) },
                {
                  key: 'toggle', label: app.status === 1 ? '停用' : '启用',
                  danger: app.status === 1,
                  onClick: () => modal.confirm({
                    title: `${app.status === 1 ? '停用' : '启用'} ${app.appName}？`,
                    content: app.status === 1 ? '停用后，所有已分配用户均无法登录该应用。' : '启用后，符合访问条件的已分配用户可以重新登录。',
                    okText: app.status === 1 ? '停用应用' : '启用应用', cancelText: '取消',
                    okButtonProps: { danger: app.status === 1 }, onOk: () => doToggle.run(app),
                  }),
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
      <StandardModal
        size="medium"
        title={editing ? `修改应用 - ${editing.appName}` : '注册应用'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        okText={editing ? '保存修改' : '注册应用'}
        cancelText="取消"
        confirmLoading={submitSave.loading}
      >
        <Form form={form} layout="vertical" onFinish={(v) => submitSave.run(v)}>
          <div className="form-grid form-grid--app">
            <Form.Item
              name="clientId" label="client_id"
              rules={[{ required: true, message: '请输入 client_id' }]}
              extra="创建后不可改"
            >
              <Input disabled={!!editing} placeholder="如 erp-web" />
            </Form.Item>
            <Form.Item
              name="appName" label="应用名称"
              rules={[{ required: true, message: '请输入应用名称' }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              name="accessTokenValidity" label="令牌有效期（秒）"
              rules={[{ required: true, message: '必填' }]}
            >
              <InputNumber min={60} className="control-fill" />
            </Form.Item>
          </div>
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
          <Form.List name="roles">
            {(fields, { add, remove }) => (
              <Space direction="vertical" className="stack-fill">
                <Typography.Text>应用角色</Typography.Text>
                {fields.map((field) => (
                  <Space key={field.key} align="start" wrap>
                    <Form.Item name={[field.name, 'roleCode']} label="角色编码" rules={[{ required: true, message: '请输入角色编码' }]}>
                      <Input placeholder="如 mes.admin" />
                    </Form.Item>
                    <Form.Item name={[field.name, 'roleName']} label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
                      <Input placeholder="如 MES 系统管理员" />
                    </Form.Item>
                    <Button danger onClick={() => remove(field.name)}>移除角色</Button>
                  </Space>
                ))}
                <Button icon={<PlusOutlined />} onClick={() => add()}>添加角色</Button>
              </Space>
            )}
          </Form.List>
        </Form>
      </StandardModal>

      {/* 密钥一次性展示 */}
      <StandardModal
        size="small"
        title={`「${secretModal?.appName}」客户端密钥`}
        open={!!secretModal}
        onCancel={() => setSecretModal(null)}
        footer={<Button type="primary" onClick={() => setSecretModal(null)}>我已保存</Button>}
      >
        <Alert
          type="warning"
          showIcon
          className="secret-warning"
          message="密钥仅本次可见，请立即复制并保存到安全位置。"
          description="认证中心仅保存 BCrypt 摘要，关闭弹框后无法再次查看当前密钥。"
        />
        <Input.Search
          readOnly
          value={secretModal?.secret}
          enterButton="复制"
          onSearch={() => {
            navigator.clipboard?.writeText(secretModal?.secret ?? '')
            message.success('已复制')
          }}
        />
      </StandardModal>

      {/* 已分配用户：查看与管理 */}
      {assignedUsersClient && (
        <AssignedUsersModal
          app={assignedUsersClient}
          onClose={() => setAssignedUsersClient(null)}
        />
      )}

      {/* 分配用户：全量用户表格 + 批量勾选 + 二次确认 */}
      {assignClient && (
        <AssignUsersModal app={assignClient} onClose={() => setAssignClient(null)} />
      )}
    </div>
  )
}

/**
 * 应用列表中的已分配人数入口。
 */
function AssignedUserLink({ app, onClick }: { app: AppClientVO; onClick: () => void }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['apps', app.clientId, 'assignment-count'],
    queryFn: () => pageAssignments(app.clientId, { pageNum: 1, pageSize: 1 }),
  })
  return (
    <Button type="link" size="small" onClick={onClick} className="inline-link-button">
      {isLoading ? '加载中…' : isError ? '查看用户' : `共 ${data?.total ?? 0} 人`}
    </Button>
  )
}

/**
 * 应用用户列表：查看分配、调整访问状态、设置角色或移除用户。
 */
function AssignedUsersModal({ app, onClose }: { app: AppClientVO; onClose: () => void }) {
  const [keyword, setKeyword] = useState('')
  const [roleUser, setRoleUser] = useState<AssignVO | null>(null)
  const debouncedKw = useDebounce(keyword)
  const queryClient = useQueryClient()

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['apps', app.clientId, 'assignments'] })
    queryClient.invalidateQueries({ queryKey: ['apps', app.clientId, 'assignment-count'] })
    queryClient.invalidateQueries({ queryKey: ['apps', 'user-summaries'] })
  }

  const doRevoke = useSubmitting(
    async (userId: number) => {
      await revokeAssignment(app.clientId, userId)
    },
    { successText: '已从应用中移除用户', onSuccess: invalidate },
  )

  const doToggleStatus = useSubmitting(
    async (userId: number, enabled: boolean) => {
      await setAssignStatus(app.clientId, userId, enabled)
    },
    { successText: '应用访问状态已更新', onSuccess: invalidate },
  )

  const busy = doRevoke.loading || doToggleStatus.loading

  const columns: ColumnsType<AssignVO> = [
    { title: '工号', dataIndex: 'employeeNo', width: 100 },
    { title: '姓名', dataIndex: 'realName', width: 90 },
    { title: '组织', dataIndex: 'orgName', ellipsis: true, width: 110 },
    { title: '员工状态', dataIndex: 'globalStatus', width: 100, render: (v) => <StatusTag value={v} map="user" /> },
    { title: '应用访问', dataIndex: 'appStatus', width: 100, render: (v) => <StatusTag value={v} /> },
    {
      title: '应用角色', dataIndex: 'roles', width: 160,
      render: (roles: string[]) => roles?.length
        ? roles.map((code) => <Tag key={code} color="blue">{app.roles.find((role) => role.roleCode === code)?.roleName || code}</Tag>)
        : <Typography.Text type="secondary">未设置</Typography.Text>,
    },
    {
      title: '操作', key: 'action', width: 340,
      render: (_, row) => {
        const globalOk = row.globalStatus === 1
        return (
          <Space size={8}>
            <Popconfirm
              title={`${row.appStatus === 1 ? '停用' : '恢复'}「${row.realName}」的应用访问？`}
              description={row.appStatus === 1
                ? `停用后无法登录 ${app.appName}，保留分配和角色，可随时恢复。`
                : `恢复后允许登录 ${app.appName}，其他应用不受影响。`}
              okText={row.appStatus === 1 ? '停用访问' : '恢复访问'}
              cancelText="取消"
              okButtonProps={{ danger: row.appStatus === 1 }}
              onConfirm={() => doToggleStatus.run(row.userId, row.appStatus !== 1)}
              disabled={!globalOk || busy}
            >
              <Button size="small" danger={row.appStatus === 1} disabled={!globalOk || busy}
                icon={row.appStatus === 1 ? <StopOutlined /> : <CheckCircleOutlined />}>
                {row.appStatus === 1 ? '停用访问' : '恢复访问'}
              </Button>
            </Popconfirm>
            <Button
              size="small" disabled={!globalOk || busy} icon={<SettingOutlined />}
              onClick={() => setRoleUser(row)}
            >
              设置角色
            </Button>
            <Popconfirm
              title={`从 ${app.appName} 移除「${row.realName}」？`}
              description="将取消该应用的分配和角色，保留员工账号，其他应用不受影响。"
              okText="移除用户"
              cancelText="取消"
              okButtonProps={{ danger: true }}
              onConfirm={() => doRevoke.run(row.userId)}
              disabled={busy}
            >
              <Button size="small" danger disabled={busy} icon={<UserDeleteOutlined />}>移除用户</Button>
            </Popconfirm>
          </Space>
        )
      },
    },
  ]

  return (
    <StandardModal
      size="wide"
      title={`${app.appName} · 用户列表`}
      open
      onCancel={onClose}
      footer={null}
    >
      <PageTable<AssignVO>
        rowKey="assignId"
        url={`/apps/${app.clientId}/assignments`}
        queryKey={['apps', app.clientId, 'assignments']}
        params={{ keyword: debouncedKw || undefined }}
        columns={columns}
        toolbar={
          <FilterBar>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="姓名 / 工号"
              className="filter-field filter-field--medium"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
          </FilterBar>
        }
      />
      {roleUser && (
        <AssignRolesModal
          app={app}
          user={roleUser}
          onClose={() => setRoleUser(null)}
          onUpdated={invalidate}
        />
      )}
    </StandardModal>
  )
}

/** 角色修改先暂存，明确保存后再提交；保留已成功的修改以便失败后重试。 */
function AssignRolesModal({ app, user, onClose, onUpdated }: {
  app: AppClientVO
  user: AssignVO
  onClose: () => void
  onUpdated: () => void
}) {
  const [savedRoles, setSavedRoles] = useState(user.roles ?? [])
  const [selectedRoles, setSelectedRoles] = useState(user.roles ?? [])
  const roleCodes = [...new Set([...app.roles.map((role) => role.roleCode), ...savedRoles])]
  const changed = selectedRoles.length !== savedRoles.length
    || selectedRoles.some((code) => !savedRoles.includes(code))
  const save = useSubmitting(async () => {
    try {
      for (const code of savedRoles.filter((role) => !selectedRoles.includes(role))) {
        await revokeAssignRole(app.clientId, user.userId, code)
        setSavedRoles((roles) => roles.filter((role) => role !== code))
      }
      for (const code of selectedRoles.filter((role) => !savedRoles.includes(role))) {
        await grantAssignRole(app.clientId, user.userId, code)
        setSavedRoles((roles) => [...roles, code])
      }
    } finally {
      onUpdated()
    }
  }, { successText: '应用角色已更新', onSuccess: onClose })

  return (
    <StandardModal
      size="small"
      title={`设置角色 · ${user.realName}`}
      open
      onCancel={onClose}
      onOk={() => save.run()}
      okText="保存角色"
      cancelText="取消"
      confirmLoading={save.loading}
      okButtonProps={{ disabled: !changed }}
      cancelButtonProps={{ disabled: save.loading }}
      closable={!save.loading}
      maskClosable={!save.loading}
      keyboard={!save.loading}
    >
      <Form layout="vertical">
        <Form.Item label={`${app.appName} 的应用角色`} extra="取消勾选可移除角色，用户仍保留该应用的访问资格。">
          {roleCodes.length ? (
            <Checkbox.Group value={selectedRoles} onChange={(values) => setSelectedRoles(values as string[])} disabled={save.loading}>
              <Space direction="vertical" size={12}>
                {roleCodes.map((code) => (
                  <Checkbox key={code} value={code}>
                    {app.roles.find((role) => role.roleCode === code)?.roleName || code}
                  </Checkbox>
                ))}
              </Space>
            </Checkbox.Group>
          ) : <Typography.Text type="secondary">该应用尚未配置角色</Typography.Text>}
        </Form.Item>
      </Form>
    </StandardModal>
  )
}

/**
 * 分配用户弹框：显示所有用户，支持搜索、分页、跨页勾选与二次确认。
 * 已分配、离职或全局停用用户仍可见，但明确标记且不可重复勾选。
 */
function AssignUsersModal({ app, onClose }: { app: AppClientVO; onClose: () => void }) {
  const [keyword, setKeyword] = useState('')
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [selectedUserIds, setSelectedUserIds] = useState<number[]>([])
  const [selectedUsers, setSelectedUsers] = useState<Record<number, UserVO>>({})
  const debouncedKeyword = useDebounce(keyword)
  const queryClient = useQueryClient()
  const { modal } = App.useApp()

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['idm', 'users', 'assignment-picker', debouncedKeyword, pageNum, pageSize],
    queryFn: () => pageUsers({
      pageNum,
      pageSize,
      keyword: debouncedKeyword || undefined,
    }),
    placeholderData: (previous) => previous,
  })

  const currentUsers = data?.records ?? []
  const currentUserIds = currentUsers.map((user) => user.id)
  const { data: summaries, isLoading: summariesLoading } = useQuery({
    queryKey: ['apps', 'user-summaries', app.clientId, currentUserIds.join(',')],
    queryFn: () => fetchUserSummaries(currentUserIds),
    enabled: currentUserIds.length > 0,
  })
  const assignedUserIds = new Set(
    currentUsers
      .filter((user) => summaries?.[String(user.id)]?.some((item) => item.clientId === app.clientId))
      .map((user) => user.id),
  )

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['apps', app.clientId, 'assignments'] })
    queryClient.invalidateQueries({ queryKey: ['apps', app.clientId, 'assignment-count'] })
    queryClient.invalidateQueries({ queryKey: ['apps', 'user-summaries'] })
  }

  const doAssign = useSubmitting(
    async (userIds: number[]) => assignUsers(app.clientId, userIds),
    {
      successText: `已分配到 ${app.appName}`,
      onSuccess: () => {
        invalidate()
        onClose()
      },
    },
  )

  const confirmAssign = () => {
    const users = selectedUserIds.map((id) => selectedUsers[id]).filter(Boolean)
    const names = users.map((user) => user.realName)
    const nameText = names.length <= 6
      ? names.join('、')
      : `${names.slice(0, 6).join('、')} 等 ${names.length} 人`
    modal.confirm({
      title: `确认分配 ${selectedUserIds.length} 名用户？`,
      content: (
        <div className="confirm-copy">
          确认将 <Typography.Text strong>{nameText}</Typography.Text> 分配到
          <Typography.Text strong> {app.appName}</Typography.Text> 吗？
        </div>
      ),
      okText: '确认分配',
      cancelText: '返回检查',
      onOk: () => doAssign.run([...selectedUserIds]),
    })
  }

  const columns: ColumnsType<UserVO> = [
    { title: '工号', dataIndex: 'employeeNo', width: 130 },
    { title: '姓名', dataIndex: 'realName', width: 110 },
    { title: '组织', dataIndex: 'orgName', width: 180, render: (value) => value || '-' },
    { title: '岗位', dataIndex: 'position', width: 150, render: (value) => value || '-' },
    {
      title: '账号状态', dataIndex: 'status', width: 110,
      render: (value) => <StatusTag value={value} map="user" />,
    },
    {
      title: '分配状态', key: 'assignStatus', width: 110,
      render: (_, user) => {
        if (assignedUserIds.has(user.id)) return <Tag color="blue">已分配</Tag>
        if (user.status !== 1) return <Tag>不可分配</Tag>
        return <Tag color="green">可分配</Tag>
      },
    },
  ]

  return (
    <StandardModal
      size="large"
      className="assign-users-modal"
      title={`分配用户到 ${app.appName}`}
      open
      onCancel={onClose}
      okText={selectedUserIds.length ? `分配所选 ${selectedUserIds.length} 人` : '确认分配'}
      cancelText="取消"
      okButtonProps={{ disabled: selectedUserIds.length === 0 }}
      confirmLoading={doAssign.loading}
      onOk={confirmAssign}
    >
      <div className="assign-users-layout">
        <Alert
          type="info"
          showIcon
          message={`勾选需要访问 ${app.appName} 的用户，已分配或非在职用户不可重复选择。`}
        />
        <div className="assign-users-toolbar">
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="搜索姓名 / 工号 / 登录名"
            className="assign-users-search"
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value)
              setPageNum(1)
            }}
          />
          <Typography.Text type={selectedUserIds.length ? undefined : 'secondary'}>
            已选择 <Typography.Text strong>{selectedUserIds.length}</Typography.Text> 人
          </Typography.Text>
        </div>
        <Table<UserVO>
          className="assign-users-table"
          rowKey="id"
          size="middle"
          columns={columns}
          dataSource={currentUsers}
          loading={isLoading || isFetching || summariesLoading}
          scroll={{ x: 820, y: '100%' }}
          rowSelection={{
            selectedRowKeys: selectedUserIds,
            preserveSelectedRowKeys: true,
            getCheckboxProps: (user) => ({
              disabled: summariesLoading || assignedUserIds.has(user.id) || user.status !== 1,
              name: user.realName,
            }),
            onChange: (keys, rows) => {
              const ids = keys.map(Number)
              setSelectedUserIds(ids)
              setSelectedUsers((previous) => {
                const next: Record<number, UserVO> = {}
                for (const id of ids) {
                  if (previous[id]) next[id] = previous[id]
                }
                for (const user of rows) next[user.id] = user
                for (const user of currentUsers) {
                  if (ids.includes(user.id)) next[user.id] = user
                }
                return next
              })
            },
          }}
          pagination={{
            total: data?.total ?? 0,
            current: pageNum,
            pageSize,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 人`,
            onChange: (page, size) => {
              setPageNum(page)
              setPageSize(size)
            },
          }}
        />
      </div>
    </StandardModal>
  )
}
