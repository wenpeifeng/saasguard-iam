import { get, post, put, del } from './request'
import type { PageResult } from './request'

/* ---------------- 组织 ---------------- */

/** 组织树节点（后端 OrgTreeNode） */
export interface OrgTreeNode {
  id: number
  code: string
  name: string
  parentId: number | null
  sort: number
  status: number
  children?: OrgTreeNode[]
}

/** 组织表单 */
export interface OrgForm {
  code: string
  name: string
  parentId: number | null
  sort?: number
  status?: number
}

/** 组织树 */
export function fetchOrgTree() {
  return get<OrgTreeNode[]>('/idm/orgs/tree')
}

/** 新建组织 */
export function createOrg(form: OrgForm) {
  return post<number>('/idm/orgs', form)
}

/** 修改组织 */
export function updateOrg(id: number, form: OrgForm) {
  return put<void>(`/idm/orgs/${id}`, form)
}

/** 删除组织 */
export function deleteOrg(id: number) {
  return del<void>(`/idm/orgs/${id}`)
}

/* ---------------- 主账号 ---------------- */

/** 全局状态：1-在职 2-离职（终态）3-全局停用 */
export const USER_STATUS: Record<number, { text: string; color: string }> = {
  1: { text: '在职', color: 'success' },
  2: { text: '离职', color: 'default' },
  3: { text: '全局停用', color: 'warning' },
}

/** 主账号（后端 UserVO） */
export interface UserVO {
  id: number
  employeeNo: string
  username: string
  realName: string
  orgId: number | null
  orgName: string
  position: string
  email: string
  phone: string
  status: number
  entryDate: string | null
  leaveDate: string | null
  createTime: string | null
}

/** 建号/改档表单（后端 UserForm，工号不可变） */
export interface UserForm {
  employeeNo: string
  realName: string
  orgId: number | null
  position?: string
  email?: string
  phone?: string
  password?: string
  entryDate?: string
}

/** 主账号分页查询 */
export function pageUsers(params: {
  pageNum: number
  pageSize: number
  keyword?: string
  orgId?: number
  status?: number
}) {
  return get<PageResult<UserVO>>('/idm/users', params as Record<string, unknown>)
}

/** 入职建号 */
export function createUser(form: UserForm) {
  return post<number>('/idm/users', form)
}

/** 修改基本信息 */
export function updateUser(id: number, form: UserForm) {
  return put<void>(`/idm/users/${id}`, form)
}

/** 转岗（改组织，事件留痕） */
export function transferUser(id: number, targetOrgId: number, reason?: string) {
  return put<void>(`/idm/users/${id}/transfer`, { targetOrgId, reason })
}

/** 离职（终态，一键全系统失效） */
export function leaveUser(id: number) {
  return put<void>(`/idm/users/${id}/leave`)
}

/** 全局状态变更（在职=1 ⇄ 全局停用=3） */
export function changeUserStatus(id: number, status: number) {
  return put<void>(`/idm/users/${id}/status/${status}`)
}

/** 重置口令 */
export function resetUserPassword(id: number, password: string) {
  return put<void>(`/idm/users/${id}/password`, { password })
}
