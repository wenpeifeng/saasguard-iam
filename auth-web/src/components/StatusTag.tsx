import { Tag } from 'antd'

/** 主账号全局状态：1-在职 2-离职（终态）3-全局停用 */
const USER_STATUS: Record<number, { text: string; color: string }> = {
  1: { text: '在职', color: 'success' },
  2: { text: '离职', color: 'default' },
  3: { text: '全局停用', color: 'warning' },
}

/** 通用启停状态 */
const ENABLE_STATUS: Record<number, { text: string; color: string }> = {
  1: { text: '启用', color: 'success' },
  0: { text: '停用', color: 'default' },
}

/**
 * 统一状态标签 —— 状态列/状态展示统一用它，保证全站视觉一致。
 * @example <StatusTag value={1} map="user" />
 */
export default function StatusTag({
  value,
  map = 'enable',
}: {
  value: number | string | null | undefined
  map?: 'user' | 'enable'
}) {
  if (value === null || value === undefined || value === '') {
    return <Tag>-</Tag>
  }
  const entry = (map === 'user' ? USER_STATUS : ENABLE_STATUS)[Number(value)]
  if (!entry) return <Tag>{String(value)}</Tag>
  return <Tag color={entry.color}>{entry.text}</Tag>
}

export { USER_STATUS, ENABLE_STATUS }
