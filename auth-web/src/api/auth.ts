import { post } from './request'
import type { LoginUser } from '@/stores/auth'

/** 登录响应（后端 LoginVO：token + 用户 + 角色） */
export type AuthResp = {
  token: string
  tokenType: string
} & LoginUser

/** 管理端登录（主账号 idm_user，用户名或工号 + 密码；loginType/clientId 供登录日志审计） */
export function login(body: { username: string; password: string; loginType?: string; clientId?: string }) {
  return post<AuthResp>('/auth/login', body)
}

/** 退出登录（后端将 JWT 加入 Redis 黑名单） */
export function logout() {
  return post<void>('/auth/logout')
}
