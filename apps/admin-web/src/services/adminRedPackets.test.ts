import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminRedPacketApi } from './adminRedPackets'
import { AdminSession } from './adminSession'

function session() { const value = new AdminSession(); value.apply({ accessToken: 'access', adminUserId: '1', displayName: '红包运营', permissionCodes: ['redpacket.read', 'redpacket.manage', 'redpacket.terminate', 'redpacket.finance'], mfaRequired: 'NONE' }); return value }

describe('AdminRedPacketApi R23', () => {
  const transport = { request: vi.fn() }
  beforeEach(() => transport.request.mockReset())
  afterEach(() => undefined)

  it('binds frozen R23 detail read models without treating them as campaigns', async () => {
    const active = session(); const api = new AdminRedPacketApi(transport as never, active)
    transport.request.mockResolvedValue({ data: { items: [], page: { page: 1, pageSize: 20, total: '0', hasMore: 'false' } } })
    await api.sessions('42', { page: 1, pageSize: 20 }); await api.claims('42', { status: 'PENDING' }); await api.ledger('42')
    expect(transport.request.mock.calls.map(call => call[0].split('?')[0])).toEqual([
      '/admin-api/v1/red-packet-campaigns/42/sessions', '/admin-api/v1/red-packet-campaigns/42/claims', '/admin-api/v1/red-packet-campaigns/42/ledger',
    ])
    expect(transport.request.mock.calls[1][0]).toContain('status=PENDING')
  })

  it('sends stable explicit idempotency keys for every risk command', async () => {
    const active = session(); const api = new AdminRedPacketApi(transport as never, active)
    transport.request.mockResolvedValue({ data: { id: '42', status: 'PAUSED_BY_RISK', totalCount: 1, remainingCount: 1, amountPerClaimCent: 100, version: 2 } })
    const key = 'r23-red-packet-risk-command-0001'
    await api.pause('42', { reason: '证据命中', expectedVersion: 1 }, key)
    await api.resume('42', { reason: '人工复核', expectedVersion: 2 }, key)
    await api.terminate('42', { reason: '确认违规', expectedVersion: 3 }, key)
    expect(transport.request.mock.calls.map(call => [call[0], call[1].method])).toEqual([
      ['/admin-api/v1/red-packet-campaigns/42/pause', 'POST'], ['/admin-api/v1/red-packet-campaigns/42/resume', 'POST'], ['/admin-api/v1/red-packet-campaigns/42/terminate', 'POST'],
    ])
    transport.request.mock.calls.forEach(call => expect(new Headers(call[1].headers).get('X-Idempotency-Key')).toBe(key))
  })
})
