import { useQuery } from '@tanstack/react-query'
import { Alert, Table } from 'antd'
import StandardModal from '@/components/StandardModal'
import { getUserEvents, type UserVO, type UserEventVO } from '@/api/idm'

const labels: Record<string, string> = { CREATE: '入职建号', UPDATE: '修改档案', TRANSFER: '转岗', LEAVE: '离职', STATUS_CHANGE: '状态变更', RESET_PWD: '重置密码' }

function describe(event: UserEventVO): string {
  let detail: Record<string, unknown> = {}
  try { detail = JSON.parse(event.detail ?? '{}') } catch { return '历史记录' }
  if (event.eventType === 'TRANSFER') return `${detail.fromOrgName ?? detail.fromOrgId} → ${detail.toOrgName ?? detail.toOrgId}${detail.reason ? `；${detail.reason}` : ''}`
  if (event.eventType === 'CREATE') return `工号：${detail.employeeNo ?? '—'}`
  if (event.eventType === 'STATUS_CHANGE') return Number(detail.to) === 3 ? '全局停用' : '恢复在职'
  if (event.eventType === 'RESET_PWD') return '密码已重置（不记录密码内容）'
  return '—'
}

/** 保留离职员工的变更记录，便于管理员追溯操作。 */
export default function UserEventsModal({ user, onClose }: { user: UserVO; onClose: () => void }) {
  const query = useQuery({ queryKey: ['idm', 'users', user.id, 'events'], queryFn: () => getUserEvents(user.id) })
  return (
    <StandardModal open title={`变更记录 · ${user.realName}（最近 100 条）`} size="large" footer={null} onCancel={onClose}>
      {query.isError && <Alert type="error" message="变更记录加载失败，请关闭后重试。" />}
      <Table<UserEventVO> rowKey="id" dataSource={query.data ?? []} loading={query.isPending}
        columns={[
          { title: '时间', dataIndex: 'createTime', width: 190 },
          { title: '操作', dataIndex: 'eventType', width: 110, render: (type: string) => labels[type] ?? type },
          { title: '说明', render: (_, event) => describe(event) },
        ]} scroll={{ x: 600 }} pagination={{ pageSize: 10 }} />
    </StandardModal>
  )
}
