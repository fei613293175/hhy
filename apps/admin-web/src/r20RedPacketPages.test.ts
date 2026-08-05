// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { implementedAdminPageIds } from './adminNavigation'
import { router as appRouter } from './router'
import { AdminRedPacketApi, adminRedPacketApi, adminSession } from './services'
import AdminRedPacketListPage from './views/AdminRedPacketListPage.vue'
import AdminRedPacketReviewPage from './views/AdminRedPacketReviewPage.vue'

const campaign = {
  id: 'rp_20', contentId: 'content_20', ownerUserId: 'user_20', status: 'PRE_REVIEWING',
  totalCount: 20, remainingCount: 20, amountPerClaimCent: 100, principalCent: 2000,
  serviceFeeCent: 40, startAt: '2026-08-05T00:00:00Z', endAt: '2026-08-06T00:00:00Z', version: 3,
} as const
const page = { items: [campaign], page: { page: 1, pageSize: 20, total: '41', hasMore: 'true' } } as const

async function render(component: object, path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: path.split('?')[0], component }] })
  await router.push(path); await router.isReady()
  const wrapper = mount(component, { global: { plugins: [router] } }); await flushPromises()
  return { wrapper, router }
}

describe('R20 admin red packet pages', () => {
  beforeEach(() => {
    adminSession.apply({ accessToken: 'token', adminUserId: '20', displayName: '红包运营', permissionCodes: ['redpacket.read'], mfaRequired: 'NONE' })
    vi.spyOn(adminRedPacketApi, 'campaigns').mockResolvedValue(page as never)
    vi.spyOn(adminRedPacketApi, 'campaign').mockResolvedValue(campaign as never)
  })
  afterEach(() => { adminSession.clear(); vi.restoreAllMocks() })

  it('registers the two frozen R20 routes with read permission', () => {
    const routes = new Map(appRouter.getRoutes().map((route) => [String(route.name), route]))
    expect(routes.get('ADM-RP-001')).toMatchObject({ path: '/red-packets', meta: { permission: 'redpacket.read' } })
    expect(routes.get('ADM-RP-003')).toMatchObject({ path: '/red-packets/review', meta: { permission: 'redpacket.read' } })
    expect(implementedAdminPageIds.has('ADM-RP-001')).toBe(true)
    expect(implementedAdminPageIds.has('ADM-RP-003')).toBe(true)
  })

  it('calls the exact frozen service routes and writes the review contract', async () => {
    const request = vi.fn().mockResolvedValue({ data: page })
    const api = new AdminRedPacketApi({ request } as never, adminSession)
    await api.campaigns({ page: 2, pageSize: 20, status: 'PRE_REVIEWING', keyword: '红包 A', sort: 'priority:desc,createdAt:asc' })
    await api.campaign('rp_20')
    await api.review('rp_20', { decision: 'APPROVE', reason: '证据一致', expectedVersion: 3, evidenceIds: ['ev_20'] }, 'r20-review-idem-00001')

    expect(request.mock.calls.map((call) => call[0])).toEqual([
      '/admin-api/v1/red-packet-campaigns?page=2&pageSize=20&status=PRE_REVIEWING&keyword=%E7%BA%A2%E5%8C%85+A&sort=priority%3Adesc%2CcreatedAt%3Aasc',
      '/admin-api/v1/red-packet-campaigns/rp_20',
      '/admin-api/v1/red-packet-campaigns/rp_20/review',
    ])
    expect(request.mock.calls[2][1]).toMatchObject({ method: 'POST', headers: { 'X-Idempotency-Key': 'r20-review-idem-00001' } })
    expect(JSON.parse(request.mock.calls[2][1].body)).toEqual({ decision: 'APPROVE', reason: '证据一致', expectedVersion: 3, evidenceIds: ['ev_20'] })
  })

  it('uses the server total and hasMore while rendering formal business statuses', async () => {
    const { wrapper, router } = await render(AdminRedPacketListPage, '/red-packets')
    expect(wrapper.text()).toContain('共 41 条')
    expect(wrapper.text()).toContain('预审核中')
    expect(wrapper.text()).not.toContain('PRE_REVIEWING')
    const next = wrapper.findAll('.pagination button').find((button) => button.text() === '下一页')
    if (!next) throw new Error('Missing next page command')
    expect(next.attributes('disabled')).toBeUndefined()
    await next.trigger('click'); await flushPromises()
    expect(adminRedPacketApi.campaigns).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }))
    expect(router.currentRoute.value.query.page).toBe('2')
    wrapper.unmount()
  })

  it('keeps review read-only without permission', async () => {
    const { wrapper } = await render(AdminRedPacketReviewPage, '/red-packets/review')
    await wrapper.get('.review-table tbody tr').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('当前会话只读')
    expect(wrapper.get('.review-action-form button').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('opens the campaign from a review deep link and preserves the query', async () => {
    const { wrapper, router } = await render(AdminRedPacketReviewPage, '/red-packets/review?campaignId=rp_20')
    await flushPromises()
    expect(adminRedPacketApi.campaign).toHaveBeenCalledWith('rp_20')
    expect(wrapper.get('.review-context-pane').text()).toContain('rp_20')
    expect(router.currentRoute.value.query.campaignId).toBe('rp_20')
    wrapper.unmount()
  })

  it('submits latest version and evidence only after explicit confirmation', async () => {
    adminSession.apply({ accessToken: 'token', adminUserId: '20', displayName: '红包审核员', permissionCodes: ['redpacket.read', 'redpacket.review'], mfaRequired: 'NONE' })
    const review = vi.spyOn(adminRedPacketApi, 'review').mockResolvedValue({ ...campaign, status: 'PRE_REVIEW_APPROVED', version: 4 } as never)
    const { wrapper } = await render(AdminRedPacketReviewPage, '/red-packets/review')
    await wrapper.get('.review-table tbody tr').trigger('click'); await flushPromises()
    await wrapper.get('.review-action-form textarea').setValue('证据一致，活动内容符合规则')
    await wrapper.get('.review-action-form input').setValue('ev_20, ev_21')
    await wrapper.get('.review-action-form').trigger('submit'); await flushPromises()

    expect(review).not.toHaveBeenCalled()
    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.text()).toContain('目标活动rp_20')
    expect(dialog.text()).toContain('服务端版本v3')
    expect(dialog.text()).toContain('审批要求')
    expect(dialog.text()).toContain('通知')
    const confirm = dialog.findAll('button').find((button) => button.text() === '确认并提交')
    if (!confirm) throw new Error('Missing red packet review confirmation command')
    await confirm.trigger('click'); await flushPromises()

    expect(review).toHaveBeenCalledOnce()
    expect(review.mock.calls[0][0]).toBe('rp_20')
    expect(review.mock.calls[0][1]).toEqual({ decision: 'APPROVE', reason: '证据一致，活动内容符合规则', expectedVersion: 3, evidenceIds: ['ev_20', 'ev_21'] })
    expect(review.mock.calls[0][2]).toMatch(/^r20-review-rp_20-3-/)
    expect(wrapper.text()).toContain('已提交通过')
    wrapper.unmount()
  })
})
