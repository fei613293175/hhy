import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminR15Api } from './adminR15'
import { AdminSecurityApi } from './adminSecurity'
import { AdminSession } from './adminSession'
import { IdempotencyKeyFactory } from './idempotency'

const BASE = 'https://admin.example.test/root/'
function success(data: unknown) { return new Response(JSON.stringify({ success: true, requestId: 'r15-request', data }), { status: 200, headers: { 'Content-Type': 'application/json' } }) }
function session() { const value = new AdminSession(); value.apply({ accessToken: 'r15-token', adminUserId: '15', displayName: 'R15 运营', permissionCodes: [], mfaRequired: 'NONE' }); return value }

describe('AdminR15Api', () => {
  const fetchMock = vi.fn<typeof fetch>()
  beforeEach(() => { fetchMock.mockReset(); vi.stubGlobal('fetch', fetchMock); fetchMock.mockImplementation(async () => success({ items: [], page: { page: 1, pageSize: 20, total: '0', hasMore: 'false' } })) })
  afterEach(() => vi.unstubAllGlobals())

  it('binds every R15 read surface to its frozen admin endpoint', async () => {
    const auth = session(); const api = new AdminR15Api(new AdminSecurityApi(BASE, auth, new IdempotencyKeyFactory()), auth)
    await api.chatReports({ page: 2, pageSize: 20, status: 'PENDING', keyword: 'chat-8', sort: 'createdAt:desc' })
    await api.contentReports({ page: 1, pageSize: 20 }); await api.appeals({ page: 1, pageSize: 20 }); await api.supportTickets({ page: 1, pageSize: 20 })
    fetchMock.mockResolvedValueOnce(success({ id: 'ticket-1', ticketNo: 'TK-1', status: 'OPEN', version: 3 }))
    await api.supportTicket('ticket-1')
    expect(fetchMock.mock.calls.map((call) => (call[0] as URL).pathname)).toEqual([
      '/admin-api/v1/chat-reports', '/admin-api/v1/content-reports', '/admin-api/v1/appeals', '/admin-api/v1/support/tickets', '/admin-api/v1/support/tickets/ticket-1',
    ])
    expect((fetchMock.mock.calls[0][0] as URL).searchParams.get('keyword')).toBe('chat-8')
  })

  it('sends all six commands once with the caller idempotency key and exact resource path', async () => {
    const auth = session(); const api = new AdminR15Api(new AdminSecurityApi(BASE, auth, new IdempotencyKeyFactory()), auth, new IdempotencyKeyFactory())
    const options = { idempotencyKey: '00000000-0000-4000-8000-000000000015' }
    fetchMock.mockImplementation(async () => success({ status: 'ACCEPTED', acceptedAt: '2026-08-03T00:00:00Z' }))
    const decision = { decision: 'APPROVE' as const, reason: '证据完整', expectedVersion: 3, evidenceIds: ['e-1'] }
    await api.decideChat('chat-1', decision, options); await api.decideReport('report-1', decision, options); await api.decideAppeal('appeal-1', decision, options)
    await api.assignTicket('ticket-1', { assigneeId: 'agent-8', expectedVersion: 3 }, options)
    await api.replyTicket('ticket-1', { content: '已收到您的问题', attachments: ['media-1'] }, options)
    await api.closeTicket('ticket-1', { reason: '问题已解决', expectedVersion: 3 }, options)
    expect(fetchMock.mock.calls.map((call) => (call[0] as URL).pathname)).toEqual([
      '/admin-api/v1/chat-reports/chat-1/decide', '/admin-api/v1/content-reports/report-1/decide', '/admin-api/v1/appeals/appeal-1/decide',
      '/admin-api/v1/support/tickets/ticket-1/assign', '/admin-api/v1/support/tickets/ticket-1/messages', '/admin-api/v1/support/tickets/ticket-1/close',
    ])
    fetchMock.mock.calls.forEach((call) => expect(new Headers((call[1] as RequestInit).headers).get('X-Idempotency-Key')).toBe(options.idempotencyKey))
  })

  it('rejects unsafe identifiers, pages and sorts before transport', async () => {
    const auth = session(); const api = new AdminR15Api(new AdminSecurityApi(BASE, auth), auth)
    await expect(api.supportTicket('../secret')).rejects.toBeInstanceOf(RangeError)
    await expect(api.chatReports({ page: 0 })).rejects.toBeInstanceOf(RangeError)
    await expect(api.appeals({ sort: 'status:drop-table' })).rejects.toBeInstanceOf(RangeError)
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
