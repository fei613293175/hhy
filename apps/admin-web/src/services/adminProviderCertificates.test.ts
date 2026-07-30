import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminProviderCertificatesApi } from './adminProviderCertificates';
import { AdminSecurityApi } from './adminSecurity';
import { AdminSession } from './adminSession';
import { IdempotencyKeyFactory } from './idempotency';

const BASE_URL = 'https://admin-api.example.test/root/';

function success(data: unknown): Response {
  return new Response(JSON.stringify({
    success: true,
    requestId: 'request-certificates',
    timestamp: '2026-07-18T16:00:00Z',
    data,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } });
}

function session(): AdminSession {
  const value = new AdminSession();
  value.apply({
    accessToken: 'admin-access-token', adminUserId: '1', displayName: 'root',
    permissionCodes: ['provider.certificate.read', 'provider.certificate.write'],
    mfaRequired: 'NONE',
  });
  return value;
}

describe('AdminProviderCertificatesApi', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('binds list, upload and rotation to frozen operations', async () => {
    const current = session();
    const api = new AdminProviderCertificatesApi(
      new AdminSecurityApi(BASE_URL, current, new IdempotencyKeyFactory()),
      current, new IdempotencyKeyFactory(),
    );
    fetchMock.mockImplementation(async () => success({
      items: [], page: { page: 1, pageSize: 20, total: '0' },
    }));

    await api.list({ page: 1, pageSize: 20, status: 'ACTIVE' });
    await api.upload({
      provider: 'payout', certificateType: 'ALIPAY_PRIVATE_KEY', alias: '主私钥',
      encryptedContentBase64: 'ZW5jcnlwdGVkLWNlcnRpZmljYXRl',
      passwordSecretRef: 'vault://prod/payout/password',
    }, { idempotencyKey: '00000000-0000-4000-8000-000000000501' });
    await api.rotate('cert-1', {
      newCertificateId: 'cert-2', approvalId: 'approval-1', reason: '到期轮换',
      expectedVersion: 3,
    }, { idempotencyKey: '00000000-0000-4000-8000-000000000502' });

    expect((fetchMock.mock.calls[0][0] as URL).searchParams.get('status')).toBe('ACTIVE');
    const [uploadUrl, uploadInit] = fetchMock.mock.calls[1] as [URL, RequestInit];
    expect(uploadUrl.pathname).toBe('/admin-api/v1/provider-certificates');
    expect(uploadInit.method).toBe('POST');
    expect(uploadInit.body).toContain('encryptedContentBase64');
    const [rotateUrl, rotateInit] = fetchMock.mock.calls[2] as [URL, RequestInit];
    expect(rotateUrl.pathname).toBe('/admin-api/v1/provider-certificates/cert-1/rotate');
    expect(rotateInit.body).toContain('expectedVersion');
  });

  it('never retries certificate writes after an uncertain network failure', async () => {
    const current = session();
    const api = new AdminProviderCertificatesApi(
      new AdminSecurityApi(BASE_URL, current, new IdempotencyKeyFactory()), current,
    );
    fetchMock.mockRejectedValueOnce(new TypeError('offline'));

    await expect(api.rotate('cert-1', {
      newCertificateId: 'cert-2', approvalId: 'approval-1', reason: '轮换', expectedVersion: 1,
    }, { idempotencyKey: '00000000-0000-4000-8000-000000000503' }))
      .rejects.toMatchObject({ code: 'NETWORK_ERROR' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('rejects malformed identifiers and pagination before network access', async () => {
    const current = session();
    const api = new AdminProviderCertificatesApi(
      new AdminSecurityApi(BASE_URL, current, new IdempotencyKeyFactory()), current,
    );

    expect(() => api.rotate('../cert', {
      newCertificateId: 'cert-2', approvalId: 'approval-1', reason: '轮换', expectedVersion: 1,
    })).toThrow(RangeError);
    await expect(api.list({ page: 0 })).rejects.toBeInstanceOf(RangeError);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
