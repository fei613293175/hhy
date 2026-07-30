// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { adminOrdersApi, adminSession } from './services'
import CommerceOrdersPage from './views/CommerceOrdersPage.vue'

const order = {
  orderNo: 'ORD_20260728_01',
  userId: 'user_16',
  orderType: 'APP_PURCHASE',
  status: 'PAID',
  currency: 'CNY',
  items: [{ skuId: 'sku_pro', itemName: '专业版应用', quantity: 1, unitPriceCent: 9900, subtotalAmountCent: 9900 }],
  priceSnapshot: { originalAmountCent: 10900, discountAmountCent: 1000, serviceFeeCent: 0, payableAmountCent: 9900, ruleVersions: ['PRICE_V2'] },
  noRefundEvidence: { confirmed: true, agreementVersion: 'NR-2026-01', confirmedAt: '2026-07-28T11:00:00Z' },
  paidAmountCent: 9900,
  createdAt: '2026-07-28T10:00:00Z',
  paidAt: '2026-07-28T10:05:00Z',
  version: 3,
} as const

async function render(path = '/commerce/orders') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/commerce/orders', component: CommerceOrdersPage }, { path: '/auth/login', component: { template: '<div />' } }],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(CommerceOrdersPage, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('R16 commerce orders page', () => {
  beforeEach(() => {
    adminSession.apply({ accessToken: 'token', adminUserId: '16', displayName: '订单管理员', permissionCodes: ['order.read'], mfaRequired: 'NONE' })
    vi.spyOn(adminOrdersApi, 'list').mockResolvedValue({ items: [order], page: { page: 1, pageSize: 20, total: '1', hasMore: 'false' } } as never)
  })
  afterEach(() => {
    adminSession.clear()
    vi.restoreAllMocks()
  })

  it('renders authoritative money, Chinese status and real metrics without transport codes', async () => {
    const { wrapper } = await render()
    expect(wrapper.text()).toContain('订单管理')
    expect(wrapper.text()).toContain('已支付')
    expect(wrapper.text()).toContain('¥99.00')
    expect(wrapper.text()).toContain('符合当前筛选')
    expect(wrapper.text()).not.toContain('PAID')
    expect(wrapper.text()).not.toContain('PENDING_PAYMENT')
    wrapper.unmount()
  })

  it('synchronizes approved filters to URL and sends the exact order number as keyword', async () => {
    const { wrapper, router } = await render()
    const inputs = wrapper.findAll('input')
    await inputs[1].setValue('ORD_20260728_01')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(router.currentRoute.value.query.orderNo).toBe('ORD_20260728_01')
    expect(adminOrdersApi.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ keyword: 'ORD_20260728_01', page: 1 }),
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
    wrapper.unmount()
  })

  it('opens a read-only detail drawer with items, price snapshot and evidence', async () => {
    vi.spyOn(adminOrdersApi, 'detail').mockResolvedValue(order as never)
    const { wrapper } = await render()
    await wrapper.get('button.table-link').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('商品明细')
    expect(wrapper.text()).toContain('价格快照')
    expect(wrapper.text()).toContain('不可退款确认凭证')
    expect(wrapper.text()).toContain('专业版应用')
    for (const invented of ['发起退款', '修改余额', '删除订单', '联系客服', '导出订单']) {
      expect(wrapper.text()).not.toContain(invented)
    }
    wrapper.unmount()
  })

  it('keeps the last list visible when refresh fails', async () => {
    const { wrapper } = await render()
    vi.mocked(adminOrdersApi.list).mockRejectedValueOnce(new Error('timeout'))
    await wrapper.get('.order-heading-actions button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('ORD_20260728_01')
    expect(wrapper.text()).toContain('订单列表暂时无法加载')
    expect(wrapper.text()).toContain('重试')
    wrapper.unmount()
  })
})
