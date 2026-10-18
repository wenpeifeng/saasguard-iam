import type { ReactNode } from 'react'

/** 列表页统一筛选区：固定间距并在窄屏自动换行。 */
export default function FilterBar({ children }: { children: ReactNode }) {
  return <div className="filter-bar">{children}</div>
}
