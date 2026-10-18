import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/** 登录用户信息（后端 /auth/login 返回的 LoginVO） */
export interface LoginUser {
  tenantId: number
  tenantCode: string
  tenantName: string
  userId: number
  username: string
  realName: string
  roles: string[]
}

/**
 * 登录态全局 store（zustand + localStorage 持久化）。
 * token 供 axios 拦截器统一读取（见 api/request.ts）。
 * 认证中心管理端自身登录态与 P2 的 OIDC access token 是两套令牌体系，勿混用。
 */
interface AuthState {
  token: string | null
  user: LoginUser | null
  activeTenantId: number | null
  activeTenantCode: string | null
  activeTenantName: string | null
  /** 登录成功后写入 */
  setAuth: (token: string, user: LoginUser) => void
  /** 平台超管切换租户数据视角 */
  switchTenant: (tenantId: number, tenantCode: string, tenantName: string) => void
  /** 退出登录 / 401 强制登出 */
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      activeTenantId: null,
      activeTenantCode: null,
      activeTenantName: null,
      setAuth: (token, user) => set({
        token, user,
        activeTenantId: user.tenantId,
        activeTenantCode: user.tenantCode,
        activeTenantName: user.tenantName,
      }),
      switchTenant: (tenantId, tenantCode, tenantName) => set({
        activeTenantId: tenantId,
        activeTenantCode: tenantCode,
        activeTenantName: tenantName,
      }),
      logout: () => set({
        token: null, user: null,
        activeTenantId: null, activeTenantCode: null, activeTenantName: null,
      }),
    }),
    { name: 'saasguard-iam' },
  ),
)
