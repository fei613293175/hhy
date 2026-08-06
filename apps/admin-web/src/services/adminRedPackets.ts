import type { AdminContract } from '@hhy/api-client'
import { AdminSecurityApi, adminSecurityApi, type AdminCallOptions } from './adminSecurity'
import { AdminSession, adminSession } from './adminSession'

type Operation<Id extends keyof AdminContract.operations> = AdminContract.operations[Id]
type Json<Value> = Value extends { content: { 'application/json': infer Body } } ? Body : never
type Success<Op> = Op extends { responses: { 200: infer Response } } ? Json<Response> : never
type Data<Op> = Success<Op> extends { data: infer Value } ? Value : never
type Body<Op> = Op extends { requestBody: { content: { 'application/json': infer Value } } } ? Value : never

export type AdminRedPacketResource = AdminContract.components['schemas']['RedPacketCampaignResource']
export type AdminRedPacketPage = Data<Operation<'adminRedPacketGetRedPacketCampaigns'>>
export type AdminRedPacketQuery = { page?: number; pageSize?: number; cursor?: string; status?: string; keyword?: string; sort?: string }
export type AdminRedPacketReview = Body<Operation<'adminRedPacketPostRedPacketCampaignsByIdReview'>>
export type AdminRedPacketLifecycle = Body<Operation<'adminRedPacketPostRedPacketCampaignsByIdPause'>>
export type AdminRedPacketSessionsPage = Data<Operation<'adminRedPacketGetRedPacketCampaignsByIdSessions'>>
export type AdminRedPacketClaimsPage = Data<Operation<'adminRedPacketGetRedPacketCampaignsByIdClaims'>>
export type AdminRedPacketLedger = Data<Operation<'adminRedPacketGetRedPacketCampaignsByIdLedger'>>

const SAFE_ID = /^[A-Za-z0-9_-]{1,64}$/
const SORTS = new Set(['createdAt:asc', 'createdAt:desc', 'updatedAt:desc', 'priority:desc,createdAt:asc'])

function safeId(value: string): string {
  if (!SAFE_ID.test(value)) throw new RangeError('红包活动标识格式无效')
  return encodeURIComponent(value)
}
function query(parameters: AdminRedPacketQuery): string {
  if (parameters.page !== undefined && parameters.page < 1) throw new RangeError('页码必须大于等于 1')
  if (parameters.pageSize !== undefined && (parameters.pageSize < 1 || parameters.pageSize > 100)) throw new RangeError('每页数量必须在 1 到 100 之间')
  if (parameters.cursor && parameters.page !== undefined && parameters.page !== 1) throw new RangeError('页码和游标不能同时用于翻页')
  if (parameters.sort && !SORTS.has(parameters.sort)) throw new RangeError('红包排序方式无效')
  const value = new URLSearchParams()
  Object.entries(parameters).forEach(([key, item]) => { if (item !== undefined && item !== '') value.set(key, String(item)) })
  return value.size ? `?${value}` : ''
}

export class AdminRedPacketApi {
  constructor(private readonly transport: AdminSecurityApi = adminSecurityApi, private readonly session: AdminSession = adminSession) {}

  campaigns(parameters: AdminRedPacketQuery = {}, options: AdminCallOptions = {}) {
    return this.list<Operation<'adminRedPacketGetRedPacketCampaigns'>>('/admin-api/v1/red-packet-campaigns', parameters, options)
  }
  campaign(id: string, options: AdminCallOptions = {}) {
    return this.transport.request<{ data: Data<Operation<'adminRedPacketGetRedPacketCampaignsById'>> }>(`/admin-api/v1/red-packet-campaigns/${safeId(id)}`, { method: 'GET' }, { ...options, token: options.token ?? this.session.accessToken }).then((response) => response.data)
  }
  review(id: string, body: AdminRedPacketReview, key: string, options: AdminCallOptions = {}) {
    if (!key || key.length < 16 || key.length > 128) throw new RangeError('幂等键长度无效')
    return this.transport.request<{ data: Data<Operation<'adminRedPacketPostRedPacketCampaignsByIdReview'>> }>(`/admin-api/v1/red-packet-campaigns/${safeId(id)}/review`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Idempotency-Key': key }, body: JSON.stringify(body) }, { ...options, token: options.token ?? this.session.accessToken }).then((response) => response.data)
  }
  pause(id: string, body: AdminRedPacketLifecycle, key: string, options: AdminCallOptions = {}) {
    return this.command<'adminRedPacketPostRedPacketCampaignsByIdPause'>(id, 'pause', body, key, options)
  }
  resume(id: string, body: AdminRedPacketLifecycle, key: string, options: AdminCallOptions = {}) {
    return this.command<'adminRedPacketPostRedPacketCampaignsByIdResume'>(id, 'resume', body, key, options)
  }
  terminate(id: string, body: Body<Operation<'adminRedPacketPostRedPacketCampaignsByIdTerminate'>>, key: string, options: AdminCallOptions = {}) {
    return this.command<'adminRedPacketPostRedPacketCampaignsByIdTerminate'>(id, 'terminate', body, key, options)
  }
  sessions(id: string, parameters: AdminRedPacketQuery = {}, options: AdminCallOptions = {}) {
    return this.list<Operation<'adminRedPacketGetRedPacketCampaignsByIdSessions'>>(`/admin-api/v1/red-packet-campaigns/${safeId(id)}/sessions`, parameters, options)
  }
  claims(id: string, parameters: AdminRedPacketQuery = {}, options: AdminCallOptions = {}) {
    return this.list<Operation<'adminRedPacketGetRedPacketCampaignsByIdClaims'>>(`/admin-api/v1/red-packet-campaigns/${safeId(id)}/claims`, parameters, options)
  }
  ledger(id: string, options: AdminCallOptions = {}) {
    return this.transport.request<{ data: Data<Operation<'adminRedPacketGetRedPacketCampaignsByIdLedger'>> }>(`/admin-api/v1/red-packet-campaigns/${safeId(id)}/ledger`, { method: 'GET' }, { ...options, token: options.token ?? this.session.accessToken }).then((response) => response.data)
  }
  private command<Op extends keyof AdminContract.operations>(id: string, action: string, body: Body<Operation<Op>>, key: string, options: AdminCallOptions) {
    if (!key || key.length < 16 || key.length > 128) throw new RangeError('幂等键长度无效')
    return this.transport.request<{ data: Data<Operation<Op>> }>(`/admin-api/v1/red-packet-campaigns/${safeId(id)}/${action}`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Idempotency-Key': key }, body: JSON.stringify(body) }, { ...options, token: options.token ?? this.session.accessToken }).then((response) => response.data)
  }
  private list<Op>(path: string, parameters: AdminRedPacketQuery, options: AdminCallOptions) {
    return this.transport.request<{ data: Data<Op> }>(path + query(parameters), { method: 'GET' }, { ...options, token: options.token ?? this.session.accessToken }).then((response) => response.data)
  }
}

export const adminRedPacketApi = new AdminRedPacketApi()
