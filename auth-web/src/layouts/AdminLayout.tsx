import { startTransition, useMemo } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Breadcrumb, Dropdown, Layout, Menu } from 'antd'
import type { MenuProps } from 'antd'
import {
  ApartmentOutlined,
  AppstoreOutlined,
  FileSearchOutlined,
  KeyOutlined,
  LogoutOutlined,
  SafetyCertificateOutlined,
  CloudServerOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useAuthStore } from '@/stores/auth'

const { Header, Sider, Content } = Layout

const ADMIN_MENUS: MenuProps['items'] = [
  {
    type: 'group', key: 'saas-platform', label: 'SaaS 平台',
    children: [{ key: '/tenants', icon: <CloudServerOutlined />, label: '租户管理' }],
  },
  {
    type: 'group',
    key: 'identity-access',
    label: '身份与访问',
    children: [
      { key: '/users', icon: <UserOutlined />, label: '主账号管理' },
      { key: '/orgs', icon: <ApartmentOutlined />, label: '组织架构' },
      { key: '/apps', icon: <AppstoreOutlined />, label: '应用管理' },
    ],
  },
  {
    type: 'group',
    key: 'security-audit',
    label: '安全与审计',
    children: [
      { key: '/audit/login-logs', icon: <KeyOutlined />, label: '登录日志' },
      { key: '/audit/op-logs', icon: <FileSearchOutlined />, label: '操作日志' },
    ],
  },
]

const PAGE_META: Record<string, { group: string; title: string }> = {
  '/tenants': { group: 'SaaS 平台', title: '租户管理' },
  '/users': { group: '身份与访问', title: '主账号管理' },
  '/orgs': { group: '身份与访问', title: '组织架构' },
  '/apps': { group: '身份与访问', title: '应用管理' },
  '/audit/login-logs': { group: '安全与审计', title: '登录日志' },
  '/audit/op-logs': { group: '安全与审计', title: '操作日志' },
}

/** SaaSGuard IAM 管理工作台。 */
export default function AdminLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, token, logout, activeTenantName } = useAuthStore()
  const pageMeta = PAGE_META[location.pathname]
  const visibleMenus = user?.roles.includes('SUPER_ADMIN')
    ? ADMIN_MENUS
    : ADMIN_MENUS?.filter((item) => item && 'key' in item && item.key !== 'saas-platform')

  const userMenu: MenuProps['items'] = useMemo(
    () => [
      {
        key: 'logout',
        icon: <LogoutOutlined />,
        label: '退出登录',
        onClick: () => {
          void fetch('/api/auth/logout', {
            method: 'POST',
            headers: { Authorization: `Bearer ${token}` },
          }).catch(() => undefined)
          logout()
          // 登录页为懒加载路由；同步点击事件内切换时用 transition，避免 React
          // “component suspended while responding to synchronous input” 整页错误。
          startTransition(() => navigate('/login', { replace: true }))
        },
      },
    ],
    [token, logout, navigate],
  )

  const displayName = user?.realName ?? user?.username ?? '未登录'

  return (
    <Layout className="admin-shell">
      <Sider
        width="clamp(220px, 18vw, 244px)"
        theme="light"
        className="admin-sider thin-scroll"
      >
        <div className="admin-brand">
          <span className="admin-brand-mark" aria-hidden="true">
            <SafetyCertificateOutlined />
          </span>
          <span className="admin-brand-copy">
            <b>SaaSGuard IAM</b>
            <small>IDENTITY FABRIC</small>
          </span>
        </div>

        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={visibleMenus}
          onClick={({ key }) => navigate(key)}
          className="admin-nav"
        />

        <div className="admin-sider-foot">
          <span className="admin-sider-foot-dot" />
          身份服务运行中
        </div>
      </Sider>

      <Layout className="admin-main">
        <Header className="admin-header">
          <div className="admin-header-start">
            <Breadcrumb
              items={[
                { title: 'SaaSGuard IAM' },
                ...(pageMeta ? [{ title: pageMeta.group }, { title: pageMeta.title }] : []),
              ]}
            />
          </div>

          <div className="admin-header-end">
            <span className="admin-environment">租户：{activeTenantName ?? user?.tenantName ?? '-'}</span>
            <span className="admin-environment">
              <span />
              身份服务在线
            </span>
            <Dropdown menu={{ items: userMenu }} placement="bottomRight">
              <button type="button" className="admin-user">
                <Avatar size={32} icon={<UserOutlined />} />
                <span>{displayName}</span>
              </button>
            </Dropdown>
          </div>
        </Header>

        <Content className="admin-content thin-scroll">
          <div key={location.pathname} className="admin-page-frame page-fade">
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  )
}
