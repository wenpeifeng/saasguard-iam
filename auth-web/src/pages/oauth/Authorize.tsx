import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Button, Form, Input, Space, Typography } from 'antd'
import { LockOutlined, SafetyCertificateOutlined, UserOutlined } from '@ant-design/icons'
import { login } from '@/api/auth'
import { oidcAuthorize } from '@/api/app'
import { useAuthStore } from '@/stores/auth'

const { Text } = Typography

/** 深色玻璃输入框样式（与登录页同一视觉语言） */
const glassInput: React.CSSProperties = {
  background: 'rgba(255,255,255,0.08)',
  borderColor: 'rgba(255,255,255,0.16)',
  borderRadius: 10,
  color: '#fff',
}

/**
 * OIDC 统一登录/授权页（业务系统重定向入口，独立路由无需登录态）。
 * 视觉与管理端登录页一致：深色科技感 + 玻璃拟态卡片。
 *
 * 流程（SPA 版授权码 + PKCE，语义对标 RFC 6749 §4.1）：
 * 1. 业务系统把浏览器重定向到 /oauth/authorize?client_id=...&redirect_uri=...&...；
 * 2. 未登录 → 展示统一登录表单（loginType=OIDC，登录日志单独留痕）；
 * 3. 已登录 → 调 POST /oauth/authorize：应用要求授权确认时先展示 consent 页；
 * 4. 拿到 redirectUri（带 code/state）后浏览器跳转回业务系统回调地址。
 */
export default function Authorize() {
  const [params] = useSearchParams()
  const { token, user, setAuth, logout } = useAuthStore()
  const [form] = Form.useForm<{ username: string; password: string }>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [needConsent, setNeedConsent] = useState(false)
  const [appName, setAppName] = useState('')

  const authorizeParams = useMemo(
    () => ({
      clientId: params.get('client_id') ?? '',
      redirectUri: params.get('redirect_uri') ?? '',
      responseType: params.get('response_type') ?? 'code',
      scope: params.get('scope') ?? 'openid profile',
      state: params.get('state') ?? undefined,
      nonce: params.get('nonce') ?? undefined,
      codeChallenge: params.get('code_challenge') ?? undefined,
      codeChallengeMethod: params.get('code_challenge_method') ?? undefined,
    }),
    [params],
  )

  const callAuthorize = async (consent: boolean) => {
    setError(null)
    const resp = await oidcAuthorize({ ...authorizeParams, consent })
    if (resp.needConsent) {
      setNeedConsent(true)
      setAppName(resp.appName ?? '')
      return
    }
    window.location.href = resp.redirectUri ?? '/'
  }

  const onLogin = async (values: { username: string; password: string }) => {
    if (loading) return
    setLoading(true)
    setError(null)
    try {
      const resp = await login({ ...values, loginType: 'OIDC', clientId: authorizeParams.clientId })
      setAuth(resp.token, {
        userId: resp.userId,
        username: resp.username,
        realName: resp.realName,
        roles: resp.roles ?? [],
      })
      await callAuthorize(false)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoading(false)
    }
  }

  const onConsent = async () => {
    setLoading(true)
    try {
      await callAuthorize(true)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      style={{
        height: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'radial-gradient(ellipse at 30% 20%, #16294f 0%, #0b1526 60%)',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* 网格背景 + 光斑 */}
      <div
        className="login-grid"
        style={{
          position: 'absolute', inset: 0,
          backgroundImage:
            'linear-gradient(rgba(255,255,255,0.04) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.04) 1px, transparent 1px)',
          backgroundSize: '60px 60px',
          maskImage: 'radial-gradient(ellipse at 50% 40%, #000 30%, transparent 75%)',
          WebkitMaskImage: 'radial-gradient(ellipse at 50% 40%, #000 30%, transparent 75%)',
        }}
      />
      <div
        className="login-orb"
        style={{
          position: 'absolute', width: 460, height: 460, left: '8%', top: -140, borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(59,130,246,0.25) 0%, transparent 65%)',
        }}
      />

      <div
        className="login-card-enter"
        style={{
          width: 430,
          padding: '40px 38px 34px',
          borderRadius: 20,
          background: 'rgba(255,255,255,0.06)',
          border: '1px solid rgba(255,255,255,0.12)',
          backdropFilter: 'blur(20px)',
          WebkitBackdropFilter: 'blur(20px)',
          boxShadow: '0 24px 64px rgba(0,0,0,0.4)',
          position: 'relative',
        }}
      >
        <Space align="center" size={10} style={{ marginBottom: 22 }}>
          <div
            className="brand-glow"
            style={{
              width: 40, height: 40, borderRadius: 12,
              background: 'linear-gradient(135deg, #3b82f6, #22d3ee)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 21, color: '#fff',
            }}
          >
            <SafetyCertificateOutlined />
          </div>
          <div>
            <div style={{ color: '#fff', fontWeight: 700, fontSize: 16 }}>统一认证中心</div>
            <div style={{ color: 'rgba(255,255,255,0.65)', fontSize: 12 }}>集团单点登录</div>
          </div>
        </Space>

        {needConsent ? (
          <>
            <Text style={{ color: 'rgba(255,255,255,0.85)', display: 'block', lineHeight: 1.8 }}>
              <b>{user?.realName ?? '已登录用户'}</b>，应用 <b>{appName || authorizeParams.clientId}</b>{' '}
              请求获取你的账号信息（姓名 / 工号 / 组织 / 角色标签）。
            </Text>
            <Space style={{ marginTop: 24 }}>
              <Button
                type="primary" size="large" loading={loading} onClick={onConsent}
                style={{ borderRadius: 10, fontWeight: 600, background: 'linear-gradient(90deg,#3b82f6,#2563eb)', border: 'none' }}
              >
                同意授权
              </Button>
              <Button size="large" onClick={() => (window.location.href = authorizeParams.redirectUri)}>
                拒绝
              </Button>
            </Space>
          </>
        ) : token ? (
          <>
            <Text style={{ color: 'rgba(255,255,255,0.85)', display: 'block', lineHeight: 1.8 }}>
              当前已登录：<b>{user?.realName ?? user?.username}</b>
              <br />
              正在向 <b style={{ color: '#7db4ff' }}>{authorizeParams.clientId}</b> 发起授权…
            </Text>
            {error && <Text type="danger" style={{ display: 'block', marginTop: 12 }}>{error}</Text>}
            <Space style={{ marginTop: 24 }}>
              <Button
                type="primary" size="large" loading={loading} onClick={() => callAuthorize(false)}
                style={{ borderRadius: 10, fontWeight: 600, background: 'linear-gradient(90deg,#3b82f6,#2563eb)', border: 'none' }}
              >
                继续授权
              </Button>
              <Button size="large" onClick={() => { logout(); window.location.reload() }}>
                切换账号
              </Button>
            </Space>
          </>
        ) : (
          <>
            <Text style={{ color: 'rgba(255,255,255,0.72)', display: 'block', marginBottom: 22, fontSize: 13 }}>
              登录以继续访问 <b style={{ color: '#7db4ff' }}>{authorizeParams.clientId || '应用'}</b>
            </Text>
            <Form form={form} layout="vertical" onFinish={onLogin}>
              <Form.Item name="username" rules={[{ required: true, message: '请输入用户名或工号' }]}>
                <Input prefix={<UserOutlined style={{ color: 'rgba(255,255,255,0.55)' }} />} placeholder="用户名 / 工号" size="large" style={glassInput} />
              </Form.Item>
              <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
                <Input.Password prefix={<LockOutlined style={{ color: 'rgba(255,255,255,0.55)' }} />} placeholder="密码" size="large" style={glassInput} />
              </Form.Item>
              {error && (
                <Text type="danger" style={{ display: 'block', marginBottom: 12 }}>{error}</Text>
              )}
              <Button
                type="primary" htmlType="submit" size="large" block loading={loading}
                style={{
                  height: 46, borderRadius: 10, fontWeight: 600,
                  background: 'linear-gradient(90deg,#3b82f6,#2563eb)',
                  boxShadow: '0 8px 24px rgba(37,99,235,0.4)', border: 'none',
                }}
              >
                登 录
              </Button>
            </Form>
          </>
        )}
      </div>
    </div>
  )
}
