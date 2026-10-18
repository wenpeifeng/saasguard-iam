import { Checkbox, Empty, Skeleton } from 'antd'
import type { AppClientVO } from '@/api/app'

interface ApplicationChecklistProps {
  apps: AppClientVO[]
  value: string[]
  onChange: (value: string[]) => void
  loading?: boolean
  disabled?: boolean
  emptyText?: string
}

/** 应用授权使用可扫读的勾选清单，避免在下拉框中混合已分配和可分配状态。 */
export default function ApplicationChecklist({
  apps,
  value,
  onChange,
  loading = false,
  disabled = false,
  emptyText = '暂无可选应用',
}: ApplicationChecklistProps) {
  if (loading) return <Skeleton active paragraph={{ rows: 2 }} title={false} />
  if (!apps.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} className="application-checklist-empty" />

  const toggle = (clientId: string, checked: boolean) => {
    onChange(checked ? [...value, clientId] : value.filter((id) => id !== clientId))
  }

  return (
    <div className="application-checklist">
      {apps.map((app) => {
        const checked = value.includes(app.clientId)
        return (
          <label key={app.clientId} className={`application-choice${checked ? ' application-choice--checked' : ''}`}>
            <Checkbox
              checked={checked}
              disabled={disabled}
              onChange={(event) => toggle(app.clientId, event.target.checked)}
            />
            <span className="application-choice-copy">
              <strong>{app.appName}</strong>
              <span>{app.clientId}</span>
            </span>
          </label>
        )
      })}
    </div>
  )
}
