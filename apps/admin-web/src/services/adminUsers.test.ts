import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminSecurityApi } from './adminSecurity';
import { AdminSession } from './adminSession';
import { AdminUsersApi } from './adminUsers';
import { IdempotencyKeyFactory } from './idempotency';

const BASE_URL = 'https://admin-api.example.test/root/';

function success(data: unknown): Response {
  return new Response(JSON.stringify({
    success: true,
    requestId: 'request-users',
    timestamp: '2026-07-18T00:00:00Z',
    data,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } });
}

function authenticatedSession(): AdminSession {
  const session = new AdminSession();
  session.apply({
    accessToken: 'admin-access-token',
    adminUserId: '1',
    displayName: 'root',
    permissionCodes: ['user.read'],
    mfaRequired: 'NONE',
  });
  return session;
}

describe('AdminUsersApi', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('binds generated list parameters to the frozen operation path', async () => {
    const session = authenticatedSession();
    const api = new AdminUsersApi(
      new AdminSecurityApi(BASE_URL, session, new IdempotencyKeyFactory()),
      session,
    );
    fetchMock.mockResolvedValueOnce(success({
      items: [{ id: '42', phoneMasked: '138****5678', status: 'ACTIVE', version: 3 }],
      page: { page: 2, pageSize: 20, total: '21', hasMore: 'false' },
    }));

    const result = await api.listUsers({
      page: 2,
      pageSize: 20,
      status: 'ACTIVE',
      keyword: '合伙人',
      sort: 'createdAt:desc',
    });

    const [url, init] = fetchMock.mock.calls[0] as [URL, RequestInit];
    expect(url.pathname).toBe('/admin-api/v1/users');
    expect(url.searchParams.get('page')).toBe('2');
    expect(url.searchParams.get('keyword')).toBe('合伙人');
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer admin-access-token');
    expect(result.items[0].phoneMasked).toBe('138****5678');
  });

  it('loads detail by the exact generated operation path', async () => {
    const session = authenticatedSession();
    const api = new AdminUsersApi(
      new AdminSecurityApi(BASE_URL, session, new IdempotencyKeyFactory()),
      session,
    );
    fetchMock.mockResolvedValueOnce(success({ id: '42', status: 'ACTIVE', version: 3 }));

    await expect(api.getUser('42')).resolves.toMatchObject({ id: '42', version: 3 });
    const [url, init] = fetchMock.mock.calls[0] as [URL, RequestInit];
    expect(url.pathname).toBe('/admin-api/v1/users/42');
    expect(init.method).toBe('GET');
  });

  it('retries read-only network failures without retrying validation mistakes', async () => {
    vi.useFakeTimers();
    const session = authenticatedSession();
    const api = new AdminUsersApi(
      new AdminSecurityApi(BASE_URL, session, new IdempotencyKeyFactory()),
      session,
    );
    fetchMock
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValueOnce(success({ items: [], page: { pageSize: 20, hasMore: 'false' } }));

    const request = api.listUsers({ page: 1, pageSize: 20 });
    await vi.advanceTimersByTimeAsync(100);
    await expect(request).resolves.toMatchObject({ items: [] });
    expect(fetchMock).toHaveBeenCalledTimes(2);

    await expect(api.listUsers({ page: 1, cursor: 'next' })).rejects.toBeInstanceOf(RangeError);
    await expect(api.getUser('invalid/value')).rejects.toBeInstanceOf(RangeError);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
