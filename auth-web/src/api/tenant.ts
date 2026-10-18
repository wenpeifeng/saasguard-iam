import { post, put } from './request'

/** SaaS 租户列表项。 */
export interface TenantVO {
  id: number
  code: string
  name: string
  domain?: string
  planCode: 'FREE' | 'STANDARD' | 'ENTERPRISE'
  status: number
  logoUrl?: string
  primaryColor: string
  locale: string
  timezone: string
  maxUsers: number
  expiresAt?: string
  createTime: string
}

/** 新建租户表单。 */
export interface TenantForm {
  code: string
  name: string
  domain?: string
  planCode: TenantVO['planCode']
  logoUrl?: string
  primaryColor: string
  locale: string
  timezone: string
  maxUsers: number
  expiresAt?: string
  adminUsername: string
  adminRealName: string
  adminPassword: string
}

/** 租户创建后可维护字段。 */
export type TenantUpdateForm = Omit<TenantForm, 'code' | 'adminUsername' | 'adminRealName' | 'adminPassword'>

/** 创建租户并初始化租户管理员。 */
export function createTenant(body: TenantForm) {
  return post<number>('/tenants', body)
}

/** 修改租户品牌、套餐与容量配置。 */
export function updateTenant(id: number, body: TenantUpdateForm) {
  return put<void>(`/tenants/${id}`, body)
}

/** 启用或停用租户。 */
export function changeTenantStatus(id: number, status: number) {
  return put<void>(`/tenants/${id}/status/${status}`)
}
