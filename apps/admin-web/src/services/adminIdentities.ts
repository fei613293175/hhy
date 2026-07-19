import type { AdminContract, RequestOptions } from '@hhy/api-client';
import { ApiRequestError, apiRequestErrorFromNetwork } from './apiError';
import { AdminSecurityApi, adminSecurityApi, type AdminCallOptions } from './adminSecurity';
import { AdminSession, adminSession } from './adminSession';
import {
  IdempotencyKeyFactory,
  adminIdempotencyKeys,
  type AdminSecurityIdempotentOperation,
} from './idempotency';

type ListOperation = AdminContract.operations['adminIdentityGetIdentities'];
type DetailOperation = AdminContract.operations['adminIdentityGetIdentitiesByUserid'];
type MediaOperation = AdminContract.operations['adminIdentityPostIdentitiesByUseridMediaAccess'];
type ReviewOperation = AdminContract.operations['adminIdentityPostIdentitySessionsByIdReview'];
type FreezeOperation = AdminContract.operations['adminIdentityPostIdentitiesByUseridFreeze'];
type JsonResponse<Value> = Value extends { content: { 'application/json': infer Json } } ? Json : never;
type Success<Operation> = Operation extends { responses: { 200: infer Response } }
  ? JsonResponse<Response> : never;
type SuccessData<Operation> = Success<Operation> extends { data: infer Data } ? Data : never;
type RequestBody<Operation> = Operation extends {
  requestBody: { content: { 'application/json': infer Body } };
} ? Body : never;

export type AdminIdentityResource = AdminContract.components['schemas']['IdentitySessionResource'];
export type AdminIdentityPageResource = Success<ListOperation>['data'];
export type AdminIdentityListParameters = NonNullable<ListOperation['parameters']['query']>;
export type AdminIdentityCommandResult = AdminContract.components['schemas']['CommandResultResource'];
export type AdminIdentityCommandOutcome = AdminIdentityResource | AdminIdentityCommandResult;
export type AdminIdentityMediaRequest = RequestBody<MediaOperation>;
export type AdminIdentityReviewRequest = RequestBody<ReviewOperation>;
export type AdminIdentityFreezeRequest = RequestBody<FreezeOperation>;

type IdentityWriteOperation = Extract<AdminSecurityIdempotentOperation,
  | 'adminIdentityPostIdentitiesByUseridMediaAccess'
  | 'adminIdentityPostIdentitySessionsByIdReview'
  | 'adminIdentityPostIdentitiesByUseridFreeze'>;

const SAFE_ID = /^[A-Za-z0-9_-]{1,64}$/;
const ALLOWED_SORTS = new Set([
  'createdAt:desc', 'createdAt:asc', 'status:asc', 'status:desc', 'id:asc', 'id:desc',
]);

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

function requireId(value: string, label: string): string {
  if (!SAFE_ID.test(value)) throw new RangeError(`${label}格式无效`);
  return value;
}

export class AdminIdentitiesApi {
  constructor(
    private readonly transport: AdminSecurityApi = adminSecurityApi,
    private readonly session: AdminSession = adminSession,
    private readonly keys: IdempotencyKeyFactory = adminIdempotencyKeys,
  ) { }

  async list(
    parameters: AdminIdentityListParameters = {},
    options: AdminCallOptions = {},
  ): Promise<AdminIdentityPageResource> {
    validateListParameters(parameters);
    const query = new URLSearchParams();
    Object.entries(parameters).forEach(([key, value]) => {
      if (value !== undefined && value !== '') query.set(key, String(value));
    });
    const suffix = query.size === 0 ? '' : `?${query.toString()}`;
    const response = await this.read<Success<ListOperation>>(
      `/admin-api/v1/identities${suffix}`, options,
    );
    return response.data;
  }

  async detail(userId: string, options: AdminCallOptions = {}): Promise<AdminIdentityResource> {
    requireId(userId, '用户标识');
    const response = await this.read<Success<DetailOperation>>(
      `/admin-api/v1/identities/${encodeURIComponent(userId)}`, options,
    );
    return response.data;
  }

  async mediaAccess(
    userId: string,
    body: AdminIdentityMediaRequest,
    options: AdminCallOptions = {},
  ): Promise<AdminIdentityCommandOutcome> {
    requireId(userId, '用户标识');
    return this.write<MediaOperation>(
      'adminIdentityPostIdentitiesByUseridMediaAccess',
      `/admin-api/v1/identities/${encodeURIComponent(userId)}/media-access`, body, options,
    );
  }

  async review(
    sessionId: string,
    body: AdminIdentityReviewRequest,
    options: AdminCallOptions = {},
  ): Promise<AdminIdentityCommandOutcome> {
    requireId(sessionId, '认证会话标识');
    return this.write<ReviewOperation>(
      'adminIdentityPostIdentitySessionsByIdReview',
      `/admin-api/v1/identity-sessions/${encodeURIComponent(sessionId)}/review`, body, options,
    );
  }

  async freeze(
    userId: string,
    body: AdminIdentityFreezeRequest,
    options: AdminCallOptions = {},
  ): Promise<AdminIdentityCommandOutcome> {
    requireId(userId, '用户标识');
    return this.write<FreezeOperation>(
      'adminIdentityPostIdentitiesByUseridFreeze',
      `/admin-api/v1/identities/${encodeURIComponent(userId)}/freeze`, body, options,
    );
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
    throw new Error('Unreachable admin identity read retry state');
  }

  private async write<Operation>(
    operation: IdentityWriteOperation,
    path: string,
    body: unknown,
    options: AdminCallOptions,
  ): Promise<SuccessData<Operation>> {
    const serialized = JSON.stringify(body);
    const usesFactory = options.idempotencyKey === undefined;
    const key = options.idempotencyKey ?? this.keys.current(operation, `${path}\n${serialized}`);
    const response = await this.transport.request<{ data: SuccessData<Operation> }>(
      path,
      { method: 'POST', body: serialized },
      { ...options, token: options.token ?? this.session.accessToken, idempotencyKey: key },
    );
    if (usesFactory) this.keys.clear(operation);
    return response.data;
  }
}

export function validateListParameters(parameters: AdminIdentityListParameters): void {
  if (parameters.cursor) throw new RangeError('当前实名列表暂不支持游标分页');
  if (parameters.page !== undefined && parameters.page < 1) {
    throw new RangeError('页码必须大于等于 1');
  }
  if (parameters.pageSize !== undefined
      && (parameters.pageSize < 1 || parameters.pageSize > 100)) {
    throw new RangeError('每页数量必须在 1 到 100 之间');
  }
  if (parameters.sort !== undefined && !ALLOWED_SORTS.has(parameters.sort)) {
    throw new RangeError('排序方式不在允许范围内');
  }
}

export const adminIdentitiesApi = new AdminIdentitiesApi();
