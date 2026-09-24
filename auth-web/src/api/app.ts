import { get, post, put, del } from './request'
import type { PageResult } from './request'

/* ---------------- 应用注册 ---------------- */

/** 应用角色标签 */
export interface AppRole {
  roleCode: string
  roleName: string
}

/** 应用（client_secret 永不返回） */
export interface AppClientVO {
  id: number
  clientId: string
  appName: string
  redirectUri: string
  accessTokenValidity: number
  consentRequired: boolean
  status: number
  adminUserId: number | null
  remark: string | null
  roles: AppRole[]
  createTime: string | null
}

/** 应用表单（创建/修改） */
export interface AppClientForm {
  clientId: string
  appName: string
  redirectUri: string
  accessTokenValidity: number
  consentRequired: boolean
  adminUserId?: number | null
  remark?: string
  roles: AppRole[]
}

/** 创建/重置密钥响应（明文密钥仅此一次） */
export interface AppSecretVO {
  client: AppClientVO
  clientSecret: string
}

export function pageApps(params: { pageNum: number; pageSize: number }) {
  return get<PageResult<AppClientVO>>('/apps', params)
}

export function getApp(clientId: string) {
  return get<AppClientVO>(`/apps/${clientId}`)
}

export function createApp(form: AppClientForm) {
  return post<AppSecretVO>('/apps', form)
}

export function updateApp(clientId: string, form: AppClientForm) {
  return put<void>(`/apps/${clientId}`, form)
}

export function changeAppStatus(clientId: string, status: number) {
  return put<void>(`/apps/${clientId}/status/${status}`)
}

export function resetAppSecret(clientId: string) {
  return put<AppSecretVO>(`/apps/${clientId}/secret`)
}

/* ---------------- 应用分配 ---------------- */

/** 分配行（业务系统管理端用户列表） */
export interface AssignVO {
  /** 兼容 PageTable 行键约束（实际唯一键为 assignId） */
  id?: number
  assignId: number
  userId: number
  employeeNo: string
  username: string
  realName: string
  orgName: string | null
  position: string | null
  globalStatus: number
  appStatus: number
  roles: string[]
  assignTime: string | null
}

export function pageAssignments(
  clientId: string,
  params: { pageNum: number; pageSize: number; keyword?: string; status?: number },
) {
  return get<PageResult<AssignVO>>(`/apps/${clientId}/assignments`, params)
}

export function assignUsers(clientId: string, userIds: number[]) {
  return post<void>(`/apps/${clientId}/assignments`, { userIds })
}

export function revokeAssignment(clientId: string, userId: number) {
  return del<void>(`/apps/${clientId}/assignments/${userId}`)
}

export function setAssignStatus(clientId: string, userId: number, enabled: boolean) {
  return put<void>(`/apps/${clientId}/assignments/${userId}/status/${enabled}`)
}

export function grantAssignRole(clientId: string, userId: number, roleCode: string) {
  return put<void>(`/apps/${clientId}/assignments/${userId}/roles/${roleCode}`)
}

export function revokeAssignRole(clientId: string, userId: number, roleCode: string) {
  return del<void>(`/apps/${clientId}/assignments/${userId}/roles/${roleCode}`)
}

/* ---------------- 审计日志 ---------------- */

export interface LoginLog {
  id: number
  userId: number | null
  username: string
  loginType: string
  clientId: string | null
  success: number
  failReason: string | null
  ip: string | null
  userAgent: string | null
  createTime: string
}

export interface OpLog {
  id: number
  userId: number | null
  username: string | null
  method: string
  uri: string
  statusCode: number
  detail: string | null
  ip: string | null
  costMs: number | null
  createTime: string
}

export function pageLoginLogs(params: {
  pageNum: number
  pageSize: number
  username?: string
  loginType?: string
  success?: number
}) {
  return get<PageResult<LoginLog>>('/audit/login-logs', params)
}

export function pageOpLogs(params: {
  pageNum: number
  pageSize: number
  username?: string
  uri?: string
}) {
  return get<PageResult<OpLog>>('/audit/op-logs', params)
}

/* ---------------- OIDC 授权（统一登录页用） ---------------- */

export interface AuthorizeParams {
  clientId: string
  redirectUri: string
  responseType: string
  scope?: string
  state?: string
  nonce?: string
  codeChallenge?: string
  codeChallengeMethod?: string
  consent?: boolean
}

export interface AuthorizeResult {
  needConsent: boolean
  appName: string | null
  redirectUri: string | null
}

/** 授权确认（需已登录，Authorization 自动附带管理端 JWT） */
export function oidcAuthorize(params: AuthorizeParams) {
  return post<AuthorizeResult>('/oauth/authorize', params)
}

/* ---------------- 用户分配摘要（主账号列表页） ---------------- */

/** 用户×应用分配摘要 */
export interface UserAppSummary {
  clientId: string
  appName: string
  appStatus: number
  roles: string[]
}

/** 按用户 ID 批量查已分配应用 + 角色标签 */
export function fetchUserSummaries(userIds: number[]) {
  return get<Record<string, UserAppSummary[]>>(
    '/apps/user-summaries',
    { userIds: userIds.join(',') },
  )
}
