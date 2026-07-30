import type { ClientContract } from '@hhy/api-client';

type OperationId = keyof ClientContract.operations;
type Operation<Id extends OperationId> = ClientContract.operations[Id];
type JsonContent<Value> = Value extends {
  content: { 'application/json': infer Json };
} ? Json : never;
type RequestBody<Id extends OperationId> = Operation<Id> extends {
  requestBody: { content: { 'application/json': infer Body } };
} ? Body : never;
type Success<Id extends OperationId> = Operation<Id> extends {
  responses: { 200: infer Response };
} ? JsonContent<Response> : never;
type SuccessData<Id extends OperationId> = Success<Id> extends { data: infer Data } ? Data : never;

type ChallengeOperation = 'authPostAuthSecurityChallenges';
type SmsOperation = 'authPostAuthSmsSend';
type RegisterOperation = 'authPostAuthRegister';
type ConfigOperation = 'publicGetInviteByCodeRegistrationConfig';
type WriteOperation = ChallengeOperation | SmsOperation | RegisterOperation;

export type SecurityChallengeRequest = RequestBody<ChallengeOperation>;
export type SmsSendRequest = RequestBody<SmsOperation>;
export type RegisterRequest = RequestBody<RegisterOperation>;
export type ChallengeResource = SuccessData<ChallengeOperation>;
export type SmsResult = SuccessData<SmsOperation>;
export type RegisterResult = SuccessData<RegisterOperation>;
export type InviteRegistrationConfig = SuccessData<ConfigOperation>;
export type PublicPageBlock = ClientContract.components['schemas']['PublicPageBlockResource'];

export interface InviteCallOptions {
  signal?: AbortSignal;
  idempotencyKey?: string;
}

export class InviteApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly requestId = 'missing',
    readonly retryable = false,
    readonly retryAfterSeconds?: number,
  ) {
    super(message);
    this.name = 'InviteApiError';
  }
}

type Fetcher = typeof globalThis.fetch;

function secureUuid(): string {
  if (typeof globalThis.crypto?.randomUUID !== 'function') {
    throw new Error('当前浏览器不支持安全随机数，无法提交注册请求');
  }
  return globalThis.crypto.randomUUID();
}

class IntentKeys {
  private readonly values = new Map<WriteOperation, { fingerprint: string; key: string }>();

  current(operation: WriteOperation, fingerprint: string): string {
    const current = this.values.get(operation);
    if (current?.fingerprint === fingerprint) return current.key;
    const key = secureUuid();
    this.values.set(operation, { fingerprint, key });
    return key;
  }

  clear(operation: WriteOperation): void {
    this.values.delete(operation);
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function parseRetryAfter(value: string | null): number | undefined {
  if (value === null) return undefined;
  const seconds = Number(value);
  return Number.isFinite(seconds) && seconds >= 0 ? seconds : undefined;
}

function networkError(caught: unknown): InviteApiError {
  if (caught instanceof DOMException && caught.name === 'AbortError') {
    return new InviteApiError(0, 'REQUEST_ABORTED', '请求已取消');
  }
  return new InviteApiError(0, 'NETWORK_ERROR', '网络连接失败，请检查网络后重试', 'missing', true);
}

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

export class InviteRegistrationApi {
  private readonly keys = new IntentKeys();

  constructor(
    private readonly baseUrl: string,
    private readonly fetcher: Fetcher = globalThis.fetch.bind(globalThis),
  ) { }

  async getConfig(inviteCode: string, options: InviteCallOptions = {}): Promise<InviteRegistrationConfig> {
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(inviteCode)) {
      throw new InviteApiError(404, 'COMMON-404-NOT_FOUND', '邀请码不存在或已经失效');
    }
    for (let attempt = 0; attempt < 3; attempt += 1) {
      try {
        const response = await this.request<Success<ConfigOperation>>(
          `/public-api/v1/invite/${encodeURIComponent(inviteCode)}/registration-config`,
          { method: 'GET' }, options,
        );
        return response.data;
      } catch (caught) {
        const retry = caught instanceof InviteApiError
          && caught.code === 'NETWORK_ERROR'
          && attempt < 2;
        if (!retry) throw caught;
        await delay(100 * (2 ** attempt), options.signal).catch((error) => {
          throw networkError(error);
        });
      }
    }
    throw new Error('Unreachable invite configuration retry state');
  }

  createChallenge(
    body: SecurityChallengeRequest,
    options: InviteCallOptions = {},
  ): Promise<ChallengeResource> {
    return this.write<ChallengeOperation>(
      'authPostAuthSecurityChallenges', '/api/v1/auth/security-challenges', body, options,
    );
  }

  sendSms(body: SmsSendRequest, options: InviteCallOptions = {}): Promise<SmsResult> {
    return this.write<SmsOperation>(
      'authPostAuthSmsSend', '/api/v1/auth/sms/send', body, options,
    );
  }

  register(body: RegisterRequest, options: InviteCallOptions = {}): Promise<RegisterResult> {
    return this.write<RegisterOperation>(
      'authPostAuthRegister', '/api/v1/auth/register', body, options,
    );
  }

  private async write<Id extends WriteOperation>(
    operation: Id,
    path: string,
    body: RequestBody<Id>,
    options: InviteCallOptions,
  ): Promise<SuccessData<Id>> {
    const serialized = JSON.stringify(body);
    const usesFactory = options.idempotencyKey === undefined;
    const idempotencyKey = options.idempotencyKey ?? this.keys.current(operation, serialized);
    const response = await this.request<{ data: SuccessData<Id> }>(
      path,
      { method: 'POST', body: serialized },
      { ...options, idempotencyKey },
    );
    if (usesFactory) this.keys.clear(operation);
    return response.data;
  }

  private async request<T>(
    path: string,
    init: RequestInit,
    options: InviteCallOptions,
  ): Promise<T> {
    const headers = new Headers(init.headers);
    headers.set('Accept', 'application/json');
    if (init.body !== undefined) headers.set('Content-Type', 'application/json');
    if (options.idempotencyKey) headers.set('X-Idempotency-Key', options.idempotencyKey);
    let response: Response;
    try {
      response = await this.fetcher(new URL(path, this.baseUrl), {
        ...init,
        headers,
        signal: options.signal,
      });
    } catch (caught) {
      throw networkError(caught);
    }
    const payload: unknown = await response.json().catch(() => null);
    if (!response.ok) {
      const error = isRecord(payload) && isRecord(payload.error) ? payload.error : {};
      throw new InviteApiError(
        response.status,
        typeof error.code === 'string' ? error.code : `HTTP-${response.status}`,
        typeof error.message === 'string' ? error.message : '请求失败，请稍后重试',
        response.headers.get('X-Request-Id')
          ?? (isRecord(payload) && typeof payload.requestId === 'string' ? payload.requestId : 'missing'),
        error.retryable === true,
        parseRetryAfter(response.headers.get('Retry-After')),
      );
    }
    return payload as T;
  }
}

function defaultBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL;
  if (configured) return configured;
  return typeof window === 'undefined' ? 'http://localhost/' : window.location.origin;
}

export const inviteRegistrationApi = new InviteRegistrationApi(defaultBaseUrl());
