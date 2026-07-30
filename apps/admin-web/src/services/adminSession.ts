import type { AdminContract } from '@hhy/api-client';

export type AdminSessionResource =
  AdminContract.components['schemas']['AdminSessionResource'];

export class AdminSession {
  private static readonly STORAGE_KEY = 'hhy.admin.session.v1';
  private token?: string;
  private ticket?: string;
  private permissionCodes: string[] = [];
  private name?: string;

  constructor() {
    this.restore();
  }

  get accessToken(): string | undefined {
    return this.token;
  }

  get mfaTicket(): string | undefined {
    return this.ticket;
  }

  get permissions(): readonly string[] {
    return [...this.permissionCodes];
  }

  get displayName(): string | undefined {
    return this.name;
  }

  get isAuthenticated(): boolean {
    return Boolean(this.token);
  }

  apply(resource: AdminSessionResource): void {
    this.clear();
    this.token = resource.accessToken;
    this.ticket = resource.mfaTicket;
    this.permissionCodes = [...(resource.permissionCodes ?? [])];
    this.name = resource.displayName;
    if (this.token) this.persist(resource.expiresAt);
  }

  hasPermission(permission: string): boolean {
    return this.permissionCodes.includes(permission);
  }

  clear(): void {
    this.token = undefined;
    this.ticket = undefined;
    this.permissionCodes = [];
    this.name = undefined;
    this.storage()?.removeItem(AdminSession.STORAGE_KEY);
  }

  private storage(): Storage | undefined {
    try {
      return typeof sessionStorage === 'undefined' ? undefined : sessionStorage;
    } catch {
      return undefined;
    }
  }

  private persist(expiresAt?: string): void {
    try {
      this.storage()?.setItem(AdminSession.STORAGE_KEY, JSON.stringify({
        accessToken: this.token,
        displayName: this.name,
        permissionCodes: this.permissionCodes,
        expiresAt,
      }));
    } catch {}
  }

  private restore(): void {
    try {
      const serialized = this.storage()?.getItem(AdminSession.STORAGE_KEY);
      if (!serialized) return;
      const saved = JSON.parse(serialized) as Record<string, unknown>;
      if (typeof saved.expiresAt === 'string' && Date.parse(saved.expiresAt) <= Date.now()) {
        this.storage()?.removeItem(AdminSession.STORAGE_KEY);
        return;
      }
      if (typeof saved.accessToken !== 'string' || !Array.isArray(saved.permissionCodes)
          || !saved.permissionCodes.every((permission) => typeof permission === 'string')) {
        this.storage()?.removeItem(AdminSession.STORAGE_KEY);
        return;
      }
      this.token = saved.accessToken;
      this.permissionCodes = [...saved.permissionCodes] as string[];
      this.name = typeof saved.displayName === 'string' ? saved.displayName : undefined;
    } catch {
      this.storage()?.removeItem(AdminSession.STORAGE_KEY);
    }
  }
}

export const adminSession = new AdminSession();
