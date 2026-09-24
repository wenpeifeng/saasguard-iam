import { useCallback, useRef, useState } from 'react'
import { App } from 'antd'

/**
 * 提交防重 Hook —— 所有写操作（新增/修改/审核/删除）统一用它包一层。
 *
 * 保证三点：
 * 1. 提交期间 loading=true，按钮禁用（物理防连点）；
 * 2. 请求未返回前重复调用直接忽略（代码层防线，与按钮禁用双保险）；
 * 3. 成功/失败统一 message 反馈（可选关闭）。
 *
 * @param action 异步动作
 * @param options.successText 成功提示文案（传 null 则不提示）
 * @param options.onSuccess 成功回调（如刷新列表/关闭弹窗）
 *
 * @example
 * const submit = useSubmitting(async (values) => await saveUser(values), {
 *   successText: '保存成功',
 *   onSuccess: () => setOpen(false),
 * })
 * <Button loading={submit.loading} disabled={submit.loading} onClick={() => submit.run(form)}>保存</Button>
 */
export function useSubmitting<TArgs extends unknown[]>(
  action: (...args: TArgs) => Promise<unknown>,
  options: { successText?: string | null; onSuccess?: () => void } = {},
) {
  const { successText = '操作成功', onSuccess } = options
  const { message } = App.useApp()
  const [loading, setLoading] = useState(false)
  const running = useRef(false)

  const run = useCallback(
    async (...args: TArgs) => {
      // 代码层防线：上一次请求未结束，忽略本次
      if (running.current) return
      running.current = true
      setLoading(true)
      try {
        await action(...args)
        if (successText !== null) message.success(successText)
        onSuccess?.()
      } catch {
        // 错误提示由 axios 拦截器统一弹出，这里只吞掉避免未捕获告警
      } finally {
        running.current = false
        setLoading(false)
      }
    },
    [action, successText, onSuccess, message],
  )

  return { loading, run }
}
