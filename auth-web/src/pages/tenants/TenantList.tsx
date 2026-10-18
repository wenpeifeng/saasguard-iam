import { useState } from 'react'
import { App, Button, Form, Input, InputNumber, Select, Space, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { ApartmentOutlined, EditOutlined, LoginOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import PageTable from '@/components/PageTable'
import FilterBar from '@/components/FilterBar'
import StandardDrawer from '@/components/StandardDrawer'
import StatusTag from '@/components/StatusTag'
import FormSection from '@/components/FormSection'
import { useDebounce } from '@/hooks/useDebounce'
import { useSubmitting } from '@/hooks/useSubmitting'
import { changeTenantStatus, createTenant, updateTenant, type TenantForm, type TenantVO } from '@/api/tenant'
import { useAuthStore } from '@/stores/auth'

const PLAN_LABEL: Record<TenantVO['planCode'], string> = {
  FREE: '免费版',
  STANDARD: '标准版',
  ENTERPRISE: '企业版',
}

/** 平台租户目录：管理租户生命周期、品牌、套餐和容量。 */
export default function TenantList() {
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<number | undefined>()
  const [planCode, setPlanCode] = useState<string | undefined>()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingTenant, setEditingTenant] = useState<TenantVO | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const debounced = useDebounce(keyword)
  const [form] = Form.useForm<TenantForm>()
  const { modal } = App.useApp()
  const switchTenant = useAuthStore((state) => state.switchTenant)

  const save = useSubmitting(
    async (values: TenantForm) => {
      if (!editingTenant) return createTenant(values)
      return updateTenant(editingTenant.id, {
        name: values.name,
        domain: values.domain,
        planCode: values.planCode,
        logoUrl: values.logoUrl,
        primaryColor: values.primaryColor,
        locale: values.locale,
        timezone: values.timezone,
        maxUsers: values.maxUsers,
        expiresAt: values.expiresAt,
      })
    },
    {
      successText: editingTenant ? '租户配置已更新' : '租户已创建，初始管理员可立即登录',
      onSuccess: () => {
        setDrawerOpen(false)
        form.resetFields()
        setRefreshKey((value) => value + 1)
      },
    },
  )

  const toggle = useSubmitting(
    async (tenant: TenantVO) => changeTenantStatus(tenant.id, tenant.status === 1 ? 0 : 1),
    { successText: '租户状态已更新', onSuccess: () => setRefreshKey((value) => value + 1) },
  )

  const columns: ColumnsType<TenantVO> = [
    { title: '租户代码', dataIndex: 'code', width: 140, fixed: 'left' },
    { title: '租户名称', dataIndex: 'name', width: 220, ellipsis: true },
    { title: '自定义域名', dataIndex: 'domain', width: 220, ellipsis: true, render: (value) => value || '-' },
    {
      title: '套餐', dataIndex: 'planCode', width: 110,
      render: (value: TenantVO['planCode']) => <Tag color={value === 'ENTERPRISE' ? 'geekblue' : undefined}>{PLAN_LABEL[value]}</Tag>,
    },
    { title: '用户上限', dataIndex: 'maxUsers', width: 100, render: (value) => (value === 0 ? '不限' : value) },
    { title: '默认时区', dataIndex: 'timezone', width: 150 },
    { title: '到期时间', dataIndex: 'expiresAt', width: 180, render: (value) => value || '长期有效' },
    { title: '状态', dataIndex: 'status', width: 90, render: (value) => <StatusTag value={value} /> },
    { title: '创建时间', dataIndex: 'createTime', width: 180 },
    {
      title: '操作', key: 'action', width: 300, fixed: 'right',
      render: (_, tenant) => (
        <Space>
          <Button
            size="small"
            icon={<LoginOutlined />}
            onClick={() => switchTenant(tenant.id, tenant.code, tenant.name)}
          >
            进入租户
          </Button>
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => {
              setEditingTenant(tenant)
              form.resetFields()
              form.setFieldsValue({ ...tenant })
              setDrawerOpen(true)
            }}
          >
            编辑
          </Button>
          <Button
            size="small"
            danger={tenant.status === 1}
            onClick={() => modal.confirm({
              title: `${tenant.status === 1 ? '停用' : '启用'}「${tenant.name}」？`,
              content: tenant.status === 1
                ? '停用后该租户所有用户将立即无法登录或继续访问，已有管理端与应用令牌也会被拒绝。'
                : '启用后该租户用户可恢复登录，请确认套餐与有效期配置正确。',
              okText: tenant.status === 1 ? '确认停用' : '确认启用',
              okButtonProps: { danger: tenant.status === 1 },
              onOk: () => toggle.run(tenant),
            })}
          >
            {tenant.status === 1 ? '停用' : '启用'}
          </Button>
        </Space>
      ),
    },
  ]

  const openCreate = () => {
    setEditingTenant(null)
    form.resetFields()
    form.setFieldsValue({
      planCode: 'STANDARD', primaryColor: '#3157D5', locale: 'zh-CN',
      timezone: 'Asia/Shanghai', maxUsers: 0, adminPassword: '123456',
    })
    setDrawerOpen(true)
  }

  return (
    <div>
      <PageHeader
        title="租户管理"
        subtitle="平台级租户目录：每个租户拥有独立的员工、组织、应用、授权与审计数据"
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>创建租户</Button>}
      />
      <PageTable<TenantVO>
        url="/tenants"
        queryKey={['tenants', refreshKey]}
        params={{ keyword: debounced || undefined, status, planCode }}
        columns={columns}
        toolbar={
          <FilterBar>
            <Input
              allowClear prefix={<SearchOutlined />} placeholder="代码 / 名称 / 域名"
              className="filter-field filter-field--wide" value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
            />
            <Select
              allowClear placeholder="套餐" className="filter-field filter-field--small"
              value={planCode} onChange={setPlanCode}
              options={Object.entries(PLAN_LABEL).map(([value, label]) => ({ value, label }))}
            />
            <Select
              allowClear placeholder="状态" className="filter-field filter-field--small"
              value={status} onChange={setStatus}
              options={[{ value: 1, label: '启用' }, { value: 0, label: '停用' }]}
            />
          </FilterBar>
        }
      />

      <StandardDrawer
        open={drawerOpen}
        title={editingTenant ? `编辑租户 · ${editingTenant.name}` : '创建 SaaS 租户'}
        onClose={() => { setDrawerOpen(false); setEditingTenant(null); form.resetFields() }}
        extra={<Button type="primary" loading={save.loading} onClick={() => form.submit()}>{editingTenant ? '保存配置' : '创建租户'}</Button>}
      >
        <Form form={form} layout="vertical" onFinish={(values) => save.run(values)} requiredMark={false}>
          <FormSection title="租户标识" description="租户代码创建后不可修改，并用于登录定位。">
            {!editingTenant && (
              <Form.Item name="code" label="租户代码" rules={[{ required: true }, { pattern: /^[a-z][a-z0-9-]{1,31}$/ }]}>
                <Input prefix={<ApartmentOutlined />} placeholder="例如 acme-cn" />
              </Form.Item>
            )}
            <Form.Item name="name" label="租户名称" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name="domain" label="自定义登录域名"><Input placeholder="login.example.com" /></Form.Item>
          </FormSection>
          <FormSection title="服务配置" description="控制租户套餐、容量和默认本地化设置。">
            <Form.Item name="planCode" label="套餐" rules={[{ required: true }]}>
              <Select options={Object.entries(PLAN_LABEL).map(([value, label]) => ({ value, label }))} />
            </Form.Item>
            <Form.Item name="maxUsers" label="用户上限（0 表示不限）"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
            <Form.Item name="logoUrl" label="品牌 Logo 地址"><Input placeholder="https://example.com/logo.svg" /></Form.Item>
            <Form.Item name="locale" label="默认语言">
              <Select options={[{ value: 'zh-CN', label: '简体中文' }, { value: 'en-US', label: 'English' }]} />
            </Form.Item>
            <Form.Item name="timezone" label="默认时区"><Input /></Form.Item>
            <Form.Item name="primaryColor" label="品牌主色"><Input type="color" /></Form.Item>
          </FormSection>
          {!editingTenant && (
            <FormSection title="初始管理员" description="创建租户时原子生成根组织与首位租户管理员。">
              <Form.Item name="adminUsername" label="管理员账号" rules={[{ required: true }]}><Input /></Form.Item>
              <Form.Item name="adminRealName" label="管理员姓名" rules={[{ required: true }]}><Input /></Form.Item>
              <Form.Item name="adminPassword" label="初始密码" rules={[{ required: true }, { min: 6 }]}>
                <Input.Password />
              </Form.Item>
            </FormSection>
          )}
        </Form>
      </StandardDrawer>
    </div>
  )
}
