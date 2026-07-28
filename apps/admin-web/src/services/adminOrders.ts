import type { AdminContract } from '@hhy/api-client'
import { ApiRequestError, apiRequestErrorFromNetwork } from './apiError'
import { AdminSecurityApi, adminSecurityApi, type AdminCallOptions } from './adminSecurity'
import { AdminSession, adminSession } from './adminSession'

type Operation<Id extends keyof AdminContract.operations> = AdminContract.operations[Id]
type JsonResponse<Value> = Value extends { content: { 'application/json': infer Json } } ? Json : never
type Success<Op> = Op extends { responses: { 200: infer Response } } ? JsonResponse<Response> : never

type ListOperation = Operation<'adminOrdersGetOrders'>
type DetailOperation = Operation<'adminOrdersGetOrdersByOrderno'>

export type AdminOrderResource = AdminContract.components['schemas']['OrderResource']
export type AdminOrderPage = Success<ListOperation>['data']
export type AdminOrderListParameters = NonNullable<ListOperation['parameters']['query']>

const SAFE_ORDER_NO = /^[A-Za-z0-9_-]{1,64}$/
const ALLOWED_STATUSES = new Set([
  'PENDING_PAYMENT', 'PAYMENT_PROCESSING', 'PAID', 'FULFILLING',
  'COMPLETED', 'PAYMENT_FAILED', 'CLOSED', 'CHANNEL_REVERSAL',
])
const ALLOWED_SORTS = new Set(['createdAt:desc', 'createdAt:asc', 'orderNo:desc', 'orderNo:asc'])

function delay(milliseconds: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Aborted', 'AbortError'))
      return
    }
    const onAbort = () => {
      globalThis.clearTimeout(timer)
      reject(new DOMException('Aborted', 'AbortError'))
    }
    const timer = globalThis.setTimeout(() => {
      signal?.removeEventListener('abort', onAbort)
      resolve()
    }, milliseconds)
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

function queryString(parameters: AdminOrderListParameters): string {
  if (parameters.page !== undefined && parameters.page < 1) throw new RangeError('页码必须大于等于 1')
  if (parameters.pageSize !== undefined && (parameters.pageSize < 1 || parameters.pageSize > 100)) {
    throw new RangeError('每页数量必须在 1 到 100 之间')
  }
  if (parameters.keyword && parameters.keyword.length > 100) throw new RangeError('关键词长度不能超过 100')
  if (parameters.status && !ALLOWED_STATUSES.has(parameters.status)) throw new RangeError('订单状态不在允许范围内')
  if (parameters.sort && !ALLOWED_SORTS.has(parameters.sort)) throw new RangeError('排序方式不在允许范围内')
  const query = new URLSearchParams()
  Object.entries(parameters).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value))
  })
  return query.size ? `?${query.toString()}` : ''
}

function safeOrderNo(value: string): string {
  if (!SAFE_ORDER_NO.test(value)) throw new RangeError('订单号格式无效')
  return encodeURIComponent(value)
}

export class AdminOrdersApi {
  constructor(
    private readonly transport: AdminSecurityApi = adminSecurityApi,
    private readonly session: AdminSession = adminSession,
  ) {}

  async list(
    parameters: AdminOrderListParameters = {},
    options: AdminCallOptions = {},
  ): Promise<AdminOrderPage> {
    const response = await this.read<Success<ListOperation>>(
      `/admin-api/v1/orders${queryString(parameters)}`, options,
    )
    return response.data
  }

  async detail(orderNo: string, options: AdminCallOptions = {}): Promise<AdminOrderResource> {
    const response = await this.read<Success<DetailOperation>>(
      `/admin-api/v1/orders/${safeOrderNo(orderNo)}`, options,
    )
    return response.data
  }

  private async read<T>(path: string, options: AdminCallOptions): Promise<T> {
    for (let attempt = 0; attempt < 3; attempt += 1) {
      try {
        return await this.transport.request<T>(
          path,
          { method: 'GET' },
          { ...options, token: options.token ?? this.session.accessToken },
        )
      } catch (caught) {
        const retry = caught instanceof ApiRequestError
          && caught.code === 'NETWORK_ERROR'
          && attempt < 2
        if (!retry) throw caught
        await delay(100 * (2 ** attempt), options.signal).catch((error) => {
          throw apiRequestErrorFromNetwork(error)
        })
      }
    }
    throw new Error('Unreachable admin order read retry state')
  }
}

export const adminOrdersApi = new AdminOrdersApi()
