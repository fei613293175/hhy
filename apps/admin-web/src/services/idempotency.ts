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
  'adminProviderCertificatePostProviderCertificates',
  'adminProviderCertificatePostProviderCertificatesByIdRotate',
  'adminIdentityPostIdentitiesByUseridMediaAccess',
  'adminIdentityPostIdentitySessionsByIdReview',
  'adminIdentityPostIdentitiesByUseridFreeze',
  'adminContentPostContentsByIdOnline',
  'adminContentPostContentsByIdOffline',
  'adminContentPostContentsByIdBan',
  'adminContentPostContentsByIdRecommend',
  'adminContentPostContentsByIdOfficialMark',
  'adminContentPutContentDictionariesByCode',
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
  private readonly active = new Map<string, {
    key: string;
    intentFingerprint: string;
  }>();

  current(operation: AdminSecurityIdempotentOperation, intentFingerprint = '', scope = ''): string {
    const slot = this.slot(operation, scope);
    const existing = this.active.get(slot);
    if (existing?.intentFingerprint === intentFingerprint) return existing.key;
    return this.rotate(operation, intentFingerprint, scope);
  }

  rotate(operation: AdminSecurityIdempotentOperation, intentFingerprint = '', scope = ''): string {
    const key = secureUuid();
    this.active.set(this.slot(operation, scope), { key, intentFingerprint });
    return key;
  }

  clear(operation?: AdminSecurityIdempotentOperation, scope = ''): void {
    if (operation) {
      this.active.delete(this.slot(operation, scope));
    } else {
      this.active.clear();
    }
  }

  private slot(operation: AdminSecurityIdempotentOperation, scope: string): string {
    return `${operation}\n${scope}`;
  }
}

export const adminIdempotencyKeys = new IdempotencyKeyFactory();
