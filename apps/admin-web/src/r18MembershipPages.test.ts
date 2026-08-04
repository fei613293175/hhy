// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { adminMembershipApi, adminSession } from './services'
import AdminMembershipSkusPage from './views/AdminMembershipSkusPage.vue'
import AdminMembershipUsersPage from './views/AdminMembershipUsersPage.vue'

const skus = {
  items: [
    { id: 'sku-month', skuId: 'pro-monthly', name: 'Pro 月度', status: 'ACTIVE', paidValueCent: 9900, benefits: [{ benefitCode: 'PUBLISH_LIMIT' }], version: 2 },
    { id: 'sku-year', skuId: 'pro-yearly', name: 'Pro 年度', status: 'DISABLED', paidValueCent: 79900, benefits: [], version: 3 },
  ],
  page: { page: 1, pageSize: 20, total: '2', hasMore: 'false' },
} as const
const users = {
  items: [{ id: 'user-safe-1', skuId: 'pro-monthly', name: 'Pro 月度', status: 'ACTIVE', startsAt: '2026-08-01T00:00:00Z', expiresAt: '2026-09-01T00:00:00Z', benefits: [{ benefitCode: 'PUBLISH_LIMIT' }], version: 4 }],
  page: { page: 1, pageSize: 20, total: '1', hasMore: 'false' },
} as const

describe('R18 admin membership pages', () => {
  beforeEach(() => {
    adminSession.apply({ accessToken: 'token', adminUserId: 'admin-1', displayName: '会员运营', permissionCodes: ['membership.read', 'membership.grant'], mfaRequired: 'NONE' })
    vi.spyOn(adminMembershipApi, 'skus').mockResolvedValue(skus as never)
    vi.spyOn(adminMembershipApi, 'users').mockResolvedValue(users as never)
  })
  afterEach(() => { adminSession.clear(); vi.restoreAllMocks() })

  it('keeps the SKU page aligned to Pro and hides transport status codes', async () => {
    const wrapper = mount(AdminMembershipSkusPage)
    await flushPromises()
    expect(wrapper.text()).toContain('会员 SKU')
    expect(wrapper.text()).toContain('有效')
    expect(wrapper.text()).toContain('停用')
    expect(wrapper.text()).not.toContain('ACTIVE')
    expect(wrapper.text()).not.toContain('DISABLED')
    expect(wrapper.text()).toContain('Pro 月度')
    wrapper.unmount()
  })

  it('renders user snapshots with human dates and bounded grant action', async () => {
    const wrapper = mount(AdminMembershipUsersPage)
    await flushPromises()
    expect(wrapper.text()).toContain('用户会员')
    expect(wrapper.text()).toContain('有效')
    expect(wrapper.text()).toContain('2026年8月1日')
    expect(wrapper.text()).not.toContain('ACTIVE')
    await wrapper.get('button.primary-button').trigger('click')
    expect(wrapper.text()).toContain('人工赠送 Pro')
    expect(wrapper.text()).toContain('赠送原因')
    wrapper.unmount()
  })
})
