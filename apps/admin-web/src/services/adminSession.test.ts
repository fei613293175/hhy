import { afterEach, describe, expect, it, vi } from 'vitest';
import { AdminSession } from './adminSession';
import { IdempotencyKeyFactory } from './idempotency';

function storageDouble(): Storage {
  return {
    length: 0,
    clear: vi.fn(),
    getItem: vi.fn(() => null),
    key: vi.fn(() => null),
    removeItem: vi.fn(),
    setItem: vi.fn(),
  };
}

describe('AdminSession', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('keeps credentials out of local storage and restores only within the current tab', () => {
    const local = storageDouble();
    const sessionStorage = storageDouble();
    let currentTabSession: string | null = null;
    vi.mocked(sessionStorage.getItem).mockImplementation(() => currentTabSession);
    vi.mocked(sessionStorage.setItem).mockImplementation((_key, value) => { currentTabSession = value; });
    vi.mocked(sessionStorage.removeItem).mockImplementation(() => { currentTabSession = null; });
    const cookieWrites: string[] = [];
    const documentDouble = {};
    Object.defineProperty(documentDouble, 'cookie', {
      get: () => '',
      set: (value: string) => cookieWrites.push(value),
    });
    vi.stubGlobal('localStorage', local);
    vi.stubGlobal('sessionStorage', sessionStorage);
    vi.stubGlobal('document', documentDouble);

    const session = new AdminSession();
    session.apply({
      accessToken: 'access-secret',
      adminUserId: '17',
      displayName: '超级管理员',
      permissionCodes: ['admin.self.read', 'admin.self.security'],
      mfaRequired: 'NONE',
    });

    expect(session.accessToken).toBe('access-secret');
    expect(session.mfaTicket).toBeUndefined();
    expect(session.displayName).toBe('超级管理员');
    expect(session.permissions).toEqual(['admin.self.read', 'admin.self.security']);
    expect(session.hasPermission('admin.self.security')).toBe(true);
    expect(session.hasPermission('admin.users.read')).toBe(false);
    expect(session.isAuthenticated).toBe(true);
    expect(sessionStorage.setItem).toHaveBeenCalledTimes(1);
    expect(local.setItem).not.toHaveBeenCalled();

    const restored = new AdminSession();
    expect(restored.accessToken).toBe('access-secret');
    expect(restored.permissions).toEqual(['admin.self.read', 'admin.self.security']);
    expect(restored.displayName).toBe('超级管理员');

    session.clear();

    expect(session.accessToken).toBeUndefined();
    expect(session.mfaTicket).toBeUndefined();
    expect(session.permissions).toEqual([]);
    expect(session.displayName).toBeUndefined();
    expect(session.isAuthenticated).toBe(false);
    expect(sessionStorage.removeItem).toHaveBeenCalled();
    expect(cookieWrites).toEqual([]);
  });

  it('rejects expired or malformed tab sessions', () => {
    const storage = storageDouble();
    vi.stubGlobal('sessionStorage', storage);
    vi.mocked(storage.getItem).mockReturnValueOnce(JSON.stringify({
      accessToken: 'expired',
      permissionCodes: ['user.read'],
      expiresAt: '2000-01-01T00:00:00Z',
    }));
    expect(new AdminSession().isAuthenticated).toBe(false);
    expect(storage.removeItem).toHaveBeenCalled();

    vi.mocked(storage.getItem).mockReturnValueOnce('{invalid');
    expect(new AdminSession().isAuthenticated).toBe(false);
  });

  it('replaces an access session with a pending MFA ticket without retaining the token', () => {
    const session = new AdminSession();
    session.apply({
      accessToken: 'old-access',
      adminUserId: '17',
      permissionCodes: ['admin.self.read'],
      mfaRequired: 'NONE',
    });

    session.apply({
      adminUserId: '17',
      displayName: 'root',
      mfaRequired: 'TOTP',
      mfaTicket: 'pending-ticket',
    });

    expect(session.accessToken).toBeUndefined();
    expect(session.mfaTicket).toBe('pending-ticket');
    expect(session.permissions).toEqual([]);
    expect(session.isAuthenticated).toBe(false);
  });
});

describe('IdempotencyKeyFactory', () => {
  it('returns a stable in-memory key until the operation is rotated or cleared', () => {
    const keys = new IdempotencyKeyFactory();
    const first = keys.current('adminSelfPostMfaConfirm');

    expect(keys.current('adminSelfPostMfaConfirm')).toBe(first);
    expect(first).toMatch(/^[0-9a-f-]{36}$/i);

    const second = keys.rotate('adminSelfPostMfaConfirm');
    expect(second).not.toBe(first);
    expect(keys.current('adminSelfPostMfaConfirm')).toBe(second);

    keys.clear('adminSelfPostMfaConfirm');
    expect(keys.current('adminSelfPostMfaConfirm')).not.toBe(second);
  });
});
