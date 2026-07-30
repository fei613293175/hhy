import type { AdminContract, RequestOptions } from '@hhy/api-client';
import { ApiRequestError, apiRequestErrorFromNetwork } from './apiError';
import { AdminSecurityApi, adminSecurityApi, type AdminCallOptions } from './adminSecurity';
import { AdminSession, adminSession } from './adminSession';
import { IdempotencyKeyFactory, adminIdempotencyKeys } from './idempotency';

type Operations = AdminContract.operations;
type ListOperation = Operations['adminDomainConfigGetDomains'];
type DnsActionsOperation = Operations['adminDomainConfigGetDomainsDnsActions'];
type UpdateOperation = Operations['adminDomainConfigPutDomainsByCode'];
type VerifyOperation = Operations['adminDomainConfigPostDomainsByCodeVerify'];
type JsonResponse<Value> = Value extends {
  content: { 'application/json': infer Json };
} ? Json : never;
type Success<Operation> = Operation extends {
  responses: { 200: infer Response };
} ? JsonResponse<Response> : never;
type SuccessData<Operation> = Success<Operation> extends { data: infer Data } ? Data : never;
type RequestBody<Operation> = Operation extends {
  requestBody: { content: { 'application/json': infer Body } };
} ? Body : never;

export type DomainResource = AdminContract.components['schemas']['DomainResource'];
export type DomainPage = SuccessData<ListOperation>;
export type DomainListParameters = NonNullable<ListOperation['parameters']['query']>;
export type UpdateDomainRequest = RequestBody<UpdateOperation>;
export type VerifyDomainRequest = RequestBody<VerifyOperation>;
export type DomainCommandOutcome = SuccessData<UpdateOperation>;

type WriteOperation =
  | 'adminDomainConfigPutDomainsByCode'
  | 'adminDomainConfigPostDomainsByCodeVerify';

function wait(milliseconds: number, signal?: AbortSignal): Promise<void> {
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

export class AdminDomainsApi {
  constructor(
    private readonly transport: AdminSecurityApi = adminSecurityApi,
    private readonly session: AdminSession = adminSession,
    private readonly keys: IdempotencyKeyFactory = adminIdempotencyKeys,
  ) { }

  list(
    parameters: DomainListParameters = {},
    options: AdminCallOptions = {},
  ): Promise<DomainPage> {
    return this.readPage<ListOperation>('/admin-api/v1/domains', parameters, options);
  }

  dnsActions(
    parameters: DomainListParameters = {},
    options: AdminCallOptions = {},
  ): Promise<SuccessData<DnsActionsOperation>> {
    return this.readPage<DnsActionsOperation>(
      '/admin-api/v1/domains/dns-actions', parameters, options,
    );
  }

  update(
    code: string,
    body: UpdateDomainRequest,
    options: AdminCallOptions = {},
  ): Promise<DomainCommandOutcome> {
    return this.write<UpdateOperation>(
      'adminDomainConfigPutDomainsByCode', 'PUT', code, '', body, options,
    );
  }

  verify(
    code: string,
    body: VerifyDomainRequest,
    options: AdminCallOptions = {},
  ): Promise<SuccessData<VerifyOperation>> {
    return this.write<VerifyOperation>(
      'adminDomainConfigPostDomainsByCodeVerify', 'POST', code, '/verify', body, options,
    );
  }

  private async readPage<Operation>(
    path: string,
    parameters: DomainListParameters,
    options: AdminCallOptions,
  ): Promise<SuccessData<Operation>> {
    validateParameters(parameters);
    const query = new URLSearchParams();
    Object.entries(parameters).forEach(([key, value]) => {
      if (value !== undefined && value !== '') query.set(key, String(value));
    });
    const suffix = query.size ? `?${query.toString()}` : '';
    const authenticated: RequestOptions = {
      ...options,
      token: options.token ?? this.session.accessToken,
    };
    for (let attempt = 0; attempt < 3; attempt += 1) {
      try {
        const response = await this.transport.request<{ data: SuccessData<Operation> }>(
          `${path}${suffix}`, { method: 'GET' }, authenticated,
        );
        return response.data;
      } catch (caught) {
        const retry = caught instanceof ApiRequestError
          && caught.code === 'NETWORK_ERROR'
          && attempt < 2;
        if (!retry) throw caught;
        await wait(100 * (2 ** attempt), options.signal).catch((error) => {
          throw apiRequestErrorFromNetwork(error);
        });
      }
    }
    throw new Error('Unreachable domain read retry state');
  }

  private async write<Operation>(
    operation: WriteOperation,
    method: 'PUT' | 'POST',
    code: string,
    suffix: string,
    body: unknown,
    options: AdminCallOptions,
  ): Promise<SuccessData<Operation>> {
    const safeCode = requireCode(code);
    const serialized = JSON.stringify(body);
    const generatedKey = options.idempotencyKey === undefined;
    const fingerprint = `${method}\n${safeCode}${suffix}\n${serialized}`;
    const key = options.idempotencyKey ?? this.keys.current(operation, fingerprint);
    const response = await this.transport.request<{ data: SuccessData<Operation> }>(
      `/admin-api/v1/domains/${encodeURIComponent(safeCode)}${suffix}`,
      { method, body: serialized },
      { ...options, token: options.token ?? this.session.accessToken, idempotencyKey: key },
    );
    if (generatedKey) this.keys.clear(operation);
    return response.data;
  }
}

function validateParameters(parameters: DomainListParameters): void {
  if (parameters.page !== undefined && parameters.page < 1) {
    throw new RangeError('页码必须大于等于 1');
  }
  if (parameters.pageSize !== undefined
    && (parameters.pageSize < 1 || parameters.pageSize > 100)) {
    throw new RangeError('每页数量必须在 1 到 100 之间');
  }
  if (parameters.cursor && parameters.page !== undefined) {
    throw new RangeError('游标与页码不能同时使用');
  }
}

function requireCode(code: string): string {
  const value = code.trim().toLowerCase();
  if (!/^[a-z][a-z0-9_]{1,31}$/.test(value)) {
    throw new RangeError('域名代码格式无效');
  }
  return value;
}

export const adminDomainsApi = new AdminDomainsApi();
