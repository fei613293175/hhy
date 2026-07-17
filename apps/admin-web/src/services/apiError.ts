import type { AdminContract } from '@hhy/api-client';

type ErrorResponse = AdminContract.components['schemas']['ErrorResponse'];
export type ApiErrorDetail = NonNullable<ErrorResponse['error']['details']>[number];

export interface ApiRequestErrorInit {
  status: number;
  code: string;
  message: string;
  requestId: string;
  retryAfter?: number;
  retryable?: boolean;
  details?: ApiErrorDetail[];
}

export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: string;
  readonly requestId: string;
  readonly retryAfter?: number;
  readonly retryable?: boolean;
  readonly details: readonly ApiErrorDetail[];

  constructor(init: ApiRequestErrorInit) {
    super(init.message);
    this.name = 'ApiRequestError';
    this.status = init.status;
    this.code = init.code;
    this.requestId = init.requestId;
    this.retryAfter = init.retryAfter;
    this.retryable = init.retryable;
    this.details = [...(init.details ?? [])];
  }
}

export function isApiRequestError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function errorResponse(value: unknown): ErrorResponse | undefined {
  if (!isRecord(value) || value.success !== false || typeof value.requestId !== 'string') {
    return undefined;
  }
  if (!isRecord(value.error)
      || typeof value.error.code !== 'string'
      || typeof value.error.message !== 'string') {
    return undefined;
  }
  return value as ErrorResponse;
}

function retryAfterSeconds(value: string | null): number | undefined {
  if (value === null) return undefined;
  if (/^\d+$/.test(value)) return Number(value);
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp)) return undefined;
  return Math.max(0, Math.ceil((timestamp - Date.now()) / 1_000));
}

export function apiRequestErrorFromResponse(response: Response, payload: unknown): ApiRequestError {
  const parsed = errorResponse(payload);
  return new ApiRequestError({
    status: response.status,
    code: parsed?.error.code ?? `HTTP-${response.status}`,
    message: (parsed?.error.message ?? response.statusText) || '请求失败',
    requestId: parsed?.requestId ?? response.headers.get('X-Request-Id') ?? 'missing',
    retryAfter: retryAfterSeconds(response.headers.get('Retry-After')),
    retryable: parsed?.error.retryable,
    details: parsed?.error.details,
  });
}

export function apiRequestErrorFromNetwork(error: unknown): ApiRequestError {
  const aborted = error instanceof DOMException && error.name === 'AbortError';
  return new ApiRequestError({
    status: 0,
    code: aborted ? 'REQUEST_ABORTED' : 'NETWORK_ERROR',
    message: aborted ? '请求已取消' : '网络请求失败',
    requestId: 'missing',
  });
}

export function apiRequestErrorFromTimeout(): ApiRequestError {
  return new ApiRequestError({
    status: 0,
    code: 'NETWORK_ERROR',
    message: '网络请求超时，请稍后重试',
    requestId: 'missing',
    retryable: true,
  });
}
