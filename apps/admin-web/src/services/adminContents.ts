import type { AdminContract } from '@hhy/api-client';
import { adminSecurityApi, type AdminCallOptions } from './adminSecurity';
import { adminSession } from './adminSession';
import { adminIdempotencyKeys } from './idempotency';

type JsonResponse<Value> = Value extends { content: { 'application/json': infer Json } } ? Json : never;
type Success<Operation> = Operation extends { responses: { 200: infer Response } } ? JsonResponse<Response> : never;
type Operation<Id extends keyof AdminContract.operations> = AdminContract.operations[Id];

export type AdminContentResource = AdminContract.components['schemas']['ContentResource'];
export type AdminContentListParameters = NonNullable<Operation<'adminContentGetContents'>['parameters']['query']>;
export type AdminContentPage = Success<Operation<'adminContentGetContents'>>['data'];
export type AdminContentDictionaryPage = Success<Operation<'adminContentGetContentDictionaries'>>['data'];
type Command<Id extends keyof AdminContract.operations> = Success<Operation<Id>>['data'];
type Body<Id extends keyof AdminContract.operations> = Operation<Id> extends { requestBody: { content: { 'application/json': infer Value } } } ? Value : never;

function queryString(parameters: Record<string, unknown>): string {
  const query = new URLSearchParams();
  Object.entries(parameters).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value));
  });
  const value = query.toString();
  return value ? `?${value}` : '';
}

function safeId(value: string): string {
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(value)) throw new RangeError('内容标识格式无效');
  return encodeURIComponent(value);
}

export class AdminContentsApi {
  async list(parameters: AdminContentListParameters = {}, options: AdminCallOptions = {}): Promise<AdminContentPage> {
    const response = await adminSecurityApi.request<Success<Operation<'adminContentGetContents'>>>(
      `/admin-api/v1/contents${queryString(parameters as Record<string, unknown>)}`,
      { method: 'GET' }, { ...options, token: options.token ?? adminSession.accessToken },
    );
    return response.data;
  }

  async get(id: string, options: AdminCallOptions = {}): Promise<AdminContentResource> {
    const response = await adminSecurityApi.request<Success<Operation<'adminContentGetContentsById'>>>(
      `/admin-api/v1/contents/${safeId(id)}`, { method: 'GET' },
      { ...options, token: options.token ?? adminSession.accessToken },
    );
    return response.data;
  }

  async dictionaries(parameters: NonNullable<Operation<'adminContentGetContentDictionaries'>['parameters']['query']> = {}, options: AdminCallOptions = {}): Promise<AdminContentDictionaryPage> {
    const response = await adminSecurityApi.request<Success<Operation<'adminContentGetContentDictionaries'>>>(
      `/admin-api/v1/content-dictionaries${queryString(parameters as Record<string, unknown>)}`,
      { method: 'GET' }, { ...options, token: options.token ?? adminSession.accessToken },
    );
    return response.data;
  }

  async command<Id extends 'adminContentPostContentsByIdOnline' | 'adminContentPostContentsByIdOffline' | 'adminContentPostContentsByIdBan' | 'adminContentPostContentsByIdRecommend' | 'adminContentPostContentsByIdOfficialMark'>(
    operation: Id,
    id: string,
    body: Body<Id>,
    options: AdminCallOptions = {},
  ): Promise<Command<Id>> {
    const suffix = {
      adminContentPostContentsByIdOnline: 'online',
      adminContentPostContentsByIdOffline: 'offline',
      adminContentPostContentsByIdBan: 'ban',
      adminContentPostContentsByIdRecommend: 'recommend',
      adminContentPostContentsByIdOfficialMark: 'official-mark',
    }[operation];
    const resourceId = safeId(id);
    const key = options.idempotencyKey
      ?? adminIdempotencyKeys.current(operation, JSON.stringify(body), resourceId);
    const response = await adminSecurityApi.request<{ data: Command<Id> }>(
      `/admin-api/v1/contents/${resourceId}/${suffix}`,
      { method: 'POST', body: JSON.stringify(body) },
      { ...options, token: options.token ?? adminSession.accessToken, idempotencyKey: key },
    );
    if (!options.idempotencyKey) adminIdempotencyKeys.clear(operation, resourceId);
    return response.data;
  }

  async updateDictionary(code: string, body: Body<'adminContentPutContentDictionariesByCode'>, options: AdminCallOptions = {}): Promise<Command<'adminContentPutContentDictionariesByCode'>> {
    if (!/^[A-Za-z0-9_-]{1,128}$/.test(code)) throw new RangeError('字典代码格式无效');
    const operation = 'adminContentPutContentDictionariesByCode' as const;
    const resourceCode = encodeURIComponent(code);
    const key = options.idempotencyKey
      ?? adminIdempotencyKeys.current(operation, JSON.stringify(body), resourceCode);
    const response = await adminSecurityApi.request<{ data: Command<typeof operation> }>(
      `/admin-api/v1/content-dictionaries/${resourceCode}`,
      { method: 'PUT', body: JSON.stringify(body) },
      { ...options, token: options.token ?? adminSession.accessToken, idempotencyKey: key },
    );
    if (!options.idempotencyKey) adminIdempotencyKeys.clear(operation, resourceCode);
    return response.data;
  }
}

export const adminContentsApi = new AdminContentsApi();
