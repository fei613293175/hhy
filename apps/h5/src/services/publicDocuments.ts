import type { ClientContract } from '@hhy/api-client';

type AgreementOperation = ClientContract.operations['publicGetAgreementsByCode'];
type HelpOperation = ClientContract.operations['publicGetHelpArticlesById'];
type SuccessBody<Operation> = Operation extends {
  responses: { 200: { content: { 'application/json': infer Body } } };
} ? Body : never;

type AgreementResponse = SuccessBody<AgreementOperation>;
type HelpResponse = SuccessBody<HelpOperation>;
export type PublicDocumentPage = (AgreementResponse | HelpResponse) extends { data: infer Data } ? Data : never;

export interface PublicDocumentResult {
  page: PublicDocumentPage;
  requestId: string;
  timestamp?: string;
}

export class PublicDocumentApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly requestId = 'missing',
  ) {
    super(message);
    this.name = 'PublicDocumentApiError';
  }
}

function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isAbortError(value: unknown) {
  return record(value) && value.name === 'AbortError';
}

export class PublicDocumentApi {
  constructor(
    private readonly baseUrl: string,
    private readonly fetcher: typeof globalThis.fetch = globalThis.fetch.bind(globalThis),
  ) { }

  async getAgreement(code: string, signal?: AbortSignal) {
    if (code.length < 1 || code.length > 128 || /[\u0000-\u001f\u007f]/.test(code)) {
      throw new PublicDocumentApiError(400, 'COMMON-400-VALIDATION', '协议代码格式不正确');
    }
    return this.get(`/public-api/v1/agreements/${encodeURIComponent(code)}`, signal);
  }

  async getHelpArticle(id: string, signal?: AbortSignal) {
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(id)) {
      throw new PublicDocumentApiError(400, 'COMMON-400-VALIDATION', '帮助文章 ID 格式不正确');
    }
    return this.get(`/public-api/v1/help/articles/${encodeURIComponent(id)}`, signal);
  }

  private async get(path: string, signal?: AbortSignal): Promise<PublicDocumentResult> {
    let response: globalThis.Response;
    try {
      response = await this.fetcher(new URL(path, this.baseUrl), {
        headers: { Accept: 'application/json' },
        signal,
      });
    } catch (caught) {
      if (isAbortError(caught)) {
        throw new PublicDocumentApiError(0, 'REQUEST_ABORTED', '请求已取消');
      }
      throw new PublicDocumentApiError(0, 'NETWORK_ERROR', '网络连接失败，请检查网络后重试');
    }

    const payload: unknown = await response.json().catch(() => null);
    if (!response.ok) {
      const error = record(payload) && record(payload.error) ? payload.error : {};
      throw new PublicDocumentApiError(
        response.status,
        typeof error.code === 'string' ? error.code : `HTTP-${response.status}`,
        typeof error.message === 'string' ? error.message : '公开内容暂时无法加载',
        record(payload) && typeof payload.requestId === 'string' ? payload.requestId : 'missing',
      );
    }

    if (
      !record(payload)
      || payload.success !== true
      || typeof payload.requestId !== 'string'
      || !record(payload.data)
      || typeof payload.data.code !== 'string'
      || typeof payload.data.version !== 'number'
    ) {
      throw new PublicDocumentApiError(500, 'INVALID_RESPONSE', '公开内容响应无法确认');
    }

    return {
      page: payload.data as PublicDocumentPage,
      requestId: payload.requestId,
      timestamp: typeof payload.timestamp === 'string' ? payload.timestamp : undefined,
    };
  }
}

function defaultBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL;
  if (configured) return configured;
  return typeof window === 'undefined' ? 'http://localhost/' : window.location.origin;
}

export const publicDocumentApi = new PublicDocumentApi(defaultBaseUrl());
