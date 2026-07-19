import type { ClientContract } from '@hhy/api-client';

type Operation = ClientContract.operations['publicPostIdentityCallbackConsume'];
type Request = Operation extends {
  requestBody: { content: { 'application/json': infer Body } };
} ? Body : never;
type Response = Operation extends {
  responses: { 200: { content: { 'application/json': infer Body } } };
} ? Body : never;

export type IdentityCallbackStatus = Response extends { data: infer Data } ? Data : never;

export class IdentityCallbackApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) {
    super(message);
    this.name = 'IdentityCallbackApiError';
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export class IdentityCallbackApi {
  constructor(
    private readonly baseUrl: string,
    private readonly fetcher: typeof globalThis.fetch = globalThis.fetch.bind(globalThis),
  ) { }

  async consume(state: string, signal?: AbortSignal): Promise<IdentityCallbackStatus> {
    const body: Request = { state };
    let response: globalThis.Response;
    try {
      response = await this.fetcher(
        new URL('/public-api/v1/identity/callback/consume', this.baseUrl),
        {
          method: 'POST',
          headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
          body: JSON.stringify(body),
          signal,
        },
      );
    } catch (caught) {
      if (caught instanceof DOMException && caught.name === 'AbortError') {
        throw new IdentityCallbackApiError(0, 'REQUEST_ABORTED', '请求已取消');
      }
      throw new IdentityCallbackApiError(0, 'NETWORK_ERROR', '网络连接失败');
    }
    const payload: unknown = await response.json().catch(() => null);
    if (!response.ok) {
      const error = isRecord(payload) && isRecord(payload.error) ? payload.error : {};
      throw new IdentityCallbackApiError(
        response.status,
        typeof error.code === 'string' ? error.code : `HTTP-${response.status}`,
        typeof error.message === 'string' ? error.message : '认证状态暂时无法确认',
      );
    }
    if (!isRecord(payload) || !isRecord(payload.data) || typeof payload.data.status !== 'string') {
      throw new IdentityCallbackApiError(500, 'INVALID_RESPONSE', '认证状态暂时无法确认');
    }
    return payload.data as IdentityCallbackStatus;
  }
}

function defaultBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL;
  if (configured) return configured;
  return typeof window === 'undefined' ? 'http://localhost/' : window.location.origin;
}

export const identityCallbackApi = new IdentityCallbackApi(defaultBaseUrl());
