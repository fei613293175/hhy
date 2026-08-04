import type { AdminContract } from '@hhy/api-client'
import { AdminSecurityApi, adminSecurityApi, type AdminCallOptions } from './adminSecurity'
import { AdminSession, adminSession } from './adminSession'

type Operation<Id extends keyof AdminContract.operations> = AdminContract.operations[Id]
type Json<Value> = Value extends { content: { 'application/json': infer Body } } ? Body : never
type Success<Op> = Op extends { responses: { 200: infer Response } } ? Json<Response> : never
type Data<Op> = Success<Op> extends { data: infer Value } ? Value : never
type Body<Op> = Op extends { requestBody: { content: { 'application/json': infer Value } } } ? Value : never

export type AdminPropResource = AdminContract.components['schemas']['PropResource']
export type AdminPropPage = Data<Operation<'adminPropsGetProps'>>
export type AdminPropQuery = { page?: number; pageSize?: number; cursor?: string; status?: string; keyword?: string; sort?: string }
export type AdminPropPatch = Body<Operation<'adminPropsPatchPropsById'>>
export type AdminPropSlotCreate = Body<Operation<'adminPropsPostHeadlineSlots'>>

const SAFE_ID = /^[A-Za-z0-9_-]{1,64}$/
const SORTS = new Set(['createdAt:asc', 'createdAt:desc', 'updatedAt:asc', 'updatedAt:desc', 'name:asc', 'quantity:asc', 'expiresAt:asc'])
function safeId(value: string) { if (!SAFE_ID.test(value)) throw new RangeError('道具资源标识格式无效'); return encodeURIComponent(value) }
function query(parameters: AdminPropQuery) {
  if (parameters.page !== undefined && parameters.page < 1) throw new RangeError('页码必须大于等于 1')
  if (parameters.pageSize !== undefined && (parameters.pageSize < 1 || parameters.pageSize > 100)) throw new RangeError('每页数量必须在 1 到 100 之间')
  if (parameters.cursor && parameters.page !== undefined && parameters.page !== 1) throw new RangeError('页码和游标不能同时用于翻页')
  if (parameters.sort && !SORTS.has(parameters.sort)) throw new RangeError('道具排序方式无效')
  const value = new URLSearchParams()
  Object.entries(parameters).forEach(([key, item]) => { if (item !== undefined && item !== '') value.set(key, String(item)) })
  return value.size ? `?${value}` : ''
}

export class AdminPropsApi {
  constructor(private readonly transport: AdminSecurityApi = adminSecurityApi, private readonly session: AdminSession = adminSession) {}
  props(parameters: AdminPropQuery = {}, options: AdminCallOptions = {}) { return this.list<Operation<'adminPropsGetProps'>>('/admin-api/v1/props', parameters, options) }
  headlineSlots(parameters: AdminPropQuery = {}, options: AdminCallOptions = {}) { return this.list<Operation<'adminPropsGetHeadlineSlots'>>('/admin-api/v1/headline-slots', parameters, options) }
  executions(parameters: AdminPropQuery = {}, options: AdminCallOptions = {}) { return this.list<Operation<'adminPropsGetPropExecutions'>>('/admin-api/v1/prop-executions', parameters, options) }
  patch(id: string, body: AdminPropPatch, key: string, options: AdminCallOptions = {}) { return this.write<Operation<'adminPropsPatchPropsById'>>(`/admin-api/v1/props/${safeId(id)}`, 'PATCH', body, key, options) }
  createHeadlineSlot(body: AdminPropSlotCreate, key: string, options: AdminCallOptions = {}) { return this.write<Operation<'adminPropsPostHeadlineSlots'>>('/admin-api/v1/headline-slots', 'POST', body, key, options) }
  private list<Op>(path: string, parameters: AdminPropQuery, options: AdminCallOptions) { return this.transport.request<{ data: Data<Op> }>(path + query(parameters), { method: 'GET' }, { ...options, token: options.token ?? this.session.accessToken }).then((response) => response.data) }
  private write<Op>(path: string, method: 'PATCH' | 'POST', body: unknown, key: string, options: AdminCallOptions) {
    if (!key || key.length < 16 || key.length > 128) throw new RangeError('幂等键长度无效')
    return this.transport.request<{ data: Data<Op> }>(path, { method, headers: { 'Content-Type': 'application/json', 'X-Idempotency-Key': key }, body: JSON.stringify(body) }, { ...options, token: options.token ?? this.session.accessToken }).then((response) => response.data)
  }
}
export const adminPropsApi = new AdminPropsApi()
