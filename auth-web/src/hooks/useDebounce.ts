import { useEffect, useState } from 'react'

/**
 * 防抖 Hook —— 搜索框输入防抖的标准写法。
 *
 * @param value 原始值（如输入框内容）
 * @param delay 防抖毫秒数，默认 300
 * @returns 防抖后的值（停止输入 delay 毫秒后才更新，用于触发查询）
 *
 * @example
 * const [kw, setKw] = useState('')
 * const debouncedKw = useDebounce(kw)
 * useQuery({ queryKey: ['users', debouncedKw], ... }) // 停止输入后才发请求
 */
export function useDebounce<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    // 值再次变化时取消上一次定时器，实现"只认最后一次"
    return () => clearTimeout(timer)
  }, [value, delay])

  return debounced
}
