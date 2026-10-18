import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Alert, Button, Form, Input, Space } from 'antd'
import {
  ApartmentOutlined,
  AppstoreOutlined,
  IdcardOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons'
import AuthShell from '@/components/AuthShell'
import { login } from '@/api/auth'
import { oidcAuthorize } from '@/api/app'
import { useAuthStore } from '@/stores/auth'

/** OIDC 统一登录与授权页。 */
export default function Authorize() {
  const [params] = useSearchParams()
  const { token, user, setAuth, logout } = useAuthStore()
  const [form] = Form.useForm<{ username: string; password: string }>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [needConsent, setNeedConsent] = useState(false)
  const [appName, setAppName] = useState('')
  const requestedTenantCode = params.get('tenant_code') ?? 'demo'

  const authorizeParams = useMemo(
    () => ({
      clientId: params.get('client_id') ?? '',
      tenantCode: requestedTenantCode,
      redirectUri: params.get('redirect_uri') ?? '',
      responseType: params.get('response_type') ?? 'code',
      scope: params.get('scope') ?? 'openid profile',
      state: params.get('state') ?? undefined,
      nonce: params.get('nonce') ?? undefined,
      codeChallenge: params.get('code_challenge') ?? undefined,
      codeChallengeMethod: params.get('code_challenge_method') ?? undefined,
    }),
    [params, requestedTenantCode],
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
      const resp = await login({
        ...values,
        tenantCode: authorizeParams.tenantCode,
        loginType: 'OIDC',
        clientId: authorizeParams.clientId,
      })
      setAuth(resp.token, {
        tenantId: resp.tenantId,
        tenantCode: resp.tenantCode,
        tenantName: resp.tenantName,
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

  const clientName = appName || authorizeParams.clientId || '业务应用'

  return (
    <AuthShell>
      <div className="auth-form-wrap auth-card-enter">
        <div className="auth-mobile-brand">
          <span><SafetyCertificateOutlined /></span>
          SaaSGuard IAM
        </div>
        <div className="auth-form-kicker">统一登录</div>
        <h2 className="auth-form-title">{needConsent ? '确认应用访问' : '验证你的身份'}</h2>
        <div className="auth-client-chip"><AppstoreOutlined /> {clientName}</div>

        {needConsent ? (
          <div className="consent-panel">
            <p><strong>{user?.realName ?? '已登录用户'}</strong>，该应用希望读取以下账号信息：</p>
            <div className="consent-list">
              <div><IdcardOutlined /><span><b>基本身份</b><small>姓名、工号与登录名</small></span></div>
              <div><ApartmentOutlined /><span><b>组织信息</b><small>所属组织与岗位信息</small></span></div>
              <div><SafetyCertificateOutlined /><span><b>应用角色</b><small>该应用的访问角色标签</small></span></div>
            </div>
            {error && <Alert type="error" showIcon message={error} />}
            <Space size={12} className="auth-action-row">
              <Button type="primary" size="large" loading={loading} onClick={onConsent}>允许访问</Button>
              <Button size="large" onClick={() => (window.location.href = authorizeParams.redirectUri)}>拒绝</Button>
            </Space>
          </div>
        ) : token ? (
          <div className="consent-panel">
            <p>当前登录账号为 <strong>{user?.realName ?? user?.username}</strong>。</p>
            <div className="auth-status-box">
              <SafetyCertificateOutlined />
              <span>身份验证已完成，可以继续访问 <b>{clientName}</b>。</span>
            </div>
            {error && <Alert type="error" showIcon message={error} />}
            <Space size={12} className="auth-action-row">
              <Button type="primary" size="large" loading={loading} onClick={() => callAuthorize(false)}>继续访问</Button>
              <Button size="large" onClick={() => { logout(); window.location.reload() }}>切换账号</Button>
            </Space>
          </div>
        ) : (
          <>
            <p className="auth-form-description">登录后继续访问该应用。认证中心不会向应用提供你的密码。</p>
            <Form form={form} layout="vertical" requiredMark={false} onFinish={onLogin} className="auth-form">
              <Form.Item name="username" label="账号" rules={[{ required: true, message: '请输入登录名或工号' }]}>
                <Input prefix={<UserOutlined />} placeholder="请输入登录名或工号" size="large" autoComplete="username" autoFocus />
              </Form.Item>
              <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
                <Input.Password prefix={<LockOutlined />} placeholder="请输入密码" size="large" autoComplete="current-password" />
              </Form.Item>
              {error && <Alert type="error" showIcon message={error} className="auth-error" />}
              <Button type="primary" htmlType="submit" size="large" block loading={loading}>登录并继续</Button>
            </Form>
          </>
        )}

        <div className="auth-security-note">
          <LockOutlined />
          <span>安全连接由 OIDC 授权码与 PKCE 保护。</span>
        </div>
      </div>
    </AuthShell>
  )
}
