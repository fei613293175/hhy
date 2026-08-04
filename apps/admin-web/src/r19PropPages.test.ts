// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { implementedAdminPageIds } from './adminNavigation'
import { router as appRouter } from './router'
import { AdminPropsApi, adminPropsApi, adminSession } from './services'
import AdminPropProductsPage from './views/AdminPropProductsPage.vue'
import AdminPropSlotsPage from './views/AdminPropSlotsPage.vue'
import AdminPropExecutionsPage from './views/AdminPropExecutionsPage.vue'

const page = (items: unknown[]) => ({ items, page: { page: 1, pageSize: 20, total: String(items.length), hasMore: 'false' } })
async function render(component: object, path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path, component }] })
  await router.push(path); await router.isReady()
  const wrapper = mount(component, { global: { plugins: [router] } }); await flushPromises()
  return { wrapper, router }
}

describe('R19 admin prop pages', () => {
  beforeEach(() => {
    adminSession.apply({ accessToken: 'token', adminUserId: '19', displayName: '道具运营', permissionCodes: ['prop.read', 'prop.slot.read'], mfaRequired: 'NONE' })
    vi.spyOn(adminPropsApi, 'props').mockResolvedValue(page([{ id: '11', propType: 'HEADLINE', name: '头条曝光', quantity: 0, status: 'ACTIVE', configuration: { priceCent: 990, durationSeconds: '86400', scope: { content: true } }, version: 2 }]) as never)
    vi.spyOn(adminPropsApi, 'headlineSlots').mockResolvedValue(page([{ id: '21', propType: 'HEADLINE_SLOT', name: 'home/top', quantity: 3, status: 'ACTIVE', configuration: { pageCode: 'home', slotCode: 'top', capacity: 3 }, version: 1 }]) as never)
    vi.spyOn(adminPropsApi, 'executions').mockResolvedValue(page([{ id: '31', propType: 'EXECUTION', name: 'HEADLINE#content-7', quantity: 1, status: 'SUCCEEDED', expiresAt: '2026-08-04T08:00:00Z', configuration: { result: { contentId: 'content-7' } }, version: 1 }]) as never)
  })
  afterEach(() => { adminSession.clear(); vi.restoreAllMocks() })

  it('registers the three frozen routes and implemented page ids', () => {
    const routes = new Map(appRouter.getRoutes().map((route) => [String(route.name), route]))
    expect(routes.get('ADM-PROP-001')).toMatchObject({ path: '/commerce/props/products', meta: { permission: 'prop.read' } })
    expect(routes.get('ADM-PROP-002')).toMatchObject({ path: '/commerce/props/slots', meta: { permission: 'prop.slot.read' } })
    expect(routes.get('ADM-PROP-003')).toMatchObject({ path: '/commerce/props/executions', meta: { permission: 'prop.read' } })
    ;['ADM-PROP-001', 'ADM-PROP-002', 'ADM-PROP-003'].forEach((id) => expect(implementedAdminPageIds.has(id)).toBe(true))
  })

  it('calls the exact frozen admin API routes', async () => {
    const request = vi.fn().mockResolvedValue({ data: page([]) })
    const api = new AdminPropsApi({ request } as never, adminSession)
    await api.props({ sort: 'name:asc' }); await api.headlineSlots({ sort: 'createdAt:desc' }); await api.executions({ sort: 'createdAt:desc' })
    await api.create({ propType: 'REFRESH', name: '刷新卡', durationSeconds: 1, executionType: 'IMMEDIATE', productCode: 'PROP-REFRESH', skuCode: 'PROP-REFRESH-1', priceCent: 990, scope: {}, status: 'ACTIVE', reason: '上架刷新道具' }, 'r19-prop-create-0001')
    await api.createHeadlineSlot({ reason: '新建首页资源位', payload: { pageCode: 'home', slotCode: 'top', capacity: 1 } }, 'r19-slot-create-0001')
    expect(request.mock.calls.map((call) => call[0])).toEqual([
      '/admin-api/v1/props?sort=name%3Aasc',
      '/admin-api/v1/headline-slots?sort=createdAt%3Adesc',
      '/admin-api/v1/prop-executions?sort=createdAt%3Adesc',
      '/admin-api/v1/props',
      '/admin-api/v1/headline-slots',
    ])
  })

  it('shows atomic prop creation only with write permission', async () => {
    adminSession.apply({ accessToken: 'token', adminUserId: '19', displayName: '道具运营', permissionCodes: ['prop.read', 'prop.write'], mfaRequired: 'NONE' })
    const { wrapper } = await render(AdminPropProductsPage, '/commerce/props/products')
    expect(wrapper.text()).toContain('创建道具商品')
    await wrapper.get('header .primary-button').trigger('click')
    expect(wrapper.text()).toContain('商品、SKU、道具定义和绑定关系将作为一个事务创建')
    wrapper.unmount()
  })

  it('renders products in business language and hides row writes without permission', async () => {
    const { wrapper, router } = await render(AdminPropProductsPage, '/commerce/props/products')
    expect(wrapper.text()).toContain('道具商品'); expect(wrapper.text()).toContain('头条曝光'); expect(wrapper.text()).toContain('¥9.90')
    expect(wrapper.text()).not.toContain('ACTIVE'); expect(wrapper.text()).not.toContain('修改道具')
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(router.currentRoute.value.query.sort).toBe('createdAt:desc')
    wrapper.unmount()
  })

  it('keeps slot creation permission-gated and exposes capacity facts', async () => {
    const { wrapper } = await render(AdminPropSlotsPage, '/commerce/props/slots')
    expect(wrapper.text()).toContain('资源位与排期'); expect(wrapper.text()).toContain('home'); expect(wrapper.text()).toContain('按容量接受排期')
    expect(wrapper.text()).not.toContain('创建资源位')
    wrapper.unmount()
  })

  it('keeps execution logs read-only without raw transport status', async () => {
    const { wrapper } = await render(AdminPropExecutionsPage, '/commerce/props/executions')
    expect(wrapper.text()).toContain('执行日志'); expect(wrapper.text()).toContain('执行成功'); expect(wrapper.text()).not.toContain('SUCCEEDED')
    for (const action of ['删除日志', '重试执行', '修改结果']) expect(wrapper.text()).not.toContain(action)
    wrapper.unmount()
  })
})
