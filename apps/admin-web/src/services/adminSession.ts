import type { AdminContract } from '@hhy/api-client';

export type AdminSessionResource =
  AdminContract.components['schemas']['AdminSessionResource'];

export class AdminSession {
  private token?: string;
  private ticket?: string;
  private permissionCodes: string[] = [];
  private name?: string;

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
  }

  hasPermission(permission: string): boolean {
    return this.permissionCodes.includes(permission);
  }

  clear(): void {
    this.token = undefined;
    this.ticket = undefined;
    this.permissionCodes = [];
    this.name = undefined;
  }
}

export const adminSession = new AdminSession();
