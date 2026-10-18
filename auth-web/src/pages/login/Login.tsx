import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Button, Checkbox, Form, Input } from 'antd'
import { LockOutlined, SafetyCertificateOutlined, UserOutlined } from '@ant-design/icons'
import AuthShell from '@/components/AuthShell'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

interface LoginForm {
  tenantCode: string
  username: string
  password: string
}

const REMEMBER_KEY = 'saasguard-login-username'

/** 管理端登录页。 */
export default function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const setAuth = useAuthStore((s) => s.setAuth)
  const [loading, setLoading] = useState(false)
  const [remember, setRemember] = useState(() => !!localStorage.getItem(REMEMBER_KEY))
  const [form] = Form.useForm<LoginForm>()

  const onFinish = async (values: LoginForm) => {
    if (loading) return
    setLoading(true)
    try {
      const resp = await login(values)
      if (remember) {
        localStorage.setItem(REMEMBER_KEY, values.username)
      } else {
        localStorage.removeItem(REMEMBER_KEY)
      }
      setAuth(resp.token, {
        tenantId: resp.tenantId,
        tenantCode: resp.tenantCode,
        tenantName: resp.tenantName,
        userId: resp.userId,
        username: resp.username,
        realName: resp.realName,
        roles: resp.roles ?? [],
      })
      const from = (location.state as { from?: string } | null)?.from
      navigate(from ?? '/', { replace: true })
    } catch {
      // 错误提示由 axios 拦截器统一处理
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthShell>
      <div className="auth-form-wrap auth-card-enter">
        <div className="auth-mobile-brand">
          <span><SafetyCertificateOutlined /></span>
          SaaSGuard IAM
        </div>
        <div className="auth-form-kicker">管理控制台</div>
        <h2 className="auth-form-title">进入身份控制台</h2>
        <p className="auth-form-description">选择租户后使用统一主账号登录，管理身份、应用访问与审计。</p>

        <Form<LoginForm>
          form={form}
          layout="vertical"
          requiredMark={false}
          initialValues={{ tenantCode: 'demo', username: localStorage.getItem(REMEMBER_KEY) ?? '' }}
          onFinish={onFinish}
          className="auth-form"
        >
          <Form.Item
            name="tenantCode"
            label="租户代码"
            rules={[{ required: true, message: '请输入租户代码' }]}
          >
            <Input
              prefix={<SafetyCertificateOutlined />}
              placeholder="例如 demo"
              size="large"
              autoComplete="organization"
            />
          </Form.Item>
          <Form.Item
            name="username"
            label="账号"
            rules={[{ required: true, message: '请输入登录名或工号' }]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="请输入登录名或工号"
              size="large"
              autoComplete="username"
              autoFocus
            />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="请输入密码"
              size="large"
              autoComplete="current-password"
            />
          </Form.Item>

          <div className="auth-form-options">
            <Checkbox checked={remember} onChange={(e) => setRemember(e.target.checked)}>
              记住账号
            </Checkbox>
            <span>登录问题请联系系统管理员</span>
          </div>

          <Button type="primary" htmlType="submit" size="large" block loading={loading}>
            登录
          </Button>
        </Form>

        <div className="auth-security-note">
          <SafetyCertificateOutlined />
          <span>仅限授权用户使用，登录行为将记录到安全审计日志。</span>
        </div>
      </div>
    </AuthShell>
  )
}
