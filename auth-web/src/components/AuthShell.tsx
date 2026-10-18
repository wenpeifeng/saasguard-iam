import type { ReactNode } from 'react'
import {
  AppstoreOutlined,
  AuditOutlined,
  CheckCircleFilled,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons'

interface AuthShellProps {
  children: ReactNode
}

/**
 * 登录与 OIDC 授权页共用的品牌外壳。
 * 左侧“身份关系轨道”表达认证中心的真实职责：身份、应用访问、审计。
 */
export default function AuthShell({ children }: AuthShellProps) {
  return (
    <div className="auth-page">
      <div className="auth-shell">
        <aside className="auth-story" aria-label="SaaSGuard IAM 能力概览">
          <div className="auth-brand">
            <div className="auth-brand-mark" aria-hidden="true">
              <SafetyCertificateOutlined />
            </div>
            <div>
              <div className="auth-brand-name">SaaSGuard IAM</div>
              <div className="auth-brand-meta">MULTI-TENANT IDENTITY FABRIC</div>
            </div>
          </div>

          <div className="auth-story-copy">
            <span className="auth-eyebrow">可扩展的 SaaS 身份与访问平台</span>
            <h1>让每个租户拥有清晰、安全的身份边界</h1>
            <p>统一管理租户、员工身份、应用访问和安全审计，为任何业务系统提供可信登录入口。</p>
          </div>

          <div className="identity-rail" aria-label="身份、访问、审计链路">
            <div className="identity-node">
              <span className="identity-node-icon"><UserOutlined /></span>
              <span><b>身份</b><small>主账号与组织</small></span>
            </div>
            <span className="identity-connector" aria-hidden="true"><i /></span>
            <div className="identity-node">
              <span className="identity-node-icon"><AppstoreOutlined /></span>
              <span><b>访问</b><small>应用分配与角色</small></span>
            </div>
            <span className="identity-connector" aria-hidden="true"><i /></span>
            <div className="identity-node">
              <span className="identity-node-icon identity-node-icon--audit"><AuditOutlined /></span>
              <span><b>审计</b><small>登录与操作留痕</small></span>
            </div>
          </div>

          <div className="auth-story-foot">
            <CheckCircleFilled />
            <span>OIDC 授权码 + PKCE · RS256 签名</span>
          </div>
        </aside>

        <main className="auth-form-panel">{children}</main>
      </div>
      <div className="auth-page-footer">SaaSGuard Identity Fabric · OpenID Connect</div>
    </div>
  )
}
