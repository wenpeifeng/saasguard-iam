import { useState } from 'react'
import { Input, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { SearchOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'
import PageTable from '@/components/PageTable'
import FilterBar from '@/components/FilterBar'
import { useDebounce } from '@/hooks/useDebounce'
import { type OpLog } from '@/api/app'

/** HTTP 状态码语义颜色 */
function statusColor(code: number) {
  if (code < 300) return 'success'
  if (code < 500) return 'warning'
  return 'error'
}

/**
 * 操作日志页（审计员只读）：管理端写操作统一切面落库。
 */
export default function OpLogList() {
  const [username, setUsername] = useState('')
  const [uri, setUri] = useState('')
  const debouncedUser = useDebounce(username)
  const debouncedUri = useDebounce(uri)

  const columns: ColumnsType<OpLog> = [
    { title: '操作人', dataIndex: 'username', width: 100, render: (v) => v ?? '-' },
    {
      title: '方法', dataIndex: 'method', width: 70,
      render: (v) => (
        <Tag color={v === 'DELETE' ? 'red' : v === 'POST' ? 'blue' : 'green'}>{v}</Tag>
      ),
    },
    { title: 'URI', dataIndex: 'uri', width: 280, ellipsis: true },
    { title: '状态码', dataIndex: 'statusCode', width: 80, render: (v) => <Tag color={statusColor(v)}>{v}</Tag> },
    { title: '明细', dataIndex: 'detail', ellipsis: true, render: (v) => v ?? '-' },
    { title: 'IP', dataIndex: 'ip', width: 110 },
    { title: '耗时', dataIndex: 'costMs', width: 80, render: (v) => (v == null ? '-' : `${v}ms`) },
    { title: '时间', dataIndex: 'createTime', width: 160 },
  ]

  return (
    <div>
      <PageHeader title="操作日志" subtitle="管理端写操作（POST/PUT/DELETE）统一切面落库，敏感字段已脱敏" />
      <PageTable<OpLog>
        title="操作日志"
        url="/audit/op-logs"
        queryKey={['audit', 'op-logs']}
        params={{ username: debouncedUser || undefined, uri: debouncedUri || undefined }}
        columns={columns}
        toolbar={
          <FilterBar>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="操作人"
              className="filter-field filter-field--medium"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="URI"
              className="filter-field filter-field--large"
              value={uri}
              onChange={(e) => setUri(e.target.value)}
            />
          </FilterBar>
        }
      />
    </div>
  )
}
