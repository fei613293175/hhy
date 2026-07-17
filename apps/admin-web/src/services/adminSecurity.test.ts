import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminSecurityApi } from './adminSecurity';
import { AdminSession } from './adminSession';
import { ApiRequestError, isApiRequestError } from './apiError';
import { IdempotencyKeyFactory } from './idempotency';

const BASE_URL = 'https://admin-api.example.test/root/';

function success(data: unknown, requestId = 'request-success'): Response {
  return new Response(JSON.stringify({
    success: true,
    requestId,
    timestamp: '2026-07-17T11:00:00Z',
    data,
  }), {
    status: 200,
    headers: { 'Content-Type': 'application/json', 'X-Request-Id': requestId },
  });
}

function sessionResource(accessToken = 'access-token') {
  return {
    accessToken,
    expiresAt: '2026-07-17T19:00:00Z',
    adminUserId: '17',
    displayName: 'root',
    permissionCodes: ['admin.self.read', 'admin.self.security'],
    mfaRequired: 'NONE',
  };
}

function securityResource() {
  return {
    adminId: '17',
    username: 'root',
    mfaEnabled: true,
    mfaMethods: ['TOTP'],
    activeSessionCount: 1,
    recoveryCodesRemaining: 0,
  };
}

function requestAt(fetchMock: ReturnType<typeof vi.fn>, index: number) {
  const [url, init] = fetchMock.mock.calls[index] as [URL, RequestInit];
  return { url, init, headers: new Headers(init.headers) };
}

describe('AdminSecurityApi', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('calls all eight frozen R01 operations with the exact method, path, headers and body', async () => {
    const api = new AdminSecurityApi(
      BASE_URL,
      new AdminSession(),
      new IdempotencyKeyFactory(),
    );
    fetchMock
      .mockResolvedValueOnce(success(sessionResource('login-access'), 'request-login'))
      .mockResolvedValueOnce(success(sessionResource('verified-access'), 'request-verify'))
      .mockResolvedValueOnce(success(securityResource(), 'request-overview'))
      .mockResolvedValueOnce(success(securityResource(), 'request-password'))
      .mockResolvedValueOnce(success({
        enrollmentId: 'enrollment-17',
        method: 'TOTP',
        secretQrCodeUrl: 'otpauth://totp/test?secret=MASKED',
        manualKeyMasked: 'ABCD****WXYZ',
        expiresAt: '2026-07-17T11:10:00Z',
      }, 'request-enroll'))
      .mockResolvedValueOnce(success(securityResource(), 'request-confirm'))
      .mockResolvedValueOnce(success({ ...securityResource(), mfaEnabled: false, mfaMethods: [] }, 'request-disable'))
      .mockResolvedValueOnce(success({
        resourceId: '23',
        status: 'REVOKED',
        version: 5,
        acceptedAt: '2026-07-17T11:00:00Z',
      }, 'request-logout'));

    await api.login(
      { username: 'root', password: 'Current!234' },
      { idempotencyKey: 'idem-login-00000001' },
    );
    await api.verifyMfa(
      { mfaTicket: 'mfa-ticket', code: '123456' },
      { idempotencyKey: 'idem-verify-0000001' },
    );
    await api.getSecurity({ token: 'explicit-access' });
    await api.changePassword(
      { currentPassword: 'Current!234', newPassword: 'New!56789', mfaCode: '234567' },
      { token: 'explicit-access', idempotencyKey: 'idem-password-00001' },
    );
    await api.enrollMfa({
      token: 'explicit-access',
      idempotencyKey: 'idem-enroll-0000001',
    });
    await api.confirmMfa(
      { enrollmentId: 'enrollment-17', code: '345678' },
      { token: 'explicit-access', idempotencyKey: 'idem-confirm-000001' },
    );
    await api.disableMfa(
      { code: '456789', reason: '换绑设备' },
      { token: 'explicit-access', idempotencyKey: 'idem-disable-000001' },
    );
    await api.logout(undefined, {
      token: 'explicit-access',
      idempotencyKey: 'idem-logout-0000001',
    });

    const expected = [
      ['POST', '/admin-api/v1/auth/login', 'idem-login-00000001'],
      ['POST', '/admin-api/v1/auth/mfa/verify', 'idem-verify-0000001'],
      ['GET', '/admin-api/v1/me/security', null],
      ['POST', '/admin-api/v1/me/security/password/change', 'idem-password-00001'],
      ['POST', '/admin-api/v1/me/security/mfa/enroll', 'idem-enroll-0000001'],
      ['POST', '/admin-api/v1/me/security/mfa/confirm', 'idem-confirm-000001'],
      ['POST', '/admin-api/v1/me/security/mfa/disable', 'idem-disable-000001'],
      ['POST', '/admin-api/v1/auth/logout', 'idem-logout-0000001'],
    ] as const;

    expect(fetchMock).toHaveBeenCalledTimes(expected.length);
    expected.forEach(([method, path, idempotencyKey], index) => {
      const request = requestAt(fetchMock, index);
      expect(request.url.href).toBe(`https://admin-api.example.test${path}`);
      expect(request.init.method).toBe(method);
      expect(request.headers.get('Accept')).toBe('application/json');
      expect(request.headers.get('X-Idempotency-Key')).toBe(idempotencyKey);
    });

    expect(requestAt(fetchMock, 0).headers.has('Authorization')).toBe(false);
    expect(requestAt(fetchMock, 0).init.body).toBe(JSON.stringify({
      username: 'root',
      password: 'Current!234',
    }));
    expect(requestAt(fetchMock, 1).headers.get('X-MFA-Ticket')).toBe('mfa-ticket');
    expect(requestAt(fetchMock, 1).init.body).toBe(JSON.stringify({
      mfaTicket: 'mfa-ticket',
      code: '123456',
    }));
    expect(requestAt(fetchMock, 2).headers.get('Authorization')).toBe('Bearer explicit-access');
    expect(requestAt(fetchMock, 2).init.body).toBeUndefined();
    expect(requestAt(fetchMock, 3).init.body).toBe(JSON.stringify({
      currentPassword: 'Current!234',
      newPassword: 'New!56789',
      mfaCode: '234567',
    }));
    expect(requestAt(fetchMock, 4).init.body).toBeUndefined();
    expect(requestAt(fetchMock, 5).init.body).toBe(JSON.stringify({
      enrollmentId: 'enrollment-17',
      code: '345678',
    }));
    expect(requestAt(fetchMock, 6).init.body).toBe(JSON.stringify({
      code: '456789',
      reason: '换绑设备',
    }));
    expect(requestAt(fetchMock, 7).init.body).toBeUndefined();
    expect(requestAt(fetchMock, 7).headers.has('Content-Type')).toBe(false);
  });

  it('parses the structured error and Retry-After header without leaking request input', async () => {
    const api = new AdminSecurityApi(BASE_URL, new AdminSession(), new IdempotencyKeyFactory());
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({
      success: false,
      requestId: 'request-rate-limit',
      error: {
        code: 'COMMON-429-RATE_LIMITED',
        message: '尝试过于频繁',
        retryable: true,
        details: [{ field: 'password', code: 'AUTH-LOCKED', message: '密码尝试次数过多' }],
      },
    }), {
      status: 429,
      headers: { 'Content-Type': 'application/json', 'Retry-After': '137' },
    }));

    const error = await api.login(
      { username: 'root', password: 'Secret!234' },
      { idempotencyKey: 'idem-rate-limit-001' },
    ).catch((caught: unknown) => caught);

    expect(isApiRequestError(error)).toBe(true);
    expect(error).toBeInstanceOf(ApiRequestError);
    expect(error).toMatchObject({
      status: 429,
      code: 'COMMON-429-RATE_LIMITED',
      message: '尝试过于频繁',
      requestId: 'request-rate-limit',
      retryAfter: 137,
      retryable: true,
      details: [{ field: 'password', code: 'AUTH-LOCKED', message: '密码尝试次数过多' }],
    });
    expect(String(error)).not.toContain('Secret!234');
  });

  it('retains the generated idempotency key after a network failure and rotates it after success', async () => {
    const session = new AdminSession();
    const keys = new IdempotencyKeyFactory();
    const api = new AdminSecurityApi(BASE_URL, session, keys);
    fetchMock
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValueOnce(success(sessionResource('first-access')))
      .mockResolvedValueOnce(success(sessionResource('second-access')));

    await expect(api.login({ username: 'root', password: 'Secret!234' }))
      .rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
    await api.login({ username: 'root', password: 'Secret!234' });
    await api.login({ username: 'root', password: 'Secret!234' });

    const firstKey = requestAt(fetchMock, 0).headers.get('X-Idempotency-Key');
    const retryKey = requestAt(fetchMock, 1).headers.get('X-Idempotency-Key');
    const nextIntentKey = requestAt(fetchMock, 2).headers.get('X-Idempotency-Key');
    expect(firstKey).toBeTruthy();
    expect(retryKey).toBe(firstKey);
    expect(nextIntentKey).not.toBe(firstKey);
  });

  it('rotates the generated idempotency key when a failed request body changes', async () => {
    const api = new AdminSecurityApi(
      BASE_URL,
      new AdminSession(),
      new IdempotencyKeyFactory(),
    );
    fetchMock
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValueOnce(success(sessionResource('changed-intent-access')));

    await expect(api.login({ username: 'root', password: 'First!234' }))
      .rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
    await api.login({ username: 'root', password: 'Changed!567' });

    const firstKey = requestAt(fetchMock, 0).headers.get('X-Idempotency-Key');
    const changedIntentKey = requestAt(fetchMock, 1).headers.get('X-Idempotency-Key');
    expect(firstKey).toBeTruthy();
    expect(changedIntentKey).toBeTruthy();
    expect(changedIntentKey).not.toBe(firstKey);
  });

  it('retries the read-only security overview twice after transient network failures', async () => {
    const api = new AdminSecurityApi(
      BASE_URL,
      new AdminSession(),
      new IdempotencyKeyFactory(),
    );
    fetchMock
      .mockRejectedValueOnce(new TypeError('offline-one'))
      .mockRejectedValueOnce(new TypeError('offline-two'))
      .mockResolvedValueOnce(success(securityResource()));

    await expect(api.getSecurity({ token: 'explicit-access' }))
      .resolves.toMatchObject({ adminId: '17', username: 'root' });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('does not automatically retry a non-network security overview failure', async () => {
    const api = new AdminSecurityApi(
      BASE_URL,
      new AdminSession(),
      new IdempotencyKeyFactory(),
    );
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({
      success: false,
      requestId: 'request-forbidden',
      error: { code: 'COMMON-403-FORBIDDEN', message: '没有权限' },
    }), { status: 403, headers: { 'Content-Type': 'application/json' } }));

    await expect(api.getSecurity({ token: 'explicit-access' }))
      .rejects.toMatchObject({ status: 403, code: 'COMMON-403-FORBIDDEN' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('clears the in-memory session on 401 and after successful logout', async () => {
    const session = new AdminSession();
    session.apply(sessionResource('sensitive-access'));
    const api = new AdminSecurityApi(BASE_URL, session, new IdempotencyKeyFactory());
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({
      success: false,
      requestId: 'request-unauthenticated',
      error: { code: 'COMMON-401-UNAUTHENTICATED', message: '会话已失效' },
    }), { status: 401, headers: { 'Content-Type': 'application/json' } }));

    await expect(api.getSecurity()).rejects.toMatchObject({ status: 401 });
    expect(session.isAuthenticated).toBe(false);

    session.apply(sessionResource('new-access'));
    fetchMock.mockResolvedValueOnce(success({
      resourceId: '23',
      status: 'REVOKED',
      acceptedAt: '2026-07-17T11:00:00Z',
    }));
    await api.logout(undefined, { idempotencyKey: 'idem-logout-clear-1' });
    expect(session.isAuthenticated).toBe(false);
    expect(session.permissions).toEqual([]);
  });
});
