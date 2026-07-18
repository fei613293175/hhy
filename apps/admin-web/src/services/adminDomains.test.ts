import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminDomainsApi } from './adminDomains';
import { AdminSecurityApi } from './adminSecurity';
import { AdminSession } from './adminSession';
import { IdempotencyKeyFactory } from './idempotency';

const BASE_URL = 'https://admin-api.example.test/root/';

function success(data: unknown): Response {
  return new Response(JSON.stringify({
    success: true,
    requestId: 'request-domains',
    timestamp: '2026-07-18T15:40:00Z',
    data,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } });
}

function session(): AdminSession {
  const value = new AdminSession();
  value.apply({
    accessToken: 'admin-access-token',
    adminUserId: '1',
    displayName: 'root',
    permissionCodes: ['domain.read', 'domain.write', 'domain.verify'],
    mfaRequired: 'NONE',
  });
  return value;
}

describe('AdminDomainsApi', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('binds domain and DNS action reads to frozen operations', async () => {
    const current = session();
    const api = new AdminDomainsApi(
      new AdminSecurityApi(BASE_URL, current, new IdempotencyKeyFactory()), current,
    );
    fetchMock.mockImplementation(async () => success({
      items: [], page: { page: 1, pageSize: 20, total: '0' },
    }));

    await api.list({ page: 1, pageSize: 20, keyword: 'api' });
    await api.dnsActions({ status: 'PENDING_USER_DNS' });

    expect((fetchMock.mock.calls[0][0] as URL).pathname).toBe('/admin-api/v1/domains');
    expect((fetchMock.mock.calls[0][0] as URL).searchParams.get('keyword')).toBe('api');
    expect((fetchMock.mock.calls[1][0] as URL).pathname)
      .toBe('/admin-api/v1/domains/dns-actions');
  });

  it('uses PUT and POST once with the supplied idempotency key', async () => {
    const current = session();
    const api = new AdminDomainsApi(
      new AdminSecurityApi(BASE_URL, current, new IdempotencyKeyFactory()),
      current,
      new IdempotencyKeyFactory(),
    );
    fetchMock.mockImplementation(async () => success({
      code: 'api', hostname: 'api.orbexa.cc', dnsStatus: 'PASSED',
      httpsStatus: 'PASSED', version: 6,
    }));
    const options = { idempotencyKey: '00000000-0000-4000-8000-000000000401' };

    await api.update('API', {
      hostname: 'api.orbexa.cc', certificateMode: 'MANAGED', expectedVersion: 5,
    }, options);
    await api.verify('api', { force: false }, options);

    const [updateUrl, updateInit] = fetchMock.mock.calls[0] as [URL, RequestInit];
    const [verifyUrl, verifyInit] = fetchMock.mock.calls[1] as [URL, RequestInit];
    expect(updateUrl.pathname).toBe('/admin-api/v1/domains/api');
    expect(updateInit.method).toBe('PUT');
    expect(verifyUrl.pathname).toBe('/admin-api/v1/domains/api/verify');
    expect(verifyInit.method).toBe('POST');
    expect(new Headers(updateInit.headers).get('X-Idempotency-Key')).toBe(options.idempotencyKey);
    expect(new Headers(verifyInit.headers).get('X-Idempotency-Key')).toBe(options.idempotencyKey);
    expect(updateInit.body).toContain('expectedVersion');
    expect(verifyInit.body).toBe(JSON.stringify({ force: false }));
  });

  it('rejects invalid code and pagination without network access', async () => {
    const current = session();
    const api = new AdminDomainsApi(
      new AdminSecurityApi(BASE_URL, current, new IdempotencyKeyFactory()), current,
    );

    await expect(api.verify('../api', {}, {})).rejects.toBeInstanceOf(RangeError);
    await expect(api.list({ page: 0 })).rejects.toBeInstanceOf(RangeError);
    await expect(api.list({ page: 1, cursor: 'next' })).rejects.toBeInstanceOf(RangeError);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('never retries a domain write after an uncertain network failure', async () => {
    const current = session();
    const api = new AdminDomainsApi(
      new AdminSecurityApi(BASE_URL, current, new IdempotencyKeyFactory()), current,
    );
    fetchMock.mockRejectedValueOnce(new TypeError('offline'));

    await expect(api.verify('api', { force: false }, {
      idempotencyKey: '00000000-0000-4000-8000-000000000402',
    })).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
