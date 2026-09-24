import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { Button, Checkbox, Form, Input } from 'antd'
import { UserOutlined, LockOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

interface LoginForm {
  username: string
  password: string
}

/** 记住账号的 localStorage key */
const REMEMBER_KEY = 'auth-center-login-username'

/**
 * 管理端登录页 —— 深色科技感分屏布局：
 * 左侧仅保留品牌标识（Logo + 标题 + 一行副标题），右侧玻璃拟态登录卡。
 * 窄屏（<768px）自动隐藏品牌区。OIDC 统一登录/授权页复用同一视觉语言。
 */
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
        userId: resp.userId,
        username: resp.username,
        realName: resp.realName,
        roles: resp.roles ?? [],
      })
      const from = (location.state as { from?: string } | null)?.from
      navigate(from ?? '/', { replace: true })
    } catch {
      // 错误提示由 axios 拦截器统一弹出
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ display: 'flex', height: '100vh', background: '#0b1526' }}>
      {/* 品牌区（窄屏隐藏）：只留标识，不堆文案 */}
      <div
        className="hidden md:flex"
        style={{
          width: '42%',
          position: 'relative',
          overflow: 'hidden',
          background: 'linear-gradient(150deg, #0b1526 0%, #122650 45%, #1d3a8f 100%)',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {/* 网格背景 */}
        <div
          className="login-grid"
          style={{
            position: 'absolute',
            inset: 0,
            backgroundImage:
              'linear-gradient(rgba(255,255,255,0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.05) 1px, transparent 1px)',
            backgroundSize: '60px 60px',
            maskImage: 'radial-gradient(ellipse at 50% 45%, #000 30%, transparent 75%)',
            WebkitMaskImage: 'radial-gradient(ellipse at 50% 45%, #000 30%, transparent 75%)',
          }}
        />
        {/* 漂浮光斑 */}
        <div
          className="login-orb"
          style={{
            position: 'absolute',
            width: 420, height: 420, left: -140, top: '18%', borderRadius: '50%',
            background: 'radial-gradient(circle, rgba(59,130,246,0.26) 0%, transparent 65%)',
          }}
        />
        <div
          className="login-orb"
          style={{
            position: 'absolute',
            width: 360, height: 360, right: -110, bottom: '12%', borderRadius: '50%',
            background: 'radial-gradient(circle, rgba(34,211,238,0.16) 0%, transparent 65%)',
            animationDelay: '-6s',
          }}
        />

        <div style={{ position: 'relative', textAlign: 'center' }}>
          <div
            className="brand-glow"
            style={{
              width: 84, height: 84, borderRadius: 22,
              background: 'linear-gradient(135deg, #3b82f6, #22d3ee)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 42, color: '#fff', margin: '0 auto 28px',
            }}
          >
            <SafetyCertificateOutlined />
          </div>
          <div style={{ color: '#fff', fontSize: 30, fontWeight: 700, letterSpacing: 2 }}>
            统一认证中心
          </div>
          <div style={{ color: 'rgba(255,255,255,0.55)', fontSize: 13, letterSpacing: 4, marginTop: 12 }}>
            IDENTITY · ACCESS · AUDIT
          </div>
        </div>
      </div>

      {/* 登录区 */}
      <div
        style={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'radial-gradient(ellipse at 70% 20%, #16294f 0%, #0b1526 60%)',
        }}
      >
        <div
          className="login-card-enter"
          style={{
            width: 400,
            padding: '44px 40px 36px',
            borderRadius: 20,
            background: 'rgba(255,255,255,0.06)',
            border: '1px solid rgba(255,255,255,0.12)',
            backdropFilter: 'blur(20px)',
            WebkitBackdropFilter: 'blur(20px)',
            boxShadow: '0 24px 64px rgba(0,0,0,0.4)',
          }}
        >
          <h2 style={{ color: '#fff', margin: '0 0 4px', fontSize: 22 }}>管理端登录</h2>
          <p style={{ color: 'rgba(255,255,255,0.72)', margin: '0 0 28px', fontSize: 13 }}>
            使用主账号（登录名或工号）登录
          </p>
          <Form<LoginForm>
            form={form}
            layout="vertical"
            initialValues={{ username: localStorage.getItem(REMEMBER_KEY) ?? '' }}
            onFinish={onFinish}
          >
            <Form.Item
              name="username"
              rules={[{ required: true, message: '请输入用户名或工号' }]}
            >
              <Input
                prefix={<UserOutlined style={{ color: 'rgba(255,255,255,0.55)' }} />}
                placeholder="用户名 / 工号"
                size="large"
                style={glassInput}
              />
            </Form.Item>
            <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password
                prefix={<LockOutlined style={{ color: 'rgba(255,255,255,0.55)' }} />}
                placeholder="密码"
                size="large"
                style={glassInput}
              />
            </Form.Item>
            <Form.Item style={{ marginBottom: 14 }}>
              <Checkbox
                checked={remember}
                onChange={(e) => setRemember(e.target.checked)}
                style={{ color: 'rgba(255,255,255,0.72)' }}
              >
                记住账号
              </Checkbox>
            </Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              size="large"
              block
              loading={loading}
              style={{
                height: 46,
                borderRadius: 10,
                fontWeight: 600,
                background: 'linear-gradient(90deg, #3b82f6, #2563eb)',
                boxShadow: '0 8px 24px rgba(37,99,235,0.4)',
                border: 'none',
              }}
            >
              登 录
            </Button>
          </Form>
        </div>
      </div>
    </div>
  )
}

/** 深色玻璃输入框样式 */
const glassInput: React.CSSProperties = {
  background: 'rgba(255,255,255,0.08)',
  borderColor: 'rgba(255,255,255,0.16)',
  borderRadius: 10,
  color: '#fff',
}
