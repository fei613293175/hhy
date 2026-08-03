import type { ClientContract } from '@hhy/api-client'

type Operation<Id extends keyof ClientContract.operations> = ClientContract.operations[Id]
type JsonResponse<Op> = Op extends { responses: { 200: { content: { 'application/json': infer Body } } } } ? Body : never
export type PaymentResource = ClientContract.components['schemas']['PaymentResource']
export type CashierResponse = JsonResponse<Operation<'paymentGetOrdersByOrdernoCashier'>>
export type CreatePaymentResponse = JsonResponse<Operation<'paymentPostOrdersByOrdernoPayments'>>
export type PaymentStatusResponse = JsonResponse<Operation<'paymentGetOrdersByOrdernoPaymentStatus'>>
export type PaymentStatusData = PaymentStatusResponse extends { data: infer Data } ? Data : never

export class PaymentApiError extends Error {
  constructor(readonly status: number, readonly requestId: string, message: string) {
    super(message)
    this.name = 'PaymentApiError'
  }
}

function baseUrl(): string {
  return import.meta.env.VITE_API_BASE_URL || (typeof window === 'undefined' ? 'http://localhost/' : window.location.origin)
}

function safeOrderNo(orderNo: string): string {
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(orderNo)) throw new RangeError('订单号格式不正确')
  return encodeURIComponent(orderNo)
}

async function request<T>(path: string, init: RequestInit, signal?: AbortSignal): Promise<T> {
  let response: Response
  try {
    response = await fetch(new URL(path, baseUrl()), { ...init, signal, headers: { Accept: 'application/json', ...init.headers } })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new PaymentApiError(0, 'missing', '网络连接失败，请检查网络后重试')
  }
  const payload: unknown = await response.json().catch(() => null)
  if (!response.ok) {
    const error = payload && typeof payload === 'object' && 'error' in payload ? (payload as { error?: { message?: string } }).error : undefined
    throw new PaymentApiError(response.status, response.headers.get('X-Request-Id') || 'missing', error?.message || '支付服务暂时不可用')
  }
  return payload as T
}

export const paymentApi = {
  cashier(orderNo: string, signal?: AbortSignal) {
    return request<CashierResponse>(`/api/v1/orders/${safeOrderNo(orderNo)}/cashier`, { method: 'GET' }, signal)
  },
  create(orderNo: string, gateway: 'ALIPAY' | 'WECHAT_PAY', returnUrl: string, idempotencyKey: string, signal?: AbortSignal) {
    return request<CreatePaymentResponse>(`/api/v1/orders/${safeOrderNo(orderNo)}/payments`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Idempotency-Key': idempotencyKey },
      body: JSON.stringify({ gateway, returnUrl }),
    }, signal)
  },
  status(orderNo: string, waitSeconds = 0, signal?: AbortSignal) {
    return request<PaymentStatusResponse>(`/api/v1/orders/${safeOrderNo(orderNo)}/payment-status?page=1&pageSize=20&waitSeconds=${Math.max(0, Math.min(10, waitSeconds))}`, { method: 'GET' }, signal)
  },
}
