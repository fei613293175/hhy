import { afterEach, describe, expect, it, vi } from 'vitest';
import { AdminContentsApi } from './adminContents';
import { adminSecurityApi } from './adminSecurity';
import { adminIdempotencyKeys } from './idempotency';

const ONLINE = 'adminContentPostContentsByIdOnline' as const;
const BODY = { expectedVersion: 3, reason: '审核通过' };

function keyAt(mock: ReturnType<typeof vi.spyOn>, index: number): string | undefined {
  return mock.mock.calls[index]?.[2]?.idempotencyKey;
}

describe('AdminContentsApi idempotency isolation', () => {
  afterEach(() => {
    adminIdempotencyKeys.clear();
    vi.restoreAllMocks();
  });

  it('retains one key for the same resource and request after a network failure', async () => {
    const request = vi.spyOn(adminSecurityApi, 'request').mockRejectedValue(new TypeError('network timeout'));
    const api = new AdminContentsApi();

    await expect(api.command(ONLINE, 'team_11', BODY as never)).rejects.toThrow('network timeout');
    await expect(api.command(ONLINE, 'team_11', BODY as never)).rejects.toThrow('network timeout');

    expect(keyAt(request, 0)).toBeDefined();
    expect(keyAt(request, 1)).toBe(keyAt(request, 0));
  });

  it('isolates concurrent intents for different content resources', async () => {
    const request = vi.spyOn(adminSecurityApi, 'request').mockRejectedValue(new TypeError('network timeout'));
    const api = new AdminContentsApi();

    await expect(api.command(ONLINE, 'team_11', BODY as never)).rejects.toThrow();
    await expect(api.command(ONLINE, 'team_12', BODY as never)).rejects.toThrow();
    await expect(api.command(ONLINE, 'team_11', BODY as never)).rejects.toThrow();

    expect(keyAt(request, 1)).not.toBe(keyAt(request, 0));
    expect(keyAt(request, 2)).toBe(keyAt(request, 0));
  });

  it('clears only the successful resource scope', async () => {
    const request = vi.spyOn(adminSecurityApi, 'request')
      .mockRejectedValueOnce(new TypeError('team 11 timeout'))
      .mockRejectedValueOnce(new TypeError('team 12 timeout'))
      .mockResolvedValueOnce({ data: { id: 'team_11', status: 'ONLINE', version: 4 } } as never)
      .mockRejectedValueOnce(new TypeError('team 12 retry timeout'));
    const api = new AdminContentsApi();

    await expect(api.command(ONLINE, 'team_11', BODY as never)).rejects.toThrow();
    await expect(api.command(ONLINE, 'team_12', BODY as never)).rejects.toThrow();
    const firstKey = keyAt(request, 0);
    const secondKey = keyAt(request, 1);
    await api.command(ONLINE, 'team_11', BODY as never);
    await expect(api.command(ONLINE, 'team_12', BODY as never)).rejects.toThrow();

    expect(keyAt(request, 2)).toBe(firstKey);
    expect(keyAt(request, 3)).toBe(secondKey);
  });
});
