import type { AdminContract, RequestOptions } from '@hhy/api-client';
import { ApiRequestError, apiRequestErrorFromNetwork } from './apiError';
import { AdminSecurityApi, adminSecurityApi, type AdminCallOptions } from './adminSecurity';
import { AdminSession, adminSession } from './adminSession';
import { IdempotencyKeyFactory, adminIdempotencyKeys } from './idempotency';

type Operations = AdminContract.operations;
type ListOperation = Operations['adminProviderConfigGetProviderConfigs'];
type DetailOperation = Operations['adminProviderConfigGetProviderConfigsByProvider'];
type CreateOperation = Operations['adminProviderConfigPostProviderConfigsByProviderVersions'];
type TestOperation = Operations['adminProviderConfigPostProviderConfigsByProviderTest'];
type ActivateOperation = Operations['adminProviderConfigPostProviderConfigsByProviderActivate'];
type RollbackOperation = Operations['adminProviderConfigPostProviderConfigsByProviderRollback'];
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

export type ProviderConfigResource = AdminContract.components['schemas']['ProviderConfigResource'];
export type ProviderConfigPage = SuccessData<ListOperation>;
export type ProviderConfigListParameters = NonNullable<ListOperation['parameters']['query']>;
export type CreateProviderConfigVersionRequest = RequestBody<CreateOperation>;
export type TestProviderConfigRequest = RequestBody<TestOperation>;
export type ActivateProviderConfigRequest = RequestBody<ActivateOperation>;
export type RollbackProviderConfigRequest = RequestBody<RollbackOperation>;
export type ProviderConfigCommandOutcome = SuccessData<CreateOperation>;

type WriteOperation =
  | 'adminProviderConfigPostProviderConfigsByProviderVersions'
  | 'adminProviderConfigPostProviderConfigsByProviderTest'
  | 'adminProviderConfigPostProviderConfigsByProviderActivate'
  | 'adminProviderConfigPostProviderConfigsByProviderRollback';

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

export class AdminProviderConfigsApi {
  constructor(
    private readonly transport: AdminSecurityApi = adminSecurityApi,
    private readonly session: AdminSession = adminSession,
    private readonly keys: IdempotencyKeyFactory = adminIdempotencyKeys,
  ) { }

  async list(
    parameters: ProviderConfigListParameters = {},
    options: AdminCallOptions = {},
  ): Promise<ProviderConfigPage> {
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
    const query = new URLSearchParams();
    Object.entries(parameters).forEach(([key, value]) => {
      if (value !== undefined && value !== '') query.set(key, String(value));
    });
    const suffix = query.size ? `?${query.toString()}` : '';
    const response = await this.read<Success<ListOperation>>(
      `/admin-api/v1/provider-configs${suffix}`, options,
    );
    return response.data;
  }

  async get(provider: string, options: AdminCallOptions = {}): Promise<ProviderConfigResource> {
    const safeProvider = requireProvider(provider);
    const response = await this.read<Success<DetailOperation>>(
      `/admin-api/v1/provider-configs/${encodeURIComponent(safeProvider)}`, options,
    );
    return response.data;
  }

  createVersion(
    provider: string,
    body: CreateProviderConfigVersionRequest,
    options: AdminCallOptions = {},
  ): Promise<ProviderConfigCommandOutcome> {
    return this.write<CreateOperation>(
      'adminProviderConfigPostProviderConfigsByProviderVersions', provider,
      'versions', body, options,
    );
  }

  testConnection(
    provider: string,
    body: TestProviderConfigRequest,
    options: AdminCallOptions = {},
  ): Promise<SuccessData<TestOperation>> {
    return this.write<TestOperation>(
      'adminProviderConfigPostProviderConfigsByProviderTest', provider,
      'test', body, options,
    );
  }

  activate(
    provider: string,
    body: ActivateProviderConfigRequest,
    options: AdminCallOptions = {},
  ): Promise<SuccessData<ActivateOperation>> {
    return this.write<ActivateOperation>(
      'adminProviderConfigPostProviderConfigsByProviderActivate', provider,
      'activate', body, options,
    );
  }

  rollback(
    provider: string,
    body: RollbackProviderConfigRequest,
    options: AdminCallOptions = {},
  ): Promise<SuccessData<RollbackOperation>> {
    return this.write<RollbackOperation>(
      'adminProviderConfigPostProviderConfigsByProviderRollback', provider,
      'rollback', body, options,
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
        await wait(100 * (2 ** attempt), options.signal).catch((error) => {
          throw apiRequestErrorFromNetwork(error);
        });
      }
    }
    throw new Error('Unreachable provider config read retry state');
  }

  private async write<Operation>(
    operation: WriteOperation,
    provider: string,
    action: string,
    body: unknown,
    options: AdminCallOptions,
  ): Promise<SuccessData<Operation>> {
    const safeProvider = requireProvider(provider);
    const serialized = JSON.stringify(body);
    const generatedKey = options.idempotencyKey === undefined;
    const fingerprint = `POST\n${safeProvider}/${action}\n${serialized}`;
    const key = options.idempotencyKey ?? this.keys.current(operation, fingerprint);
    const response = await this.transport.request<{ data: SuccessData<Operation> }>(
      `/admin-api/v1/provider-configs/${encodeURIComponent(safeProvider)}/${action}`,
      { method: 'POST', body: serialized },
      { ...options, token: options.token ?? this.session.accessToken, idempotencyKey: key },
    );
    if (generatedKey) this.keys.clear(operation);
    return response.data;
  }
}

function requireProvider(provider: string): string {
  const value = provider.trim().toLowerCase();
  if (!/^[a-z][a-z0-9_-]{0,63}$/.test(value)) {
    throw new RangeError('供应商标识格式无效');
  }
  return value;
}

export const adminProviderConfigsApi = new AdminProviderConfigsApi();
