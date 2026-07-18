import type { AdminContract, RequestOptions } from '@hhy/api-client';
import { ApiRequestError, apiRequestErrorFromNetwork } from './apiError';
import { AdminSecurityApi, adminSecurityApi, type AdminCallOptions } from './adminSecurity';
import { AdminSession, adminSession } from './adminSession';

type ListOperation = AdminContract.operations['adminUsersGetUsers'];
type DetailOperation = AdminContract.operations['adminUsersGetUsersById'];
type JsonResponse<Value> = Value extends {
  content: { 'application/json': infer Json };
} ? Json : never;
type Success<Operation> = Operation extends {
  responses: { 200: infer Response };
} ? JsonResponse<Response> : never;

export type AdminUserResource = AdminContract.components['schemas']['UserResource'];
export type AdminUserPageResource = Success<ListOperation>['data'];
export type AdminUserListParameters = NonNullable<ListOperation['parameters']['query']>;

function delay(milliseconds: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Aborted', 'AbortError'));
      return;
    }
    const onAbort = () => {
      globalThis.clearTimeout(timer);
      reject(new DOMException('Aborted', 'AbortError'));
    };
    const timer = globalThis.setTimeout(() => {
      signal?.removeEventListener('abort', onAbort);
      resolve();
    }, milliseconds);
    signal?.addEventListener('abort', onAbort, { once: true });
  });
}

export class AdminUsersApi {
  constructor(
    private readonly transport: AdminSecurityApi = adminSecurityApi,
    private readonly session: AdminSession = adminSession,
  ) { }

  async listUsers(
    parameters: AdminUserListParameters = {},
    options: AdminCallOptions = {},
  ): Promise<AdminUserPageResource> {
    validateListParameters(parameters);
    const query = new URLSearchParams();
    Object.entries(parameters).forEach(([key, value]) => {
      if (value !== undefined && value !== '') query.set(key, String(value));
    });
    const suffix = query.size === 0 ? '' : `?${query.toString()}`;
    const response = await this.read<Success<ListOperation>>(
      `/admin-api/v1/users${suffix}`,
      options,
    );
    return response.data;
  }

  async getUser(userId: string, options: AdminCallOptions = {}): Promise<AdminUserResource> {
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(userId)) {
      throw new RangeError('用户标识格式无效');
    }
    const response = await this.read<Success<DetailOperation>>(
      `/admin-api/v1/users/${encodeURIComponent(userId)}`,
      options,
    );
    return response.data;
  }

  private async read<T>(path: string, options: AdminCallOptions): Promise<T> {
    const authenticated: RequestOptions = {
      ...options,
      token: options.token ?? this.session.accessToken,
    };
    for (let attempt = 0; attempt < 3; attempt += 1) {
      try {
        return await this.transport.request<T>(path, { method: 'GET' }, authenticated);
      } catch (caught) {
        const retry = caught instanceof ApiRequestError
          && caught.code === 'NETWORK_ERROR'
          && attempt < 2;
        if (!retry) throw caught;
        await delay(100 * (2 ** attempt), options.signal).catch((error) => {
          throw apiRequestErrorFromNetwork(error);
        });
      }
    }
    throw new Error('Unreachable admin user read retry state');
  }
}

function validateListParameters(parameters: AdminUserListParameters): void {
  if (parameters.cursor && parameters.page !== undefined) {
    throw new RangeError('游标与页码不能同时使用');
  }
  if (parameters.page !== undefined && parameters.page < 1) {
    throw new RangeError('页码必须大于等于 1');
  }
  if (parameters.pageSize !== undefined
      && (parameters.pageSize < 1 || parameters.pageSize > 100)) {
    throw new RangeError('每页数量必须在 1 到 100 之间');
  }
}

export const adminUsersApi = new AdminUsersApi();
