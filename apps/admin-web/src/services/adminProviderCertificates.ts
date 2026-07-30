import type { AdminContract, RequestOptions } from '@hhy/api-client';
import { ApiRequestError, apiRequestErrorFromNetwork } from './apiError';
import { AdminSecurityApi, adminSecurityApi, type AdminCallOptions } from './adminSecurity';
import { AdminSession, adminSession } from './adminSession';
import { IdempotencyKeyFactory, adminIdempotencyKeys } from './idempotency';

type Operations = AdminContract.operations;
type ListOperation = Operations['adminProviderCertificateGetProviderCertificates'];
type UploadOperation = Operations['adminProviderCertificatePostProviderCertificates'];
type RotateOperation = Operations['adminProviderCertificatePostProviderCertificatesByIdRotate'];
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

export type ProviderCertificate = AdminContract.components['schemas']['CertificateResource'];
export type ProviderCertificatePage = SuccessData<ListOperation>;
export type ProviderCertificateListParameters = NonNullable<ListOperation['parameters']['query']>;
export type UploadProviderCertificateRequest = RequestBody<UploadOperation>;
export type RotateProviderCertificateRequest = RequestBody<RotateOperation>;
export type ProviderCertificateCommandOutcome = SuccessData<UploadOperation>;

type WriteOperation =
  | 'adminProviderCertificatePostProviderCertificates'
  | 'adminProviderCertificatePostProviderCertificatesByIdRotate';

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

export class AdminProviderCertificatesApi {
  constructor(
    private readonly transport: AdminSecurityApi = adminSecurityApi,
    private readonly session: AdminSession = adminSession,
    private readonly keys: IdempotencyKeyFactory = adminIdempotencyKeys,
  ) { }

  async list(
    parameters: ProviderCertificateListParameters = {},
    options: AdminCallOptions = {},
  ): Promise<ProviderCertificatePage> {
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
        const response = await this.transport.request<Success<ListOperation>>(
          `/admin-api/v1/provider-certificates${suffix}`,
          { method: 'GET' },
          authenticated,
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
    throw new Error('Unreachable provider certificate read retry state');
  }

  upload(
    body: UploadProviderCertificateRequest,
    options: AdminCallOptions = {},
  ): Promise<ProviderCertificateCommandOutcome> {
    return this.write<UploadOperation>(
      'adminProviderCertificatePostProviderCertificates',
      '/admin-api/v1/provider-certificates', body, options,
    );
  }

  rotate(
    id: string,
    body: RotateProviderCertificateRequest,
    options: AdminCallOptions = {},
  ): Promise<SuccessData<RotateOperation>> {
    const safeId = requireId(id);
    return this.write<RotateOperation>(
      'adminProviderCertificatePostProviderCertificatesByIdRotate',
      `/admin-api/v1/provider-certificates/${encodeURIComponent(safeId)}/rotate`,
      body, options,
    );
  }

  private async write<Operation>(
    operation: WriteOperation,
    path: string,
    body: unknown,
    options: AdminCallOptions,
  ): Promise<SuccessData<Operation>> {
    const serialized = JSON.stringify(body);
    const generatedKey = options.idempotencyKey === undefined;
    const key = options.idempotencyKey
      ?? this.keys.current(operation, `POST\n${path}\n${serialized}`);
    const response = await this.transport.request<{ data: SuccessData<Operation> }>(
      path,
      { method: 'POST', body: serialized },
      { ...options, token: options.token ?? this.session.accessToken, idempotencyKey: key },
    );
    if (generatedKey) this.keys.clear(operation);
    return response.data;
  }
}

function validateParameters(parameters: ProviderCertificateListParameters): void {
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

function requireId(id: string): string {
  const value = id.trim();
  if (!/^[A-Za-z][A-Za-z0-9_-]{0,63}$/.test(value)) {
    throw new RangeError('供应商证书标识格式无效');
  }
  return value;
}

export const adminProviderCertificatesApi = new AdminProviderCertificatesApi();
