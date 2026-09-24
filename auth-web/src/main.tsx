import ReactDOM from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import { router } from '@/router'
import '@/styles/global.css'

dayjs.locale('zh-cn')

/** TanStack Query 全局客户端：写操作成功后统一失效重查（页面里再按需 invalidate） */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <ConfigProvider
    locale={zhCN}
    theme={{
      token: {
        colorPrimary: '#2563eb',
        colorInfo: '#2563eb',
        colorLink: '#2563eb',
        borderRadius: 8,
        fontSize: 14,
        colorBgLayout: '#f0f3f9',
        colorText: '#1e293b',
        colorTextSecondary: '#64748b',
      },
      components: {
        Table: { headerBg: '#f8fafc', headerColor: '#475569', rowHoverBg: '#f1f5fd' },
        Card: { borderRadiusLG: 14 },
        Menu: { itemBorderRadius: 9, itemMarginInline: 6, itemSelectedBg: 'rgba(59,130,246,0.22)', itemSelectedColor: '#fff' },
        Modal: { borderRadiusLG: 14 },
      },
    }}
  >
    <App>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </App>
  </ConfigProvider>,
)
