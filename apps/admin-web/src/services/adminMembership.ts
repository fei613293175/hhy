import type { AdminContract } from '@hhy/api-client'
import { AdminSecurityApi, adminSecurityApi, type AdminCallOptions } from './adminSecurity'
import { AdminSession, adminSession } from './adminSession'

type Operation<Id extends keyof AdminContract.operations> = AdminContract.operations[Id]
type JsonResponse<Value> = Value extends { content: { 'application/json': infer Json } } ? Json : never
type Success<Op> = Op extends { responses: { 200: infer Response } } ? JsonResponse<Response> : never
type SkuList = Success<Operation<'adminMembershipGetMembershipSkus'>>['data']
type UserList = Success<Operation<'adminMembershipGetUserMemberships'>>['data']
type Sku = AdminContract.components['schemas']['MembershipResource']

export type AdminMembershipSkuPage = SkuList
export type AdminUserMembershipPage = UserList
export type AdminMembershipSku = Sku

const SAFE_ID = /^[A-Za-z0-9_-]{1,64}$/
const SORTS = new Set(['priceCent:asc', 'priceCent:desc', 'updatedAt:desc'])

function id(value: string): string {
  if (!SAFE_ID.test(value)) throw new RangeError('会员资源编号格式无效')
  return encodeURIComponent(value)
}
function query(values: Record<string, string | number | undefined>): string {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  })
  return params.size ? '?' + params.toString() : ''
}

export class AdminMembershipApi {
  constructor(
    private readonly transport: AdminSecurityApi = adminSecurityApi,
    private readonly session: AdminSession = adminSession,
  ) {}

  skus(parameters: Record<string, string | number | undefined> = {}, options: AdminCallOptions = {}) {
    const sort = typeof parameters.sort === 'string' ? parameters.sort : 'priceCent:asc'
    if (!SORTS.has(sort)) throw new RangeError('会员排序方式无效')
    return this.read<Success<Operation<'adminMembershipGetMembershipSkus'>>>(
      '/admin-api/v1/membership/skus' + query({ page: 1, pageSize: 20, ...parameters, sort }), options,
    ).then((value) => value.data)
  }

  users(parameters: Record<string, string | number | undefined> = {}, options: AdminCallOptions = {}) {
    return this.read<Success<Operation<'adminMembershipGetUserMemberships'>>>(
      '/admin-api/v1/user-memberships' + query({ page: 1, pageSize: 20, ...parameters }), options,
    ).then((value) => value.data)
  }

  patchSku(resourceId: string, body: Operation<'adminMembershipPatchMembershipSkusById'>['requestBody']['content']['application/json'], key: string, options: AdminCallOptions = {}) {
    return this.write<Success<Operation<'adminMembershipPatchMembershipSkusById'>>>(
      '/admin-api/v1/membership/skus/' + id(resourceId), 'PATCH', body, key, options,
    ).then((value) => value.data)
  }

  replaceBenefits(resourceId: string, body: Operation<'adminMembershipPutMembershipSkusByIdBenefits'>['requestBody']['content']['application/json'], key: string, options: AdminCallOptions = {}) {
    return this.write<Success<Operation<'adminMembershipPutMembershipSkusByIdBenefits'>>>(
      '/admin-api/v1/membership/skus/' + id(resourceId) + '/benefits', 'PUT', body, key, options,
    ).then((value) => value.data)
  }

  grant(body: Operation<'adminMembershipPostUserMembershipsGrants'>['requestBody']['content']['application/json'], key: string, options: AdminCallOptions = {}) {
    return this.write<Success<Operation<'adminMembershipPostUserMembershipsGrants'>>>(
      '/admin-api/v1/user-memberships/grants', 'POST', body, key, options,
    ).then((value) => value.data)
  }

  private read<T>(path: string, options: AdminCallOptions) {
    return this.transport.request<T>(path, { method: 'GET' }, { ...options, token: options.token ?? this.session.accessToken })
  }
  private write<T>(path: string, method: 'PATCH' | 'PUT' | 'POST', body: unknown, key: string, options: AdminCallOptions) {
    if (!key || key.length < 16 || key.length > 128) throw new RangeError('幂等键长度无效')
    return this.transport.request<T>(path, {
      method, headers: { 'Content-Type': 'application/json', 'X-Idempotency-Key': key },
      body: JSON.stringify(body),
    }, { ...options, token: options.token ?? this.session.accessToken })
  }
}

export const adminMembershipApi = new AdminMembershipApi()
