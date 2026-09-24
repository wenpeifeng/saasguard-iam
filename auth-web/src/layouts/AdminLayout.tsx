import { useMemo, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Layout, Menu, Dropdown, Avatar, Space } from 'antd'
import type { MenuProps } from 'antd'
import {
  UserOutlined,
  ApartmentOutlined,
  LogoutOutlined,
  SafetyOutlined,
  AppstoreOutlined,
  FileTextOutlined,
  FileSearchOutlined,
  KeyOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons'
import { useAuthStore } from '@/stores/auth'

const { Header, Sider, Content } = Layout;

/**
 * 管理门户菜单。key 即路由路径；P2 追加"应用管理/角色标签"，
 * P3 追加"审计日志"，届时在此扩展。
 */
const ADMIN_MENUS: Required<MenuProps>['items'] = [
  { key: '/users', icon: <UserOutlined />, label: '主账号管理' },
  { key: '/orgs', icon: <ApartmentOutlined />, label: '组织架构' },
  { key: '/apps', icon: <AppstoreOutlined />, label: '应用管理' },
  {
    key: 'audit', icon: <FileTextOutlined />, label: '审计日志',
    children: [
      { key: '/audit/login-logs', icon: <KeyOutlined />, label: '登录日志' },
      { key: '/audit/op-logs', icon: <FileSearchOutlined />, label: '操作日志' },
    ],
  },
]

/** 当前路径 → 顶栏面包屑文案 */
function breadcrumb(pathname: string): string {
  const entry = ADMIN_MENUS?.find((m) => {
    if (m && 'children' in m && m.children) {
      return m.children.some((c) => c && 'key' in c && c.key === pathname)
    }
    return m && 'key' in m && m.key === pathname
  })
  const top = entry && 'label' in entry && typeof entry.label === 'string' ? entry.label : ''
  const sub = (() => {
    if (entry && 'children' in entry && entry.children) {
      const child = entry.children.find((c) => c && 'key' in c && c.key === pathname)
      return child && 'label' in child && typeof child.label === 'string' ? ` / ${child.label}` : ''
    }
    return ''
  })()
  return `认证中心${top ? ' / ' + top : ''}${sub}`
}

/**
 * 认证中心管理端布局 —— 现代浅色工作台：
 * 深蓝渐变悬浮侧边栏（圆角卡片式）+ 白色顶栏（面包屑）+ 柔灰内容区（路由切换渐入）。
 * P2 的统一登录页/授权页挂在 /login、/oauth，不经过本布局。
 */
export default function AdminLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const { user, token, logout } = useAuthStore()

  const userMenu: MenuProps['items'] = useMemo(
    () => [
      {
        key: 'logout',
        icon: <LogoutOutlined />,
        label: '退出登录',
        onClick: () => {
          // 通知后端拉黑 JWT（best-effort），本地态无条件清理
          void fetch('/api/auth/logout', {
            method: 'POST',
            headers: { Authorization: `Bearer ${token}` },
          }).catch(() => undefined)
          logout()
          navigate('/login', { replace: true })
        },
      },
    ],
    [token, logout, navigate],
  )

  return (
    <Layout style={{ height: '100vh', background: '#f0f3f9' }}>
      {/* 悬浮圆角侧边栏（内边距留出卡片效果） */}
      <div style={{ padding: '10px 0 10px 10px', height: '100%' }}>
        <Sider
          trigger={null}
          collapsible
          collapsed={collapsed}
          width={228}
          style={{
            height: '100%',
            overflow: 'auto',
            borderRadius: 16,
            background: 'linear-gradient(180deg, #101f3c 0%, #152a52 70%, #1b3563 100%)',
            boxShadow: '4px 0 24px rgba(16,31,60,0.18)',
            position: 'sticky',
            top: 10,
          }}
          className="thin-scroll"
        >
          <div
            style={{
              height: 60,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 9,
              color: '#fff',
              fontSize: 16,
              fontWeight: 650,
            }}
          >
            <Avatar
              shape="square"
              size={32}
              style={{ background: 'linear-gradient(135deg,#3b82f6,#22d3ee)', borderRadius: 9 }}
            >
              A
            </Avatar>
            {!collapsed && <span style={{ letterSpacing: 1 }}>统一认证中心</span>}
          </div>
          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={[location.pathname]}
            defaultOpenKeys={['audit']}
            items={ADMIN_MENUS}
            onClick={({ key }) => navigate(key)}
            style={{ background: 'transparent', borderInlineEnd: 'none', padding: '4px 8px' }}
          />
        </Sider>
      </div>

      <Layout style={{ background: 'transparent' }}>
        <Header
          style={{
            background: 'rgba(255,255,255,0.85)',
            backdropFilter: 'blur(12px)',
            margin: '10px 10px 0',
            padding: '0 20px',
            borderRadius: 14,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            boxShadow: '0 2px 12px rgba(16,31,60,0.06)',
          }}
        >
          <Space size={4}>
            <span
              onClick={() => setCollapsed(!collapsed)}
              style={{ cursor: 'pointer', fontSize: 16, color: '#334155' }}
            >
              {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            </span>
            <span style={{ color: '#64748b', fontSize: 13.5, marginLeft: 10 }}>
              {breadcrumb(location.pathname)}
            </span>
          </Space>

          <Space size={12}>
            <span
              style={{
                display: 'inline-flex', alignItems: 'center', gap: 6,
                fontSize: 12, fontWeight: 600, letterSpacing: 1.5,
                color: '#2563eb', background: 'rgba(37,99,235,0.08)',
                padding: '3px 10px', borderRadius: 999,
              }}
            >
              <SafetyOutlined /> AUTH CENTER
            </span>
            <Dropdown menu={{ items: userMenu }} placement="bottomRight">
              <Space style={{ cursor: 'pointer', paddingLeft: 4 }}>
                <Avatar
                  size={32}
                  style={{ background: 'linear-gradient(135deg,#3b82f6,#2563eb)' }}
                  icon={<UserOutlined />}
                />
                <span style={{ color: '#1e293b', fontWeight: 500 }}>
                  {user?.realName ?? user?.username ?? '未登录'}
                </span>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        <Content
          className="thin-scroll"
          style={{ overflow: 'auto', padding: 14 }}
        >
          <div key={location.pathname} className="page-fade" style={{ minHeight: 'calc(100vh - 88px)' }}>
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  )
}
