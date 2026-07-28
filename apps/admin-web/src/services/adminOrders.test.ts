import { describe, expect, it, vi } from 'vitest'
import { ApiRequestError } from './apiError'
import { AdminOrdersApi } from './adminOrders'

function transport() {
  return { request: vi.fn() }
}

describe('AdminOrdersApi', () => {
  it('uses the frozen list and detail paths with the active token', async () => {
    const mock = transport()
    mock.request
      .mockResolvedValueOnce({ data: { items: [], page: { pageSize: 20, hasMore: 'false' } } })
      .mockResolvedValueOnce({ data: { orderNo: 'ORD_20260728_01' } })
    const api = new AdminOrdersApi(mock as never, { accessToken: 'admin-token' } as never)

    await api.list({ page: 2, pageSize: 20, status: 'PAID', keyword: 'ORD', sort: 'createdAt:desc' })
    await api.detail('ORD_20260728_01')

    expect(mock.request.mock.calls[0][0]).toBe('/admin-api/v1/orders?page=2&pageSize=20&status=PAID&keyword=ORD&sort=createdAt%3Adesc')
    expect(mock.request.mock.calls[0][2].token).toBe('admin-token')
    expect(mock.request.mock.calls[1][0]).toBe('/admin-api/v1/orders/ORD_20260728_01')
  })

  it('rejects unapproved query values and unsafe order numbers', async () => {
    const api = new AdminOrdersApi(transport() as never, {} as never)
    await expect(api.list({ page: 0 })).rejects.toThrow('页码')
    await expect(api.list({ status: 'REFUNDED' })).rejects.toThrow('订单状态')
    await expect(api.list({ sort: 'paidAmountCent:desc' })).rejects.toThrow('排序方式')
    await expect(api.detail('../secret')).rejects.toThrow('订单号')
  })

  it('retries transient network reads but not business failures', async () => {
    const network = new ApiRequestError({ status: 0, code: 'NETWORK_ERROR', message: '网络异常', requestId: 'network-test' })
    const forbidden = new ApiRequestError({ status: 403, code: 'FORBIDDEN', message: '无权限', requestId: 'forbidden-test' })
    const mock = transport()
    mock.request.mockRejectedValueOnce(network).mockResolvedValueOnce({
      data: { items: [], page: { pageSize: 20, hasMore: 'false' } },
    })
    const api = new AdminOrdersApi(mock as never, {} as never)
    await expect(api.list()).resolves.toMatchObject({ items: [] })
    expect(mock.request).toHaveBeenCalledTimes(2)

    mock.request.mockClear()
    mock.request.mockRejectedValue(forbidden)
    await expect(api.detail('ORD_01')).rejects.toBe(forbidden)
    expect(mock.request).toHaveBeenCalledTimes(1)
  })
})
