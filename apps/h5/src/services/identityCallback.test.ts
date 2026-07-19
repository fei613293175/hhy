import { describe, expect, it, vi } from 'vitest';
import { IdentityCallbackApi, IdentityCallbackApiError } from './identityCallback';

function response(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('IdentityCallbackApi', () => {
  it('posts the one-time state once and returns only the business status', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(response({
      success: true,
      requestId: 'not-for-ui',
      data: { status: 'LIVENESS_PENDING' },
    }));
    const api = new IdentityCallbackApi('https://api.example.test/', fetcher);

    const result = await api.consume('ff738a6c-71a1-4647-ab50-5d6f3cb5f544');

    expect(result).toEqual({ status: 'LIVENESS_PENDING' });
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher.mock.calls[0]?.[0].toString()).toBe(
      'https://api.example.test/public-api/v1/identity/callback/consume',
    );
    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual({
      state: 'ff738a6c-71a1-4647-ab50-5d6f3cb5f544',
    });
  });

  it('never retries a failed one-time consume automatically', async () => {
    const fetcher = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('offline'));
    const api = new IdentityCallbackApi('https://api.example.test/', fetcher);

    await expect(api.consume('ff738a6c-71a1-4647-ab50-5d6f3cb5f544'))
      .rejects.toBeInstanceOf(IdentityCallbackApiError);
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
});
