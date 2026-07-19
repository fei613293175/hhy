import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminIdentitiesApi } from './adminIdentities';
import { AdminSecurityApi } from './adminSecurity';
import { AdminSession } from './adminSession';
import { IdempotencyKeyFactory } from './idempotency';

const BASE_URL = 'https://admin-api.example.test/root/';

function success(data: unknown): Response {
  return new Response(JSON.stringify({
    success: true,
    requestId: 'request-identity',
    timestamp: '2026-07-19T00:00:00Z',
    data,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } });
}

function authenticatedSession(): AdminSession {
  const session = new AdminSession();
  session.apply({
    accessToken: 'admin-access-token',
    adminUserId: '1',
    displayName: 'reviewer',
    permissionCodes: ['identity.read', 'identity.review', 'identity.media.view', 'identity.freeze'],
    mfaRequired: 'NONE',
  });
  return session;
}

describe('AdminIdentitiesApi', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('binds frozen list filters and detail user id', async () => {
    const session = authenticatedSession();
    const api = new AdminIdentitiesApi(
      new AdminSecurityApi(BASE_URL, session, new IdempotencyKeyFactory()), session,
    );
    fetchMock
      .mockResolvedValueOnce(success({
        items: [{ id: '77', userId: '42', status: 'MANUAL_REVIEW', version: 3 }],
        page: { page: 1, pageSize: 20, total: '1', hasMore: 'false' },
      }))
      .mockResolvedValueOnce(success({ id: '77', userId: '42', status: 'MANUAL_REVIEW', version: 3 }));

    await api.list({ page: 1, pageSize: 20, status: 'MANUAL_REVIEW', keyword: '42', sort: 'createdAt:desc' });
    await api.detail('42');

    const [listUrl] = fetchMock.mock.calls[0] as [URL, RequestInit];
    const [detailUrl] = fetchMock.mock.calls[1] as [URL, RequestInit];
    expect(listUrl.pathname).toBe('/admin-api/v1/identities');
    expect(listUrl.searchParams.get('status')).toBe('MANUAL_REVIEW');
    expect(detailUrl.pathname).toBe('/admin-api/v1/identities/42');
  });

  it('rejects unsupported pagination and sort before transport', async () => {
    const session = authenticatedSession();
    const api = new AdminIdentitiesApi(
      new AdminSecurityApi(BASE_URL, session, new IdempotencyKeyFactory()), session,
    );
    await expect(api.list({ cursor: 'next' })).rejects.toBeInstanceOf(RangeError);
    await expect(api.list({ pageSize: 101 })).rejects.toBeInstanceOf(RangeError);
    await expect(api.list({ sort: 'priority:desc' })).rejects.toBeInstanceOf(RangeError);
    await expect(api.detail('bad/value')).rejects.toBeInstanceOf(RangeError);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('retries read-only network failure at most twice', async () => {
    vi.useFakeTimers();
    const session = authenticatedSession();
    const api = new AdminIdentitiesApi(
      new AdminSecurityApi(BASE_URL, session, new IdempotencyKeyFactory()), session,
    );
    fetchMock
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValueOnce(success({ items: [], page: { pageSize: 20, hasMore: 'false' } }));

    const request = api.list({ page: 1, pageSize: 20 });
    await vi.advanceTimersByTimeAsync(100);
    await expect(request).resolves.toMatchObject({ items: [] });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('binds three writes to separate operation paths and stable keys', async () => {
    const session = authenticatedSession();
    const api = new AdminIdentitiesApi(
      new AdminSecurityApi(BASE_URL, session, new IdempotencyKeyFactory()),
      session,
      new IdempotencyKeyFactory(),
    );
    const key = '00000000-0000-4000-8000-000000000077';
    fetchMock.mockImplementation(async () => success({
      resourceId: '42', businessNo: 'APR-1', status: 'PENDING_APPROVAL', version: 3,
    }));

    await api.mediaAccess('42', { purpose: '复核实名资料', ttlSeconds: 60 }, { idempotencyKey: key });
    await api.review('77', {
      decision: 'APPROVE', reason: '证据一致', expectedVersion: 3, evidenceIds: [],
    }, { idempotencyKey: key });
    await api.freeze('42', { reason: '实名争议待核查', expectedVersion: 3 }, { idempotencyKey: key });

    expect(fetchMock.mock.calls.map((call) => (call[0] as URL).pathname)).toEqual([
      '/admin-api/v1/identities/42/media-access',
      '/admin-api/v1/identity-sessions/77/review',
      '/admin-api/v1/identities/42/freeze',
    ]);
    fetchMock.mock.calls.forEach((call) => {
      const headers = new Headers((call[1] as RequestInit).headers);
      expect(headers.get('X-Idempotency-Key')).toBe(key);
      expect((call[1] as RequestInit).method).toBe('POST');
    });
  });

  it('never retries a write after a network failure', async () => {
    const session = authenticatedSession();
    const api = new AdminIdentitiesApi(
      new AdminSecurityApi(BASE_URL, session, new IdempotencyKeyFactory()), session,
    );
    fetchMock.mockRejectedValueOnce(new TypeError('offline'));
    await expect(api.freeze(
      '42',
      { reason: '实名争议待核查', expectedVersion: 3 },
      { idempotencyKey: '00000000-0000-4000-8000-000000000078' },
    )).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
