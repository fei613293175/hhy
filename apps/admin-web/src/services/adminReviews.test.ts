import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminReviewsApi } from './adminReviews';
import { AdminSecurityApi } from './adminSecurity';
import { AdminSession } from './adminSession';
import { IdempotencyKeyFactory } from './idempotency';

const BASE_URL = 'https://admin-api.example.test/root/';

function success(data: unknown): Response {
  return new Response(JSON.stringify({
    success: true,
    requestId: 'request-review',
    timestamp: '2026-07-25T00:00:00Z',
    data,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } });
}

function authenticatedSession(): AdminSession {
  const session = new AdminSession();
  session.apply({
    accessToken: 'admin-access-token',
    adminUserId: '1',
    displayName: '审核主管',
    permissionCodes: ['review.read', 'review.decide', 'review.assign', 'report.read', 'appeal.read'],
    mfaRequired: 'NONE',
  });
  return session;
}

describe('AdminReviewsApi', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('binds the four frozen read operations and query contract', async () => {
    const session = authenticatedSession();
    const api = new AdminReviewsApi(
      new AdminSecurityApi(BASE_URL, session, new IdempotencyKeyFactory()), session,
    );
    fetchMock.mockImplementation(async () => success({ items: [], page: { page: 1, pageSize: 20, total: '0', hasMore: 'false' } }));

    await api.queue({ page: 2, pageSize: 20, status: 'REVIEWING', keyword: '71', sort: 'priority:desc,createdAt:asc' });
    await api.detail('71');
    await api.reports({ page: 1, pageSize: 20, keyword: '71', sort: 'createdAt:desc' });
    await api.appeals({ page: 1, pageSize: 20, keyword: '71', sort: 'createdAt:desc' });

    const urls = fetchMock.mock.calls.map((call) => call[0] as URL);
    expect(urls.map((url) => url.pathname)).toEqual([
      '/admin-api/v1/reviews/queue',
      '/admin-api/v1/reviews/71',
      '/admin-api/v1/content-reports',
      '/admin-api/v1/appeals',
    ]);
    expect(urls[0].searchParams.get('sort')).toBe('priority:desc,createdAt:asc');
    expect(urls[0].searchParams.get('status')).toBe('REVIEWING');
  });

  it('rejects invalid identifiers, pagination and sorts before transport', async () => {
    const session = authenticatedSession();
    const api = new AdminReviewsApi(
      new AdminSecurityApi(BASE_URL, session, new IdempotencyKeyFactory()), session,
    );
    await expect(api.detail('bad/value')).rejects.toBeInstanceOf(RangeError);
    await expect(api.queue({ page: 0 })).rejects.toBeInstanceOf(RangeError);
    await expect(api.queue({ pageSize: 101 })).rejects.toBeInstanceOf(RangeError);
    await expect(api.queue({ sort: 'priority:asc' })).rejects.toBeInstanceOf(RangeError);
    await expect(api.queue({ cursor: 'next', sort: 'priority:desc,createdAt:asc' })).rejects.toBeInstanceOf(RangeError);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('retries read-only network failure at most twice', async () => {
    vi.useFakeTimers();
    const session = authenticatedSession();
    const api = new AdminReviewsApi(
      new AdminSecurityApi(BASE_URL, session, new IdempotencyKeyFactory()), session,
    );
    fetchMock
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValueOnce(success({ items: [], page: { page: 1, pageSize: 20, total: '0', hasMore: 'false' } }));

    const request = api.queue({ page: 1, pageSize: 20 });
    await vi.advanceTimersByTimeAsync(100);
    await expect(request).resolves.toMatchObject({ items: [] });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('binds decide and assign to separate stable idempotent writes', async () => {
    const session = authenticatedSession();
    const api = new AdminReviewsApi(
      new AdminSecurityApi(BASE_URL, session, new IdempotencyKeyFactory()),
      session,
      new IdempotencyKeyFactory(),
    );
    const key = '00000000-0000-4000-8000-000000000091';
    fetchMock.mockImplementation(async () => success({
      id: '71', subjectType: 'CONTENT', subjectId: '71', status: 'REVIEWING', version: 4,
    }));

    await api.decide('71', { decision: 'ESCALATE', reason: '需要二审', expectedVersion: 3, evidenceIds: ['report-1'] }, { idempotencyKey: key });
    await api.assign('71', { assigneeId: '9', reason: '专业领域复核', expectedVersion: 4 }, { idempotencyKey: key });

    expect(fetchMock.mock.calls.map((call) => (call[0] as URL).pathname)).toEqual([
      '/admin-api/v1/reviews/71/decide',
      '/admin-api/v1/reviews/71/assign',
    ]);
    fetchMock.mock.calls.forEach((call) => {
      const headers = new Headers((call[1] as RequestInit).headers);
      expect(headers.get('X-Idempotency-Key')).toBe(key);
      expect((call[1] as RequestInit).method).toBe('POST');
    });
  });

  it('never retries a write after an uncertain network failure', async () => {
    const session = authenticatedSession();
    const api = new AdminReviewsApi(
      new AdminSecurityApi(BASE_URL, session, new IdempotencyKeyFactory()), session,
    );
    fetchMock.mockRejectedValueOnce(new TypeError('offline'));
    await expect(api.assign('71', {
      assigneeId: '9', reason: '领取待审任务', expectedVersion: 3,
    })).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
