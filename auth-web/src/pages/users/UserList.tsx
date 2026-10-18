import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, DatePicker, Dropdown, Form, Input, Select, Space, Tag } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import ApplicationChecklist from './ApplicationChecklist'
import UserAppAssignmentModal from './UserAppAssignmentModal'
import UserEventsModal from './UserEventsModal'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import PageTable from '@/components/PageTable'
import StatusTag from '@/components/StatusTag'
import OrgTreeSelect from '@/components/OrgTreeSelect'
import FilterBar from '@/components/FilterBar'
import StandardModal from '@/components/StandardModal'
import { useDebounce } from '@/hooks/useDebounce'
import { useSubmitting } from '@/hooks/useSubmitting'
import { assignUsers, fetchAllApps, fetchUserSummaries, type UserAppSummary } from '@/api/app'
import {
  createUser,
  updateUser,
  transferUser,
  leaveUser,
  changeUserStatus,
  resetUserPassword,
  type UserVO,
  type UserForm,
} from '@/api/idm'

/** 行内"已分配应用"标签：应用名 + 管理员标签（如 ERP·admin），禁用态显示删除线 */
function UserAppsCell({ userId }: { userId: number }) {
  const { data } = useQuery({
    queryKey: ['apps', 'user-summaries', userId],
    queryFn: () => fetchUserSummaries([userId]),
    staleTime: 30_000,
  })
  const summaries: UserAppSummary[] | undefined = data?.[String(userId)]
  if (!summaries || summaries.length === 0) {
    return <span className="empty-value">-</span>
  }
  return (
    <Space size={4} wrap>
      {summaries.map((s) => (
        <Tag
          key={s.clientId}
          color={s.appStatus === 1 ? 'geekblue' : 'default'}
          className={s.appStatus === 1 ? undefined : 'status-tag--disabled'}
        >
          {s.appName}
          {s.roles?.length ? `·${s.roles.map((r) => r.split('.').pop()).join(',')}` : ''}
        </Tag>
      ))}
    </Space>
  )
}

/**
 * 主账号列表页 —— 生命周期操作全部收口于此：
 * 建号 / 改档 / 转岗（留痕）/ 离职（终态）/ 全局停用⇄在职 / 重置口令。
 */
export default function UserList() {
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<UserVO | null>(null)
  const [transferTarget, setTransferTarget] = useState<UserVO | null>(null)
  const [pwdTarget, setPwdTarget] = useState<UserVO | null>(null)
  const [assignTarget, setAssignTarget] = useState<UserVO | null>(null)
  const [eventsTarget, setEventsTarget] = useState<UserVO | null>(null)
  const [initialAppIds, setInitialAppIds] = useState<string[]>([])
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<number | undefined>()
  const [orgFilter, setOrgFilter] = useState<number | null>(null)
  const debouncedKw = useDebounce(keyword)
  const queryClient = useQueryClient()
  const { modal, message } = App.useApp()
  type UserFields = Omit<UserForm, 'entryDate'> & { entryDate?: Dayjs }
  const [form] = Form.useForm<UserFields>()
  const [transferForm] = Form.useForm<{ targetOrgId: number; reason?: string }>()
  const [pwdForm] = Form.useForm<{ password: string }>()
  const appCatalog = useQuery({
    queryKey: ['apps', 'assignment-options'],
    queryFn: fetchAllApps,
    enabled: modalOpen && !editing,
  })
  const enabledApps = (appCatalog.data ?? []).filter((app) => app.status === 1)

  /** 写操作成功后统一失效用户列表缓存 */
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['idm', 'users'] })
    queryClient.invalidateQueries({ queryKey: ['apps'] })
  }

  const submitSave = useSubmitting(
    async (fields: UserFields) => {
      const values: UserForm = { ...fields, entryDate: fields.entryDate?.format('YYYY-MM-DD') }
      if (editing) {
        await updateUser(editing.id, values)
        message.success('档案已保存')
      } else {
        const id = await createUser(values)
        // 建档成功后立即关闭弹框，即使个别应用分配失败也不会重复建号。
        setModalOpen(false)
        invalidate()
        const assignments = await Promise.allSettled(initialAppIds.map((clientId) => assignUsers(clientId, [id])))
        const failed = assignments.filter((result) => result.status === 'rejected').length
        if (failed) {
          message.warning(`账号已创建，${failed} 个应用分配失败，可在用户操作中重试。`)
        } else {
          message.success(initialAppIds.length ? '账号已创建，应用已分配' : '账号已创建')
        }
      }
    },
    { successText: null, onSuccess: () => { setModalOpen(false); invalidate() } },
  )

  const submitTransfer = useSubmitting(
    async (values: { targetOrgId: number; reason?: string }) => {
      await transferUser(transferTarget!.id, values.targetOrgId, values.reason)
    },
    { successText: '转岗成功', onSuccess: () => { setTransferTarget(null); invalidate() } },
  )

  const submitResetPwd = useSubmitting(
    async (values: { password: string }) => {
      await resetUserPassword(pwdTarget!.id, values.password)
    },
    { successText: '密码已重置，原登录会话已失效', onSuccess: () => setPwdTarget(null) },
  )

  const doLeave = useSubmitting(
    async (user: UserVO) => {
      await leaveUser(user.id)
    },
    {
      successText: '已办理离职，应用登录资格已撤销',
      onSuccess: invalidate,
    },
  )

  const doToggleStatus = useSubmitting(
    async (user: UserVO) => {
      await changeUserStatus(user.id, user.status === 1 ? 3 : 1)
    },
    { successText: '状态已变更', onSuccess: invalidate },
  )

  const openCreate = () => {
    setEditing(null)
    setInitialAppIds([])
    form.resetFields()
    form.setFieldsValue({ entryDate: dayjs() })
    setModalOpen(true)
  }

  const openEdit = (user: UserVO) => {
    if (user.immutable) {
      message.info('系统种子账号为只读主档')
      return
    }
    setEditing(user)
    setInitialAppIds([])
    form.resetFields()
    form.setFieldsValue({
      employeeNo: user.employeeNo,
      realName: user.realName,
      orgId: user.orgId,
      position: user.position,
      email: user.email,
      phone: user.phone,
      entryDate: user.entryDate ? dayjs(user.entryDate) : undefined,
    })
    setModalOpen(true)
  }

  const confirmLeave = (user: UserVO) => {
    modal.confirm({
      title: `确认将「${user.realName}」办理离职？`,
      content:
        '离职后不能恢复在职，将禁止登录所有应用并撤销认证中心会话，保留员工档案和变更记录。业务系统已有会话按其同步机制失效。',
      okText: '确认离职',
      okButtonProps: { danger: true },
      onOk: () => doLeave.run(user),
    })
  }

  const columns: ColumnsType<UserVO> = [
    { title: '工号', dataIndex: 'employeeNo', width: 110 },
    { title: '姓名', dataIndex: 'realName', width: 100 },
    { title: '登录名', dataIndex: 'username', width: 110 },
    {
      title: '来源',
      dataIndex: 'accountSource',
      width: 100,
      render: (value: UserVO['accountSource']) =>
        value === 'SEED' ? <Tag color="gold">系统种子</Tag> : <Tag color="blue">入职建号</Tag>,
    },
    { title: '组织', dataIndex: 'orgName', ellipsis: true },
    { title: '岗位', dataIndex: 'position', width: 110, ellipsis: true },
    { title: '手机号', dataIndex: 'phone', width: 120 },
    {
      title: '已分配应用', key: 'apps', width: 220,
      render: (_, user) => <UserAppsCell userId={user.id} />,
    },
    { title: '状态', dataIndex: 'status', width: 100, render: (v) => <StatusTag value={v} map="user" /> },
    { title: '入职日期', dataIndex: 'entryDate', width: 110 },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, user) => {
        const left = user.status === 2
        const readonly = user.immutable
        const items = [
          { key: 'events', label: '变更记录', onClick: () => setEventsTarget(user) },
          { key: 'edit', label: '修改档案', disabled: left || readonly, onClick: () => openEdit(user) },
          { key: 'apps', label: '分配应用', disabled: user.status !== 1, onClick: () => setAssignTarget(user) },
          { key: 'transfer', label: '转岗', disabled: user.status !== 1 || readonly, onClick: () => {
            transferForm.setFieldsValue({ targetOrgId: user.orgId ?? undefined, reason: '' })
            setTransferTarget(user)
          } },
          {
            key: 'status',
            label: user.status === 1 ? '全局停用' : '恢复在职',
            danger: user.status === 1,
            disabled: left || readonly,
            onClick: () => modal.confirm({ title: `${user.status === 1 ? '全局停用' : '恢复在职'} · ${user.realName}`,
              content: user.status === 1 ? '将禁止登录所有应用并撤销认证中心会话。恢复后需要重新登录。' : '恢复后可重新登录，单独停用的应用仍保持停用。',
              okText: '确认', cancelText: '取消', okButtonProps: { danger: user.status === 1 }, onOk: () => doToggleStatus.run(user) }),
          },
          { key: 'pwd', label: <span className="menu-item--emphasis">重置密码</span>, disabled: left || readonly, onClick: () => {
            pwdForm.resetFields()
            setPwdTarget(user)
          } },
          { type: 'divider' as const },
          { key: 'leave', label: '离职', danger: true, disabled: left || readonly, onClick: () => confirmLeave(user) },
        ]
        return (
          <Dropdown menu={{ items }} trigger={['click']}>
            <Button size="small">操作</Button>
          </Dropdown>
        )
      },
    },
  ]

  return (
    <div>
      <PageHeader
        title="主账号管理"
        subtitle="集团主账号唯一入口；系统种子统一展示为只读，普通员工在此完成建号/转岗/离职/全局停用"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            入职建号
          </Button>
        }
      />
      <PageTable<UserVO>
        title="主账号列表"
        url="/idm/users"
        queryKey={['idm', 'users']}
        params={{
          keyword: debouncedKw || undefined,
          status: statusFilter,
          orgId: orgFilter,
        }}
        columns={columns}
        toolbar={
          <FilterBar>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="姓名 / 工号 / 登录名"
              className="filter-field filter-field--medium"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <Select
              allowClear
              placeholder="状态"
              className="filter-field filter-field--small"
              value={statusFilter}
              onChange={setStatusFilter}
              options={[
                { value: 1, label: '在职' },
                { value: 2, label: '离职' },
                { value: 3, label: '全局停用' },
              ]}
            />
            <OrgTreeSelect
              allowClear
              placeholder="所属组织"
              className="filter-field filter-field--large"
              value={orgFilter}
              onChange={setOrgFilter}
            />
          </FilterBar>
        }
      />

      {/* 建号/改档弹窗（工号不可改） */}
      <StandardModal
        size="medium"
        title={editing ? `修改档案 - ${editing.realName}` : '入职建号'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        okText={editing ? '保存修改' : '创建账号'}
        cancelText="取消"
        confirmLoading={submitSave.loading}
      >
        <Form
          form={form}
          layout="vertical"
          className="employee-form"
          onFinish={(v) => submitSave.run(v)}
        >
          <div className="employee-form-grid">
            {editing && (
              <Form.Item name="employeeNo" label="工号" className="employee-form-item--wide">
                <Input disabled />
              </Form.Item>
            )}
            <Form.Item name="realName" label="姓名" className="employee-form-item--wide" rules={[{ required: true, message: '请输入姓名' }]}>
              <Input />
            </Form.Item>
            <Form.Item name="orgId" label="所属组织" className="employee-form-item--wide" extra={editing ? '变更组织请使用“转岗”。' : undefined} rules={[{ required: true, message: '请选择组织' }]}>
              <OrgTreeSelect disableDisabled disabled={!!editing} />
            </Form.Item>
            {!editing && (
              <Form.Item name="password" label="初始密码" className="employee-form-item--wide"
                rules={[{ required: true, message: '请输入初始密码' }, { min: 6, max: 64, message: '请输入 6–64 位密码' }]}>
                <Input.Password placeholder="6–64 位" autoComplete="new-password" />
              </Form.Item>
            )}
            <Form.Item name="position" label="岗位">
              <Input />
            </Form.Item>
            <Form.Item name="entryDate" label="入职日期">
              <DatePicker className="control-fill" format="YYYY-MM-DD" disabledDate={(date) => date.isAfter(dayjs(), 'day')} />
            </Form.Item>
            <Form.Item name="phone" label="手机号">
              <Input />
            </Form.Item>
            <Form.Item name="email" label="邮箱">
              <Input />
            </Form.Item>
            {!editing && (
              <Form.Item label="初始应用（可选）" className="employee-form-item--wide">
                {appCatalog.isError ? (
                  <div className="application-load-error">应用加载失败，仍可先创建账号。</div>
                ) : (
                  <ApplicationChecklist
                    apps={enabledApps}
                    value={initialAppIds}
                    onChange={setInitialAppIds}
                    loading={appCatalog.isPending}
                    disabled={submitSave.loading}
                    emptyText="暂无可分配的应用"
                  />
                )}
              </Form.Item>
            )}
          </div>
        </Form>
      </StandardModal>

      {/* 转岗弹窗 */}
      <StandardModal
        size="small"
        title={`转岗 - ${transferTarget?.realName ?? ''}`}
        open={!!transferTarget}
        onCancel={() => setTransferTarget(null)}
        onOk={() => transferForm.submit()}
        okText="确认转岗"
        cancelText="取消"
        confirmLoading={submitTransfer.loading}
      >
        <Form form={transferForm} layout="vertical" onFinish={(v) => submitTransfer.run(v)}>
          <Form.Item
            name="targetOrgId"
            label="目标组织"
            rules={[{ required: true, message: '请选择目标组织' }]}
          >
            <OrgTreeSelect disableDisabled />
          </Form.Item>
          <Form.Item name="reason" label="转岗原因（留痕）">
            <Input.TextArea rows={2} placeholder="如：组织架构调整" />
          </Form.Item>
        </Form>
      </StandardModal>

      {/* 重置口令弹窗 */}
      <StandardModal
        size="small"
        title={`重置密码 - ${pwdTarget?.realName ?? ''}`}
        open={!!pwdTarget}
        onCancel={() => setPwdTarget(null)}
        onOk={() => pwdForm.submit()}
        okText="重置密码"
        cancelText="取消"
        confirmLoading={submitResetPwd.loading}
      >
        <Form form={pwdForm} layout="vertical" onFinish={(v) => submitResetPwd.run(v)}>
          <Form.Item
            name="password"
            label="新密码"
            extra="重置后原登录会话失效，需要使用新密码重新登录。"
            rules={[{ required: true, message: '请输入新密码' }, { min: 6, max: 64, message: '请输入 6–64 位密码' }]}
          >
            <Input.Password placeholder="6–64 位" autoComplete="new-password" />
          </Form.Item>
        </Form>
      </StandardModal>
      {assignTarget && <UserAppAssignmentModal user={assignTarget} onClose={() => setAssignTarget(null)} />}
      {eventsTarget && <UserEventsModal user={eventsTarget} onClose={() => setEventsTarget(null)} />}
    </div>
  )
}
