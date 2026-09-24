import axios, { type AxiosResponse } from 'axios'
import { message } from 'antd'
import { useAuthStore } from '@/stores/auth'

/** 后端统一响应结构 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 分页响应结构 */
export interface PageResult<T = unknown> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

/**
 * Axios 实例。
 * - baseURL /api：走 Vite 代理到认证中心后端 8082（见 vite.config.ts）；
 * - 请求拦截：自动附带 JWT；
 * - 响应拦截：拆包 Result —— code===0 直接返回 data，否则统一报错并 reject，
 *   页面代码里拿到的永远是业务数据本身，不用层层判断 code。
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response: AxiosResponse<ApiResult>) => {
    const result = response.data
    if (result.code === 0) {
      return result.data as never
    }
    // 业务错误：提示后拒绝，页面可 catch 做针对性处理
    message.error(result.message || '操作失败')
    return Promise.reject(new Error(result.message))
  },
  (error) => {
    if (error.response?.status === 401) {
      // 登录态失效：清理本地态并跳登录页
      useAuthStore.getState().logout()
      window.location.href = '/login'
    } else {
      const msg = error.response?.data?.message || error.message || '网络异常'
      message.error(msg)
    }
    return Promise.reject(error)
  },
)

/** GET 快捷方法（自动携带类型） */
export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return request.get(url, { params }) as Promise<T>
}

/** POST 快捷方法 */
export function post<T>(url: string, body?: unknown): Promise<T> {
  return request.post(url, body) as Promise<T>
}

/** PUT 快捷方法 */
export function put<T>(url: string, body?: unknown): Promise<T> {
  return request.put(url, body) as Promise<T>
}

/** DELETE 快捷方法 */
export function del<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return request.delete(url, { params }) as Promise<T>
}
