import type { AdminContract, RequestOptions } from '@hhy/api-client'
import { ApiRequestError, apiRequestErrorFromNetwork } from './apiError'
import { AdminSecurityApi, adminSecurityApi, type AdminCallOptions } from './adminSecurity'
import { AdminSession, adminSession } from './adminSession'
import { IdempotencyKeyFactory, adminIdempotencyKeys, type AdminSecurityIdempotentOperation } from './idempotency'

type Operation<Id extends keyof AdminContract.operations> = AdminContract.operations[Id]
type Json<Value> = Value extends { content: { 'application/json': infer Body } } ? Body : never
type Success<Op> = Op extends { responses: { 200: infer Response } } ? Json<Response> : never
type Data<Op> = Success<Op> extends { data: infer Value } ? Value : never
type Body<Op> = Op extends { requestBody: { content: { 'application/json': infer Value } } } ? Value : never

type ChatList = Operation<'adminChatGetChatReports'>
type ChatDecide = Operation<'adminChatPostChatReportsByIdDecide'>
type ReportList = Operation<'adminReportsGetContentReports'>
type ReportDecide = Operation<'adminReportsPostContentReportsByIdDecide'>
type AppealList = Operation<'adminAppealsGetAppeals'>
type AppealDecide = Operation<'adminAppealsPostAppealsByIdDecide'>
type TicketList = Operation<'adminSupportGetSupportTickets'>
type TicketDetail = Operation<'adminSupportGetSupportTicketsById'>
type TicketAssign = Operation<'adminSupportPostSupportTicketsByIdAssign'>
type TicketMessage = Operation<'adminSupportPostSupportTicketsByIdMessages'>
type TicketClose = Operation<'adminSupportPostSupportTicketsByIdClose'>

export type AdminChatReportPage = Data<ChatList>
export type AdminContentReportPage = Data<ReportList>
export type AdminAppealQueuePage = Data<AppealList>
export type AdminSupportTicketPage = Data<TicketList>
export type AdminSupportTicket = AdminContract.components['schemas']['SupportTicketResource']
export type AdminGovernanceDecision = Body<ReportDecide>
export type AdminSupportAssign = Body<TicketAssign>
export type AdminSupportMessage = Body<TicketMessage>
export type AdminSupportClose = Body<TicketClose>
export type AdminListQuery = { page?: number; pageSize?: number; cursor?: string; status?: string; keyword?: string; sort?: string }

type R15Write = Extract<AdminSecurityIdempotentOperation,
  'adminReportsPostContentReportsByIdDecide' | 'adminAppealsPostAppealsByIdDecide'
  | 'adminChatPostChatReportsByIdDecide' | 'adminSupportPostSupportTicketsByIdAssign'
  | 'adminSupportPostSupportTicketsByIdMessages' | 'adminSupportPostSupportTicketsByIdClose'>

const SAFE_ID = /^[A-Za-z0-9_-]{1,64}$/
const SORTS = new Set(['createdAt:desc', 'createdAt:asc', 'updatedAt:desc', 'updatedAt:asc', 'id:desc', 'id:asc'])

function safeId(value: string): string {
  if (!SAFE_ID.test(value)) throw new RangeError('资源标识格式无效')
  return encodeURIComponent(value)
}

function query(parameters: AdminListQuery): string {
  if (parameters.page !== undefined && parameters.page < 1) throw new RangeError('页码必须大于等于 1')
  if (parameters.pageSize !== undefined && (parameters.pageSize < 1 || parameters.pageSize > 100)) throw new RangeError('每页数量必须在 1 到 100 之间')
  if (parameters.cursor && parameters.page !== undefined && parameters.page !== 1) throw new RangeError('页码和游标不能同时用于翻页')
  if (parameters.cursor && parameters.cursor.length > 256) throw new RangeError('游标长度超出限制')
  if (parameters.sort && !SORTS.has(parameters.sort)) throw new RangeError('排序方式不在允许范围内')
  const value = new URLSearchParams()
  Object.entries(parameters).forEach(([key, item]) => { if (item !== undefined && item !== '') value.set(key, String(item)) })
  return value.size ? `?${value}` : ''
}

function delay(milliseconds: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(new DOMException('Aborted', 'AbortError'))
    const timer = globalThis.setTimeout(resolve, milliseconds)
    signal?.addEventListener('abort', () => { globalThis.clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }, { once: true })
  })
}

export class AdminR15Api {
  constructor(
    private readonly transport: AdminSecurityApi = adminSecurityApi,
    private readonly session: AdminSession = adminSession,
    private readonly keys: IdempotencyKeyFactory = adminIdempotencyKeys,
  ) {}

  chatReports(parameters: AdminListQuery = {}, options: AdminCallOptions = {}) { return this.list<ChatList>('/admin-api/v1/chat-reports', parameters, options) }
  contentReports(parameters: AdminListQuery = {}, options: AdminCallOptions = {}) { return this.list<ReportList>('/admin-api/v1/content-reports', parameters, options) }
  appeals(parameters: AdminListQuery = {}, options: AdminCallOptions = {}) { return this.list<AppealList>('/admin-api/v1/appeals', parameters, options) }
  supportTickets(parameters: AdminListQuery = {}, options: AdminCallOptions = {}) { return this.list<TicketList>('/admin-api/v1/support/tickets', parameters, options) }

  async supportTicket(id: string, options: AdminCallOptions = {}): Promise<Data<TicketDetail>> {
    const response = await this.read<Success<TicketDetail>>(`/admin-api/v1/support/tickets/${safeId(id)}`, options)
    return response.data
  }

  decideChat(id: string, body: Body<ChatDecide>, options: AdminCallOptions = {}) { return this.write<ChatDecide>('adminChatPostChatReportsByIdDecide', `/admin-api/v1/chat-reports/${safeId(id)}/decide`, id, body, options) }
  decideReport(id: string, body: Body<ReportDecide>, options: AdminCallOptions = {}) { return this.write<ReportDecide>('adminReportsPostContentReportsByIdDecide', `/admin-api/v1/content-reports/${safeId(id)}/decide`, id, body, options) }
  decideAppeal(id: string, body: Body<AppealDecide>, options: AdminCallOptions = {}) { return this.write<AppealDecide>('adminAppealsPostAppealsByIdDecide', `/admin-api/v1/appeals/${safeId(id)}/decide`, id, body, options) }
  assignTicket(id: string, body: AdminSupportAssign, options: AdminCallOptions = {}) { return this.write<TicketAssign>('adminSupportPostSupportTicketsByIdAssign', `/admin-api/v1/support/tickets/${safeId(id)}/assign`, id, body, options) }
  replyTicket(id: string, body: AdminSupportMessage, options: AdminCallOptions = {}) { return this.write<TicketMessage>('adminSupportPostSupportTicketsByIdMessages', `/admin-api/v1/support/tickets/${safeId(id)}/messages`, id, body, options) }
  closeTicket(id: string, body: AdminSupportClose, options: AdminCallOptions = {}) { return this.write<TicketClose>('adminSupportPostSupportTicketsByIdClose', `/admin-api/v1/support/tickets/${safeId(id)}/close`, id, body, options) }

  private async list<Op>(path: string, parameters: AdminListQuery, options: AdminCallOptions): Promise<Data<Op>> {
    const response = await this.read<{ data: Data<Op> }>(`${path}${query(parameters)}`, options)
    return response.data
  }

  private async read<T>(path: string, options: AdminCallOptions): Promise<T> {
    const authenticated: RequestOptions = { ...options, token: options.token ?? this.session.accessToken }
    for (let attempt = 0; attempt < 3; attempt += 1) {
      try { return await this.transport.request<T>(path, { method: 'GET' }, authenticated) }
      catch (caught) {
        if (!(caught instanceof ApiRequestError) || caught.code !== 'NETWORK_ERROR' || attempt === 2) throw caught
        await delay(100 * (2 ** attempt), options.signal).catch((error) => { throw apiRequestErrorFromNetwork(error) })
      }
    }
    throw new Error('Unreachable R15 read retry state')
  }

  private async write<Op>(operation: R15Write, path: string, scope: string, body: unknown, options: AdminCallOptions): Promise<Data<Op>> {
    const serialized = JSON.stringify(body)
    const generated = options.idempotencyKey === undefined
    const key = options.idempotencyKey ?? this.keys.current(operation, `${path}\n${serialized}`, scope)
    const response = await this.transport.request<{ data: Data<Op> }>(path, { method: 'POST', body: serialized }, {
      ...options, token: options.token ?? this.session.accessToken, idempotencyKey: key,
    })
    if (generated) this.keys.clear(operation, scope)
    return response.data
  }
}

export const adminR15Api = new AdminR15Api()
