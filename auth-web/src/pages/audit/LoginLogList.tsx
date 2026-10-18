import { useState } from 'react'
import { Input, Select, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { SearchOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import PageTable from '@/components/PageTable'
import FilterBar from '@/components/FilterBar'
import { useDebounce } from '@/hooks/useDebounce'
import { type LoginLog } from '@/api/app'

/**
 * 登录日志页（审计员只读）：成功/失败、IP、UA、登录类型（管理端/OIDC）。
 */
export default function LoginLogList() {
  const [username, setUsername] = useState('')
  const [loginType, setLoginType] = useState<string | undefined>()
  const [success, setSuccess] = useState<number | undefined>()
  const debounced = useDebounce(username)

  const columns: ColumnsType<LoginLog> = [
    { title: '账号', dataIndex: 'username', width: 120 },
    { title: '用户ID', dataIndex: 'userId', width: 80 },
    {
      title: '类型', dataIndex: 'loginType', width: 90,
      render: (v) => (v === 'OIDC' ? <Tag color="geekblue">OIDC</Tag> : <Tag>管理端</Tag>),
    },
    { title: '应用', dataIndex: 'clientId', width: 110, render: (v) => v ?? '-' },
    {
      title: '结果', dataIndex: 'success', width: 80,
      render: (v) => (v === 1 ? <Tag color="success">成功</Tag> : <Tag color="error">失败</Tag>),
    },
    { title: '失败原因', dataIndex: 'failReason', width: 170, render: (v) => v ?? '-' },
    { title: 'IP', dataIndex: 'ip', width: 120 },
    { title: 'User-Agent', dataIndex: 'userAgent', ellipsis: true },
    { title: '时间', dataIndex: 'createTime', width: 160 },
  ]

  return (
    <div>
      <PageHeader title="登录日志" subtitle="管理端与 OIDC 统一登录页的全部登录尝试（成功/失败均留痕）" />
      <PageTable<LoginLog>
        title="登录日志"
        url="/audit/login-logs"
        queryKey={['audit', 'login-logs']}
        params={{ username: debounced || undefined, loginType, success }}
        columns={columns}
        toolbar={
          <FilterBar>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="账号"
              className="filter-field filter-field--medium"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
            <Select
              allowClear
              placeholder="登录类型"
              className="filter-field filter-field--small"
              value={loginType}
              onChange={setLoginType}
              options={[
                { value: 'ADMIN', label: '管理端' },
                { value: 'OIDC', label: 'OIDC' },
              ]}
            />
            <Select
              allowClear
              placeholder="结果"
              className="filter-field filter-field--small"
              value={success}
              onChange={setSuccess}
              options={[
                { value: 1, label: '成功' },
                { value: 0, label: '失败' },
              ]}
            />
          </FilterBar>
        }
      />
    </div>
  )
}
