import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { App, Button, Dropdown, Form, Input, Modal, Select, Space, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import PageTable from '@/components/PageTable'
import StatusTag from '@/components/StatusTag'
import OrgTreeSelect from '@/components/OrgTreeSelect'
import { useDebounce } from '@/hooks/useDebounce'
import { useSubmitting } from '@/hooks/useSubmitting'
import { useQuery } from '@tanstack/react-query'
import { fetchUserSummaries, type UserAppSummary } from '@/api/app'
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
    queryKey: ['user-apps', userId],
    queryFn: () => fetchUserSummaries([userId]),
    staleTime: 30_000,
  })
  const summaries: UserAppSummary[] | undefined = data?.[String(userId)]
  if (!summaries || summaries.length === 0) {
    return <span style={{ color: '#bfbfbf' }}>-</span>
  }
  return (
    <Space size={4} wrap>
      {summaries.map((s) => (
        <Tag
          key={s.clientId}
          color={s.appStatus === 1 ? 'geekblue' : 'default'}
          style={s.appStatus === 1 ? undefined : { textDecoration: 'line-through', opacity: 0.6 }}
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
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<number | undefined>()
  const [orgFilter, setOrgFilter] = useState<number | null>(null)
  const debouncedKw = useDebounce(keyword)
  const queryClient = useQueryClient()
  const { modal } = App.useApp()
  const [form] = Form.useForm<UserForm>()
  const [transferForm] = Form.useForm<{ targetOrgId: number; reason?: string }>()
  const [pwdForm] = Form.useForm<{ password: string }>()

  /** 写操作成功后统一失效用户列表缓存 */
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['idm', 'users'] })

  const submitSave = useSubmitting(
    async (values: UserForm) => {
      if (editing) {
        await updateUser(editing.id, values)
      } else {
        await createUser(values)
      }
    },
    { successText: '保存成功', onSuccess: () => { setModalOpen(false); invalidate() } },
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
    { successText: '口令已重置', onSuccess: () => setPwdTarget(null) },
  )

  const doLeave = useSubmitting(
    async (user: UserVO) => {
      await leaveUser(user.id)
    },
    {
      successText: '已办理离职，该账号在全部系统立即失效',
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
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (user: UserVO) => {
    setEditing(user)
    form.setFieldsValue({
      employeeNo: user.employeeNo,
      realName: user.realName,
      orgId: user.orgId,
      position: user.position,
      email: user.email,
      phone: user.phone,
    })
    setModalOpen(true)
  }

  const confirmLeave = (user: UserVO) => {
    modal.confirm({
      title: `确认将「${user.realName}」办理离职？`,
      content:
        '离职是终态操作：办理后该账号在全部业务系统立即失效，且不可再转岗/启停/重置口令。',
      okText: '确认离职',
      okButtonProps: { danger: true },
      onOk: () => doLeave.run(user),
    })
  }

  const columns: ColumnsType<UserVO> = [
    { title: '工号', dataIndex: 'employeeNo', width: 110 },
    { title: '姓名', dataIndex: 'realName', width: 100 },
    { title: '登录名', dataIndex: 'username', width: 110 },
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
        const items = [
          { key: 'edit', label: '修改档案', disabled: left, onClick: () => openEdit(user) },
          { key: 'transfer', label: '转岗', disabled: left, onClick: () => {
            transferForm.setFieldsValue({ targetOrgId: user.orgId ?? undefined, reason: '' })
            setTransferTarget(user)
          } },
          {
            key: 'status',
            label: user.status === 1 ? '全局停用' : '恢复在职',
            disabled: left,
            onClick: () => doToggleStatus.run(user),
          },
          { key: 'pwd', label: '重置口令', disabled: left, onClick: () => {
            pwdForm.resetFields()
            setPwdTarget(user)
          } },
          { type: 'divider' as const },
          { key: 'leave', label: '离职', danger: true, disabled: left, onClick: () => confirmLeave(user) },
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
        subtitle="集团主账号生命周期唯一入口：建号/转岗/离职/全局停用（离职为终态，全系统失效）"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            入职建号
          </Button>
        }
      />
      <PageTable<UserVO>
        title="主账号列表"
        url="/idm/users"
        queryKey={['idm', 'users', debouncedKw, statusFilter, orgFilter]}
        params={{
          keyword: debouncedKw || undefined,
          status: statusFilter,
          orgId: orgFilter,
        }}
        columns={columns}
        toolbar={
          <Space wrap>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="姓名 / 工号 / 手机号"
              style={{ width: 220 }}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <Select
              allowClear
              placeholder="状态"
              style={{ width: 120 }}
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
              style={{ width: 240 }}
              value={orgFilter}
              onChange={setOrgFilter}
            />
          </Space>
        }
      />

      {/* 建号/改档弹窗（工号不可改） */}
      <Modal
        title={editing ? `修改档案 - ${editing.realName}` : '入职建号'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={submitSave.loading}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(v) => submitSave.run(v)}
        >
          <Form.Item
            name="employeeNo"
            label="工号"
            rules={[{ required: true, message: '请输入工号' }]}
            extra="工号创建后不可变更，是全集团唯一匹配标识"
          >
            <Input disabled={!!editing} placeholder="如 E1001" />
          </Form.Item>
          <Form.Item name="realName" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="orgId" label="所属组织" rules={[{ required: true, message: '请选择组织' }]}>
            <OrgTreeSelect disableDisabled />
          </Form.Item>
          <Form.Item name="position" label="岗位">
            <Input />
          </Form.Item>
          <Form.Item name="phone" label="手机号">
            <Input />
          </Form.Item>
          <Form.Item name="email" label="邮箱">
            <Input />
          </Form.Item>
          {!editing && (
            <Form.Item
              name="password"
              label="初始口令"
              rules={[{ required: true, message: '请输入初始口令' }, { min: 8, message: '至少 8 位' }]}
            >
              <Input.Password placeholder="至少 8 位" />
            </Form.Item>
          )}
          <Form.Item name="entryDate" label="入职日期">
            <Input placeholder="YYYY-MM-DD" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 转岗弹窗 */}
      <Modal
        title={`转岗 - ${transferTarget?.realName ?? ''}`}
        open={!!transferTarget}
        onCancel={() => setTransferTarget(null)}
        onOk={() => transferForm.submit()}
        confirmLoading={submitTransfer.loading}
        destroyOnClose
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
      </Modal>

      {/* 重置口令弹窗 */}
      <Modal
        title={`重置口令 - ${pwdTarget?.realName ?? ''}`}
        open={!!pwdTarget}
        onCancel={() => setPwdTarget(null)}
        onOk={() => pwdForm.submit()}
        confirmLoading={submitResetPwd.loading}
        destroyOnClose
      >
        <Form form={pwdForm} layout="vertical" onFinish={(v) => submitResetPwd.run(v)}>
          <Form.Item
            name="password"
            label="新口令"
            rules={[{ required: true, message: '请输入新口令' }, { min: 8, message: '至少 8 位' }]}
          >
            <Input.Password placeholder="至少 8 位" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
