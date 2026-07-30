import {
  ApiClient,
  type AdminContract,
  type RequestOptions,
} from '@hhy/api-client';
import {
  ApiRequestError,
  apiRequestErrorFromNetwork,
  apiRequestErrorFromResponse,
  apiRequestErrorFromTimeout,
} from './apiError';
import { AdminSession, adminSession, type AdminSessionResource } from './adminSession';
import {
  IdempotencyKeyFactory,
  adminIdempotencyKeys,
  type AdminSecurityIdempotentOperation,
} from './idempotency';

type OperationId = keyof AdminContract.operations;
type Operation<Id extends OperationId> = AdminContract.operations[Id];
type JsonContent<Value> = Value extends {
  content: { 'application/json': infer Json };
} ? Json : never;
type OperationRequest<Id extends OperationId> = Operation<Id> extends {
  requestBody?: infer RequestBody;
} ? JsonContent<NonNullable<RequestBody>> : never;
type OperationSuccess<Id extends OperationId> = Operation<Id> extends {
  responses: { 200: infer Response };
} ? JsonContent<Response> : never;
type OperationData<Id extends OperationId> = OperationSuccess<Id> extends {
  data: infer Data;
} ? Data : never;

type LoginOperation = 'adminAdminAuthPostAuthLogin';
type VerifyMfaOperation = 'adminAdminAuthPostAuthMfaVerify';
type LogoutOperation = 'adminAdminAuthPostAuthLogout';
type GetSecurityOperation = 'adminSelfGetSecurity';
type ChangePasswordOperation = 'adminSelfPostPasswordChange';
type EnrollMfaOperation = 'adminSelfPostMfaEnroll';
type ConfirmMfaOperation = 'adminSelfPostMfaConfirm';
type DisableMfaOperation = 'adminSelfPostMfaDisable';

export type AdminLoginRequest = OperationRequest<LoginOperation>;
export type AdminMfaVerifyRequest = OperationRequest<VerifyMfaOperation>;
export type AdminLogoutRequest = OperationRequest<LogoutOperation>;
export type AdminPasswordChangeRequest = OperationRequest<ChangePasswordOperation>;
export type AdminMfaConfirmRequest = OperationRequest<ConfirmMfaOperation>;
export type AdminMfaDisableRequest = OperationRequest<DisableMfaOperation>;
export type AdminSelfSecurityResource = OperationData<GetSecurityOperation>;
export type AdminMfaEnrollmentResource = OperationData<EnrollMfaOperation>;
export type AdminCommandResultResource =
  AdminContract.components['schemas']['CommandResultResource'];

export interface AdminCallOptions extends RequestOptions {
  timeoutMs?: number;
}

const DEFAULT_REQUEST_TIMEOUT_MS = 10_000;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function requireAdminSession(value: unknown, requestId: string): AdminSessionResource {
  if (isRecord(value) && typeof value.adminUserId === 'string') {
    return value as AdminSessionResource;
  }
  throw invalidSuccessResponse(requestId);
}

function requireCommandResult(value: unknown, requestId: string): AdminCommandResultResource {
  if (isRecord(value) && typeof value.status === 'string') {
    return value as AdminCommandResultResource;
  }
  throw invalidSuccessResponse(requestId);
}

function invalidSuccessResponse(requestId: string): ApiRequestError {
  return new ApiRequestError({
    status: 502,
    code: 'INVALID_API_RESPONSE',
    message: '服务端返回了不兼容的响应',
    requestId,
  });
}

function requestIntentFingerprint(init: RequestInit): string {
  const input = `${init.method ?? 'GET'}\n${typeof init.body === 'string' ? init.body : ''}`;
  let hash = 0x811c9dc5;
  for (let index = 0; index < input.length; index += 1) {
    hash ^= input.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(16).padStart(8, '0');
}

function retryDelay(milliseconds: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Aborted', 'AbortError'));
      return;
    }
    const onAbort = () => {
      globalThis.clearTimeout(timeout);
      reject(new DOMException('Aborted', 'AbortError'));
    };
    const timeout = globalThis.setTimeout(() => {
      signal?.removeEventListener('abort', onAbort);
      resolve();
    }, milliseconds);
    signal?.addEventListener('abort', onAbort, { once: true });
  });
}

export class AdminSecurityApi extends ApiClient {
  constructor(
    private readonly adminBaseUrl: string,
    private readonly session: AdminSession = adminSession,
    private readonly keys: IdempotencyKeyFactory = adminIdempotencyKeys,
    private readonly requestTimeoutMs: number = DEFAULT_REQUEST_TIMEOUT_MS,
  ) {
    super(adminBaseUrl);
  }

  override async request<T>(
    path: string,
    init: RequestInit = {},
    options: AdminCallOptions = {},
  ): Promise<T> {
    const headers = new Headers(init.headers);
    headers.set('Accept', 'application/json');
    if (init.body !== undefined && init.body !== null) {
      headers.set('Content-Type', 'application/json');
    }
    if (options.token) headers.set('Authorization', `Bearer ${options.token}`);
    if (options.idempotencyKey) {
      headers.set('X-Idempotency-Key', options.idempotencyKey);
    }

    const controller = new AbortController();
    let timedOut = false;
    const timeoutMs = options.timeoutMs ?? this.requestTimeoutMs;
    const onExternalAbort = () => controller.abort(options.signal?.reason);
    if (options.signal?.aborted) onExternalAbort();
    else options.signal?.addEventListener('abort', onExternalAbort, { once: true });
    const timeout = globalThis.setTimeout(() => {
      timedOut = true;
      controller.abort(new DOMException('Timed out', 'TimeoutError'));
    }, timeoutMs);

    let response: Response;
    try {
      response = await fetch(new URL(path, this.adminBaseUrl), {
        ...init,
        headers,
        signal: controller.signal,
      });
    } catch (error) {
      if (timedOut) throw apiRequestErrorFromTimeout();
      throw apiRequestErrorFromNetwork(error);
    } finally {
      globalThis.clearTimeout(timeout);
      options.signal?.removeEventListener('abort', onExternalAbort);
    }

    const payload: unknown = await response.json().catch(() => null);
    if (!response.ok) {
      const error = apiRequestErrorFromResponse(response, payload);
      if (error.status === 401) this.session.clear();
      throw error;
    }
    return payload as T;
  }

  async login(
    body: AdminLoginRequest,
    options: AdminCallOptions = {},
  ): Promise<AdminSessionResource> {
    const response = await this.idempotent<LoginOperation>(
      'adminAdminAuthPostAuthLogin',
      '/admin-api/v1/auth/login',
      { method: 'POST', body: JSON.stringify(body) },
      options,
    );
    const result = requireAdminSession(response.data, response.requestId);
    this.session.apply(result);
    return result;
  }

  async verifyMfa(
    body: AdminMfaVerifyRequest,
    options: AdminCallOptions = {},
  ): Promise<AdminSessionResource> {
    const response = await this.idempotent<VerifyMfaOperation>(
      'adminAdminAuthPostAuthMfaVerify',
      '/admin-api/v1/auth/mfa/verify',
      {
        method: 'POST',
        headers: { 'X-MFA-Ticket': body.mfaTicket },
        body: JSON.stringify(body),
      },
      options,
    );
    const result = requireAdminSession(response.data, response.requestId);
    this.session.apply(result);
    return result;
  }

  async logout(
    body?: AdminLogoutRequest,
    options: AdminCallOptions = {},
  ): Promise<AdminCommandResultResource> {
    const response = await this.idempotent<LogoutOperation>(
      'adminAdminAuthPostAuthLogout',
      '/admin-api/v1/auth/logout',
      { method: 'POST', ...(body === undefined ? {} : { body: JSON.stringify(body) }) },
      this.authenticated(options),
    );
    const result = requireCommandResult(response.data, response.requestId);
    this.session.clear();
    return result;
  }

  async getSecurity(
    options: AdminCallOptions = {},
  ): Promise<AdminSelfSecurityResource> {
    for (let attempt = 0; attempt < 3; attempt += 1) {
      try {
        const response = await this.request<OperationSuccess<GetSecurityOperation>>(
          '/admin-api/v1/me/security',
          { method: 'GET' },
          this.authenticated(options),
        );
        return response.data;
      } catch (caught) {
        const canRetry = caught instanceof ApiRequestError
          && caught.code === 'NETWORK_ERROR'
          && attempt < 2;
        if (!canRetry) throw caught;
        await retryDelay(100 * (2 ** attempt), options.signal).catch((error) => {
          throw apiRequestErrorFromNetwork(error);
        });
      }
    }
    throw new Error('Unreachable security retry state');
  }

  async changePassword(
    body: AdminPasswordChangeRequest,
    options: AdminCallOptions = {},
  ): Promise<AdminSelfSecurityResource> {
    const response = await this.idempotent<ChangePasswordOperation>(
      'adminSelfPostPasswordChange',
      '/admin-api/v1/me/security/password/change',
      { method: 'POST', body: JSON.stringify(body) },
      this.authenticated(options),
    );
    this.session.clear();
    return response.data;
  }

  async enrollMfa(
    options: AdminCallOptions = {},
  ): Promise<AdminMfaEnrollmentResource> {
    const response = await this.idempotent<EnrollMfaOperation>(
      'adminSelfPostMfaEnroll',
      '/admin-api/v1/me/security/mfa/enroll',
      { method: 'POST' },
      this.authenticated(options),
    );
    return response.data;
  }

  async confirmMfa(
    body: AdminMfaConfirmRequest,
    options: AdminCallOptions = {},
  ): Promise<AdminSelfSecurityResource> {
    const response = await this.idempotent<ConfirmMfaOperation>(
      'adminSelfPostMfaConfirm',
      '/admin-api/v1/me/security/mfa/confirm',
      { method: 'POST', body: JSON.stringify(body) },
      this.authenticated(options),
    );
    return response.data;
  }

  async disableMfa(
    body: AdminMfaDisableRequest,
    options: AdminCallOptions = {},
  ): Promise<AdminSelfSecurityResource> {
    const response = await this.idempotent<DisableMfaOperation>(
      'adminSelfPostMfaDisable',
      '/admin-api/v1/me/security/mfa/disable',
      { method: 'POST', body: JSON.stringify(body) },
      this.authenticated(options),
    );
    return response.data;
  }

  private authenticated(options: AdminCallOptions): RequestOptions {
    return {
      ...options,
      token: options.token ?? this.session.accessToken,
    };
  }

  private async idempotent<Id extends AdminSecurityIdempotentOperation>(
    operation: Id,
    path: string,
    init: RequestInit,
    options: AdminCallOptions,
  ): Promise<OperationSuccess<Id>> {
    const usesFactory = options.idempotencyKey === undefined;
    const intentFingerprint = requestIntentFingerprint(init);
    const idempotencyKey = options.idempotencyKey
      ?? this.keys.current(operation, intentFingerprint);
    const response = await this.request<OperationSuccess<Id>>(path, init, {
      ...options,
      idempotencyKey,
    });
    if (usesFactory) this.keys.clear(operation);
    return response;
  }
}

function defaultBaseUrl(): string {
  return typeof window === 'undefined' ? 'http://localhost/' : window.location.origin;
}

export function createAdminSecurityApi(
  baseUrl: string,
  session: AdminSession = adminSession,
  keys: IdempotencyKeyFactory = adminIdempotencyKeys,
): AdminSecurityApi {
  return new AdminSecurityApi(baseUrl, session, keys);
}

export const adminSecurityApi = createAdminSecurityApi(defaultBaseUrl());
