import type { AdminContract, RequestOptions } from '@hhy/api-client';
import { ApiRequestError, apiRequestErrorFromNetwork } from './apiError';
import { AdminSecurityApi, adminSecurityApi, type AdminCallOptions } from './adminSecurity';
import { AdminSession, adminSession } from './adminSession';
import {
  IdempotencyKeyFactory,
  adminIdempotencyKeys,
  type AdminSecurityIdempotentOperation,
} from './idempotency';

type Operation<Id extends keyof AdminContract.operations> = AdminContract.operations[Id];
type JsonResponse<Value> = Value extends { content: { 'application/json': infer Json } } ? Json : never;
type Success<Op> = Op extends { responses: { 200: infer Response } } ? JsonResponse<Response> : never;
type SuccessData<Op> = Success<Op> extends { data: infer Data } ? Data : never;
type Body<Op> = Op extends { requestBody: { content: { 'application/json': infer Value } } } ? Value : never;

type QueueOperation = Operation<'adminReviewGetReviewsQueue'>;
type DetailOperation = Operation<'adminReviewGetReviewsById'>;
type ReportsOperation = Operation<'adminReportsGetContentReports'>;
type AppealsOperation = Operation<'adminAppealsGetAppeals'>;
type DecideOperation = Operation<'adminReviewPostReviewsByIdDecide'>;
type AssignOperation = Operation<'adminReviewPostReviewsByIdAssign'>;

export type AdminReviewResource = AdminContract.components['schemas']['ReviewResource'];
export type AdminReviewPage = SuccessData<QueueOperation>;
export type AdminReportPage = SuccessData<ReportsOperation>;
export type AdminAppealPage = SuccessData<AppealsOperation>;
export type AdminReviewQueueParameters = NonNullable<QueueOperation['parameters']['query']>;
export type AdminReportParameters = NonNullable<ReportsOperation['parameters']['query']>;
export type AdminAppealParameters = NonNullable<AppealsOperation['parameters']['query']>;
export type AdminReviewDecisionRequest = Body<DecideOperation>;
export type AdminReviewAssignRequest = Body<AssignOperation>;
export type AdminReviewCommandOutcome = SuccessData<DecideOperation> | SuccessData<AssignOperation>;

type ReviewWriteOperation = Extract<AdminSecurityIdempotentOperation,
  'adminReviewPostReviewsByIdDecide' | 'adminReviewPostReviewsByIdAssign'>;

const SAFE_ID = /^[A-Za-z0-9_-]{1,64}$/;
const QUEUE_SORTS = new Set([
  'priority:desc,createdAt:asc',
  'createdAt:desc', 'createdAt:asc',
  'updatedAt:desc', 'updatedAt:asc',
  'id:desc', 'id:asc',
]);
const RELATED_SORTS = new Set([
  'createdAt:desc', 'createdAt:asc',
  'updatedAt:desc', 'updatedAt:asc',
  'id:desc', 'id:asc',
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

function safeId(value: string, label = '审核标识'): string {
  if (!SAFE_ID.test(value)) throw new RangeError(`${label}格式无效`);
  return encodeURIComponent(value);
}

function queryString(parameters: Record<string, unknown>): string {
  const query = new URLSearchParams();
  Object.entries(parameters).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value));
  });
  return query.size ? `?${query.toString()}` : '';
}

function validateQuery(
  parameters: Record<string, unknown>,
  allowedSorts: ReadonlySet<string>,
): void {
  const page = parameters.page;
  const pageSize = parameters.pageSize;
  const cursor = parameters.cursor;
  const sort = parameters.sort;
  if (typeof page === 'number' && page < 1) throw new RangeError('页码必须大于等于 1');
  if (typeof pageSize === 'number' && (pageSize < 1 || pageSize > 100)) {
    throw new RangeError('每页数量必须在 1 到 100 之间');
  }
  if (cursor && typeof page === 'number' && page !== 1) {
    throw new RangeError('页码和游标不能同时用于翻页');
  }
  if (typeof cursor === 'string' && cursor.length > 256) throw new RangeError('游标长度超出限制');
  if (typeof sort === 'string' && !allowedSorts.has(sort)) throw new RangeError('排序方式不在允许范围内');
  if (sort === 'priority:desc,createdAt:asc' && cursor) {
    throw new RangeError('默认优先级排序仅支持页码分页');
  }
}

export class AdminReviewsApi {
  constructor(
    private readonly transport: AdminSecurityApi = adminSecurityApi,
    private readonly session: AdminSession = adminSession,
    private readonly keys: IdempotencyKeyFactory = adminIdempotencyKeys,
  ) { }

  async queue(
    parameters: AdminReviewQueueParameters = {},
    options: AdminCallOptions = {},
  ): Promise<AdminReviewPage> {
    validateQuery(parameters as Record<string, unknown>, QUEUE_SORTS);
    const response = await this.read<Success<QueueOperation>>(
      `/admin-api/v1/reviews/queue${queryString(parameters as Record<string, unknown>)}`,
      options,
    );
    return response.data;
  }

  async detail(id: string, options: AdminCallOptions = {}): Promise<AdminReviewResource> {
    const response = await this.read<Success<DetailOperation>>(
      `/admin-api/v1/reviews/${safeId(id)}`, options,
    );
    return response.data;
  }

  async reports(
    parameters: AdminReportParameters = {},
    options: AdminCallOptions = {},
  ): Promise<AdminReportPage> {
    validateQuery(parameters as Record<string, unknown>, RELATED_SORTS);
    const response = await this.read<Success<ReportsOperation>>(
      `/admin-api/v1/content-reports${queryString(parameters as Record<string, unknown>)}`,
      options,
    );
    return response.data;
  }

  async appeals(
    parameters: AdminAppealParameters = {},
    options: AdminCallOptions = {},
  ): Promise<AdminAppealPage> {
    validateQuery(parameters as Record<string, unknown>, RELATED_SORTS);
    const response = await this.read<Success<AppealsOperation>>(
      `/admin-api/v1/appeals${queryString(parameters as Record<string, unknown>)}`,
      options,
    );
    return response.data;
  }

  async decide(
    id: string,
    body: AdminReviewDecisionRequest,
    options: AdminCallOptions = {},
  ): Promise<SuccessData<DecideOperation>> {
    return this.write<DecideOperation>(
      'adminReviewPostReviewsByIdDecide', id, 'decide', body, options,
    );
  }

  async assign(
    id: string,
    body: AdminReviewAssignRequest,
    options: AdminCallOptions = {},
  ): Promise<SuccessData<AssignOperation>> {
    return this.write<AssignOperation>(
      'adminReviewPostReviewsByIdAssign', id, 'assign', body, options,
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
    throw new Error('Unreachable admin review read retry state');
  }

  private async write<Op>(
    operation: ReviewWriteOperation,
    id: string,
    suffix: 'decide' | 'assign',
    body: unknown,
    options: AdminCallOptions,
  ): Promise<SuccessData<Op>> {
    const resourceId = safeId(id);
    const serialized = JSON.stringify(body);
    const usesFactory = options.idempotencyKey === undefined;
    const key = options.idempotencyKey
      ?? this.keys.current(operation, `${resourceId}/${suffix}\n${serialized}`, resourceId);
    const response = await this.transport.request<{ data: SuccessData<Op> }>(
      `/admin-api/v1/reviews/${resourceId}/${suffix}`,
      { method: 'POST', body: serialized },
      { ...options, token: options.token ?? this.session.accessToken, idempotencyKey: key },
    );
    if (usesFactory) this.keys.clear(operation, resourceId);
    return response.data;
  }
}

export const adminReviewsApi = new AdminReviewsApi();
