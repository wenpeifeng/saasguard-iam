import { useEffect, useState, type ReactNode } from 'react'
import { hashKey, useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { get, type PageResult } from '@/api/request'

/** 查询参数：分页 + 业务筛选（业务字段由页面扩展） */
export interface PageQuery {
  pageNum: number
  pageSize: number
  [key: string]: unknown
}

interface PageTableProps<T> {
  /** 接口路径（如 '/system/users'），GET 请求 */
  url: string
  /** TanStack Query 缓存键前缀（如 ['system','users']），筛选值会自动拼进 key 触发重查 */
  queryKey: readonly unknown[]
  /** 表格列定义（与 AntD Table 一致） */
  columns: ColumnsType<T>
  /** 行键，默认 id */
  rowKey?: string | ((record: T) => string | number)
  /** 筛选参数（变化自动重新查询；配合 useDebounce 使用体验最佳） */
  params?: Record<string, unknown>
  /** 顶部工具栏（搜索框/操作按钮） */
  toolbar?: ReactNode
  /** 额外包一层说明 */
  title?: string
}

/**
 * 统一查询表格 —— 全系统所有分页列表页的标准容器。
 * <p>
 * 统一封装（商业系统一致性核心）：
 * - 分页参数管理与页码跳转、showTotal 文案统一（"共 N 条"）；
 * - loading / 空态 / 错误兜底（TanStack Query 自动管理，失败自动重试 1 次）；
 * - 服务端分页约定：pageNum/pageSize -> PageResult<T>。
 *
 * @example
 * <PageTable<UserRow>
 *   url="/system/users"
 *   queryKey={['system','users',kw]}
 *   params={{ keyword: kw || undefined }}
 *   toolbar={<Input.Search ... />}
 *   columns={columns}
 * />
 */
export default function PageTable<T extends { id?: number | string }>({
  url,
  queryKey,
  columns,
  rowKey = 'id',
  params = {},
  toolbar,
  title,
}: PageTableProps<T>) {
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  const queryParams: PageQuery = { ...params, pageNum, pageSize }
  const filterKey = hashKey([...queryKey, params])

  useEffect(() => {
    setPageNum(1)
  }, [filterKey])

  const { data, isLoading, isFetching, isError, refetch } = useQuery({
    queryKey: [...queryKey, params, pageNum, pageSize],
    queryFn: () => get<PageResult<T>>(url, queryParams),
    placeholderData: (prev) => prev, // 翻页时保留旧数据，避免闪空
  })

  return (
    <Card title={title} className="page-table-card">
      {toolbar && <div className="page-table-toolbar">{toolbar}</div>}
      {isError && (
        <Alert
          type="error"
          showIcon
          className="page-table-error"
          message="数据加载失败"
          description="请检查网络连接后重试。"
          action={<Button size="small" onClick={() => void refetch()}>重新加载</Button>}
        />
      )}
      <Table<T>
        rowKey={rowKey}
        columns={columns}
        dataSource={data?.records ?? []}
        loading={isLoading || isFetching}
        locale={{ emptyText: isError ? '暂时无法加载数据' : '暂无数据' }}
        scroll={{ x: 'max-content' }}
        pagination={{
          total: data?.total ?? 0,
          current: pageNum,
          pageSize,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (page, size) => {
            setPageNum(page)
            setPageSize(size)
          },
        }}
      />
    </Card>
  )
}
