import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminSecurityApi } from './adminSecurity';
import { AdminSession } from './adminSession';
import { AdminProviderConfigsApi } from './adminProviderConfigs';
import { IdempotencyKeyFactory } from './idempotency';

const BASE_URL = 'https://admin-api.example.test/root/';

function success(data: unknown): Response {
  return new Response(JSON.stringify({
    success: true,
    requestId: 'request-provider-config',
    timestamp: '2026-07-18T13:50:00Z',
    data,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } });
}

function session(): AdminSession {
  const value = new AdminSession();
  value.apply({
    accessToken: 'admin-access-token',
    adminUserId: '1',
    displayName: 'root',
    permissionCodes: ['provider.config.read', 'provider.config.write', 'provider.config.test'],
    mfaRequired: 'NONE',
  });
  return value;
}

describe('AdminProviderConfigsApi', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('binds list and detail to the frozen generated operations', async () => {
    const current = session();
    const api = new AdminProviderConfigsApi(
      new AdminSecurityApi(BASE_URL, current, new IdempotencyKeyFactory()), current,
    );
    fetchMock
      .mockResolvedValueOnce(success({ items: [], page: { page: 1, pageSize: 20, total: '0' } }))
      .mockResolvedValueOnce(success({
        provider: 'sms', environment: 'STAGING', configuredSecrets: [], version: 2,
      }));

    await api.list({ page: 1, pageSize: 20, keyword: 'sms' });
    await api.get('SMS');

    const [listUrl] = fetchMock.mock.calls[0] as [URL, RequestInit];
    const [detailUrl, detailInit] = fetchMock.mock.calls[1] as [URL, RequestInit];
    expect(listUrl.pathname).toBe('/admin-api/v1/provider-configs');
    expect(listUrl.searchParams.get('keyword')).toBe('sms');
    expect(detailUrl.pathname).toBe('/admin-api/v1/provider-configs/sms');
    expect(new Headers(detailInit.headers).get('Authorization')).toBe('Bearer admin-access-token');
  });

  it('sends every write once with the supplied idempotency key and frozen body', async () => {
    const current = session();
    const api = new AdminProviderConfigsApi(
      new AdminSecurityApi(BASE_URL, current, new IdempotencyKeyFactory()),
      current,
      new IdempotencyKeyFactory(),
    );
    fetchMock.mockImplementation(async () => success({
      provider: 'sms', environment: 'STAGING', version: 3,
    }));
    const options = { idempotencyKey: '00000000-0000-4000-8000-000000000301' };

    await api.createVersion('sms', {
      environment: 'STAGING',
      values: { 'sms.active_provider': 'ALIYUN' },
      secretRefs: { 'sms.aliyun.access_key_secret': 'vault://hhy/staging/sms/key' },
      remark: '短信测试环境',
    }, options);
    await api.testConnection('sms', { versionId: 'sms-v1', testRecipient: '138****5678' }, options);
    await api.activate('sms', {
      versionId: 'sms-v1', approvalId: 'APR-1', expectedVersion: 3,
    }, options);
    await api.rollback('sms', {
      targetVersionId: 'sms-v0', approvalId: 'APR-2', reason: '连接异常', expectedVersion: 4,
    }, options);

    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(fetchMock.mock.calls.map((call) => (call[0] as URL).pathname)).toEqual([
      '/admin-api/v1/provider-configs/sms/versions',
      '/admin-api/v1/provider-configs/sms/test',
      '/admin-api/v1/provider-configs/sms/activate',
      '/admin-api/v1/provider-configs/sms/rollback',
    ]);
    fetchMock.mock.calls.forEach((call) => {
      const [, init] = call as [URL, RequestInit];
      expect(init.method).toBe('POST');
      expect(new Headers(init.headers).get('X-Idempotency-Key')).toBe(options.idempotencyKey);
    });
    expect(fetchMock.mock.calls[0][1]?.body).toContain('vault://hhy/staging/sms/key');
  });

  it('rejects invalid provider and pagination before network access', async () => {
    const current = session();
    const api = new AdminProviderConfigsApi(
      new AdminSecurityApi(BASE_URL, current, new IdempotencyKeyFactory()), current,
    );

    await expect(api.get('../sms')).rejects.toBeInstanceOf(RangeError);
    await expect(api.list({ page: 0 })).rejects.toBeInstanceOf(RangeError);
    await expect(api.list({ page: 1, cursor: 'next' })).rejects.toBeInstanceOf(RangeError);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('never retries a provider write after an uncertain network failure', async () => {
    const current = session();
    const api = new AdminProviderConfigsApi(
      new AdminSecurityApi(BASE_URL, current, new IdempotencyKeyFactory()), current,
    );
    fetchMock.mockRejectedValueOnce(new TypeError('offline'));

    await expect(api.testConnection('storage', { versionId: 'storage-v1' }, {
      idempotencyKey: '00000000-0000-4000-8000-000000000302',
    })).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
