import type { ClientContract } from '@hhy/api-client';

type Operation = ClientContract.operations['publicGetShareContentsById'];
type Response = Operation extends {
  responses: { 200: { content: { 'application/json': infer Body } } };
} ? Body : never;

export type PublicSharePage = Response extends { data: infer Data } ? Data : never;

export class PublicShareApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly requestId = 'missing',
  ) {
    super(message);
    this.name = 'PublicShareApiError';
  }
}

function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export class PublicShareApi {
  constructor(
    private readonly baseUrl: string,
    private readonly fetcher: typeof globalThis.fetch = globalThis.fetch.bind(globalThis),
  ) { }

  async get(contentId: string, signal?: AbortSignal): Promise<PublicSharePage> {
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(contentId)) {
      throw new PublicShareApiError(400, 'COMMON-400-VALIDATION', '分享链接格式不正确');
    }
    let response: globalThis.Response;
    try {
      response = await this.fetcher(
        new URL(`/public-api/v1/share/contents/${encodeURIComponent(contentId)}`, this.baseUrl),
        { headers: { Accept: 'application/json' }, signal },
      );
    } catch (caught) {
      if (caught instanceof DOMException && caught.name === 'AbortError') {
        throw new PublicShareApiError(0, 'REQUEST_ABORTED', '请求已取消');
      }
      throw new PublicShareApiError(0, 'NETWORK_ERROR', '网络连接失败，请检查网络后重试');
    }

    const payload: unknown = await response.json().catch(() => null);
    if (!response.ok) {
      const error = record(payload) && record(payload.error) ? payload.error : {};
      throw new PublicShareApiError(
        response.status,
        typeof error.code === 'string' ? error.code : `HTTP-${response.status}`,
        typeof error.message === 'string' ? error.message : response.status === 404 ? '分享内容不存在或已失效' : '分享内容暂时无法加载',
        record(payload) && typeof payload.requestId === 'string' ? payload.requestId : 'missing',
      );
    }
    if (!record(payload) || !record(payload.data) || typeof payload.data.code !== 'string' || typeof payload.data.version !== 'number') {
      throw new PublicShareApiError(500, 'INVALID_RESPONSE', '分享内容暂时无法确认');
    }
    return payload.data as PublicSharePage;
  }
}

function defaultBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL;
  if (configured) return configured;
  return typeof window === 'undefined' ? 'http://localhost/' : window.location.origin;
}

export const publicShareApi = new PublicShareApi(defaultBaseUrl());
