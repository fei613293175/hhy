import type { AdminContract } from '@hhy/api-client';

export const ADMIN_SECURITY_IDEMPOTENT_OPERATIONS = [
  'adminAdminAuthPostAuthLogin',
  'adminAdminAuthPostAuthMfaVerify',
  'adminAdminAuthPostAuthLogout',
  'adminSelfPostPasswordChange',
  'adminSelfPostMfaEnroll',
  'adminSelfPostMfaConfirm',
  'adminSelfPostMfaDisable',
  'adminUsersPostUsersByIdRestrictions',
  'adminUsersDeleteUsersByIdRestrictionsByType',
  'adminUsersPostUsersByIdFreeze',
  'adminUsersPostUsersByIdUnfreeze',
  'adminUsersPostUsersByIdForceLogout',
  'adminProviderConfigPostProviderConfigsByProviderVersions',
  'adminProviderConfigPostProviderConfigsByProviderTest',
  'adminProviderConfigPostProviderConfigsByProviderActivate',
  'adminProviderConfigPostProviderConfigsByProviderRollback',
  'adminDomainConfigPutDomainsByCode',
  'adminDomainConfigPostDomainsByCodeVerify',
] as const satisfies readonly (keyof AdminContract.operations)[];

export type AdminSecurityIdempotentOperation =
  typeof ADMIN_SECURITY_IDEMPOTENT_OPERATIONS[number];

function secureUuid(): string {
  if (typeof globalThis.crypto?.randomUUID !== 'function') {
    throw new Error('Secure random UUID generation is unavailable');
  }
  return globalThis.crypto.randomUUID();
}

export class IdempotencyKeyFactory {
  private readonly active = new Map<AdminSecurityIdempotentOperation, {
    key: string;
    intentFingerprint: string;
  }>();

  current(operation: AdminSecurityIdempotentOperation, intentFingerprint = ''): string {
    const existing = this.active.get(operation);
    if (existing?.intentFingerprint === intentFingerprint) return existing.key;
    return this.rotate(operation, intentFingerprint);
  }

  rotate(operation: AdminSecurityIdempotentOperation, intentFingerprint = ''): string {
    const key = secureUuid();
    this.active.set(operation, { key, intentFingerprint });
    return key;
  }

  clear(operation?: AdminSecurityIdempotentOperation): void {
    if (operation) {
      this.active.delete(operation);
    } else {
      this.active.clear();
    }
  }
}

export const adminIdempotencyKeys = new IdempotencyKeyFactory();
