import { lazy, Suspense, type ComponentType } from 'react'
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { Spin } from 'antd'
import { RequireAuth } from '@/router/guards'
import AdminLayout from '@/layouts/AdminLayout'

/** 懒加载页面：按需分割代码，首屏只加载布局与登录页 */
const Login = lazy(() => import('@/pages/login/Login'))
const Authorize = lazy(() => import('@/pages/oauth/Authorize'))

/** 模块路由片段的统一形状 */
interface RouteFragment {
  path: string
  lazy: () => Promise<{ default: ComponentType }>
}

/** 懒加载路由的统一 loading 态 */
function RouteLoading() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
      <Spin size="large" />
    </div>
  )
}

/** 将模块路由片段转为路由 children（element 风格 + Suspense） */
function toRouteChildren(fragments: RouteFragment[]) {
  return fragments.map((f) => {
    const Component = lazy(f.lazy)
    return {
      path: f.path,
      element: (
        <Suspense fallback={<RouteLoading />}>
          <Component />
        </Suspense>
      ),
    }
  })
}

/**
 * 路由表（管理端单门户）：
 * - /login 统一登录页（无需登录态；P2 的 OIDC 授权页将另挂 /oauth 独立路由）
 * - /      管理端 AdminLayout（主账号/组织；P2/P3 追加应用/分配/审计菜单）
 * 新增页面：在下方 fragments 追加路径与懒加载函数。
 */
export const router = createBrowserRouter([
  { path: '/login', element: <Login /> },
  // OIDC 统一登录/授权页（业务系统重定向入口，独立路由无需登录态）
  { path: '/oauth/authorize', element: <Authorize /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AdminLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/users" replace /> },
      ...toRouteChildren([
        { path: 'users', lazy: () => import('@/pages/users/UserList') },
        { path: 'orgs', lazy: () => import('@/pages/orgs/OrgTree') },
        { path: 'apps', lazy: () => import('@/pages/apps/AppList') },
        { path: 'audit/login-logs', lazy: () => import('@/pages/audit/LoginLogList') },
        { path: 'audit/op-logs', lazy: () => import('@/pages/audit/OpLogList') },
      ]),
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])
