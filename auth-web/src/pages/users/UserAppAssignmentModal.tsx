import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Empty, Skeleton, Tag, Typography } from 'antd'
import StandardModal from '@/components/StandardModal'
import { useSubmitting } from '@/hooks/useSubmitting'
import { assignUsers, fetchAllApps, fetchUserSummaries } from '@/api/app'
import type { UserVO } from '@/api/idm'
import ApplicationChecklist from './ApplicationChecklist'

/** 员工视角分配应用：已有权限与待添加应用分区展示。 */
export default function UserAppAssignmentModal({ user, onClose }: { user: UserVO; onClose: () => void }) {
  const [selected, setSelected] = useState<string[]>([])
  const queryClient = useQueryClient()
  const { modal, message } = App.useApp()
  const catalog = useQuery({
    queryKey: ['apps', 'assignment-options'],
    queryFn: fetchAllApps,
  })
  const summaries = useQuery({
    queryKey: ['apps', 'user-summaries', user.id],
    queryFn: () => fetchUserSummaries([user.id]),
  })
  const assignedApps = summaries.data?.[String(user.id)] ?? []
  const assignedIds = useMemo(() => new Set(assignedApps.map((app) => app.clientId)), [assignedApps])
  const availableApps = (catalog.data ?? []).filter((app) => app.status === 1 && !assignedIds.has(app.clientId))
  const save = useSubmitting(async () => {
    const completed: string[] = []
    try {
      for (const clientId of selected) {
        await assignUsers(clientId, [user.id])
        completed.push(clientId)
      }
    } catch (error) {
      if (completed.length) message.warning(`已完成 ${completed.length} 个应用，其余应用可重试。`)
      throw error
    } finally {
      setSelected((values) => values.filter((value) => !completed.includes(value)))
      queryClient.invalidateQueries({ queryKey: ['apps'] })
    }
  }, { successText: '应用已分配', onSuccess: onClose })

  const loading = catalog.isPending || summaries.isPending
  const loadError = catalog.isError || summaries.isError
  const selectedNames = selected.map((id) => catalog.data?.find((app) => app.clientId === id)?.appName).filter(Boolean)

  return (
    <StandardModal
      title={`分配应用 · ${user.realName}`}
      open
      size="medium"
      onCancel={onClose}
      okText={selected.length ? `分配所选 ${selected.length} 个` : '确认分配'}
      cancelText="取消"
      confirmLoading={save.loading}
      okButtonProps={{ disabled: !selected.length || loading || loadError }}
      onOk={() => modal.confirm({
        title: `确认分配 ${selected.length} 个应用？`,
        content: `确认将 ${user.realName} 分配到 ${selectedNames.join('、')} 吗？`,
        okText: '确认分配',
        cancelText: '返回检查',
        onOk: () => save.run(),
      })}
    >
      <Typography.Paragraph className="assignment-user-meta">
        登录名：{user.username}　工号：{user.employeeNo}
      </Typography.Paragraph>

      <section className="assignment-section">
        <div className="assignment-section-heading">
          <strong>已分配应用</strong>
          <span>{assignedApps.length} 个</span>
        </div>
        {summaries.isPending ? (
          <Skeleton active paragraph={{ rows: 1 }} title={false} />
        ) : assignedApps.length ? (
          <div className="assigned-application-list">
            {assignedApps.map((app) => (
              <div className="assigned-application" key={app.clientId}>
                <div>
                  <strong>{app.appName}</strong>
                  <span>{app.clientId}</span>
                </div>
                <div className="assigned-application-status">
                  {app.appStatus !== 1 && <Tag>已停用</Tag>}
                  {app.roles?.map((role) => <Tag color="blue" key={role}>{role}</Tag>)}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂未分配应用" className="application-checklist-empty" />
        )}
      </section>

      <section className="assignment-section">
        <div className="assignment-section-heading">
          <strong>添加应用</strong>
          <span>{selected.length ? `已选 ${selected.length} 个` : '可多选'}</span>
        </div>
        {loadError ? (
          <Typography.Text type="danger">应用加载失败，请取消后重试。</Typography.Text>
        ) : (
          <ApplicationChecklist
            apps={availableApps}
            value={selected}
            onChange={setSelected}
            loading={loading}
            disabled={save.loading}
            emptyText="已分配全部可用应用"
          />
        )}
      </section>
    </StandardModal>
  )
}
