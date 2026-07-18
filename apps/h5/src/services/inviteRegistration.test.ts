import { beforeEach, describe, expect, it, vi } from 'vitest';
import { InviteApiError, InviteRegistrationApi } from './inviteRegistration';

function jsonResponse(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

describe('InviteRegistrationApi', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('retries only the public configuration read after a network failure', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValueOnce(jsonResponse({
        success: true,
        requestId: 'req-config',
        data: { items: [], page: { page: 1, pageSize: 20, hasMore: 'false' } },
      }));
    const api = new InviteRegistrationApi('https://api.example.test/', fetcher);

    const result = await api.getConfig('INVITE-R02');

    expect(result.items).toEqual([]);
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(fetcher.mock.calls[0]?.[0].toString()).toBe(
      'https://api.example.test/public-api/v1/invite/INVITE-R02/registration-config',
    );
  });

  it('sends challenge with a secure idempotency key and never automatically retries writes', async () => {
    const fetcher = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('offline'));
    const api = new InviteRegistrationApi('https://api.example.test/', fetcher);

    await expect(api.createChallenge({ scene: 'REGISTER', clientNonce: 'nonce-r02' }))
      .rejects.toMatchObject({ code: 'NETWORK_ERROR' });

    expect(fetcher).toHaveBeenCalledTimes(1);
    const init = fetcher.mock.calls[0]?.[1];
    expect(new Headers(init?.headers).get('X-Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(init?.method).toBe('POST');
  });

  it('reuses a write key after uncertain failure when the intent is unchanged', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValueOnce(jsonResponse({
        success: true,
        requestId: 'req-challenge',
        data: { challengeId: 'c-1', challengeType: 'IMAGE', expiresAt: '2026-07-18T09:30:00Z' },
      }));
    const api = new InviteRegistrationApi('https://api.example.test/', fetcher);
    const body = { scene: 'REGISTER' as const, clientNonce: 'nonce-r02' };

    await expect(api.createChallenge(body)).rejects.toBeInstanceOf(InviteApiError);
    await api.createChallenge(body);

    const first = new Headers(fetcher.mock.calls[0]?.[1]?.headers).get('X-Idempotency-Key');
    const second = new Headers(fetcher.mock.calls[1]?.[1]?.headers).get('X-Idempotency-Key');
    expect(second).toBe(first);
  });

  it('uses the exact challenge-protected register contract without SMS fields', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({
        success: false,
        requestId: 'req-register',
        error: { code: 'COMMON-429-RATE_LIMITED', message: '请求过于频繁', retryable: true },
      }, 429, { 'Retry-After': '45' }));
    const api = new InviteRegistrationApi('https://api.example.test/', fetcher);

    await expect(api.register({
      phone: '13800000000', password: 'StrongPass9', inviteCode: 'INVITE-R02',
      challengeId: 'c-1', challengeProof: 'proof',
    })).rejects.toMatchObject({
      status: 429, code: 'COMMON-429-RATE_LIMITED', retryAfterSeconds: 45,
    });

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher.mock.calls[0]?.[0].toString()).toBe('https://api.example.test/api/v1/auth/register');
    const body = JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body)) as Record<string, unknown>;
    expect(body).not.toHaveProperty('smsCode');
    expect(body).not.toHaveProperty('agreementVersions');
  });
});
