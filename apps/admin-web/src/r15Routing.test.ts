// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest'
import { implementedAdminPageIds, canAccessPage } from './adminNavigation'
import { adminPages } from './catalog'
import { router } from './router'
import { adminSession } from './services'

describe('R15 admin routing and navigation', () => {
  afterEach(() => adminSession.clear())

  it('registers all five product surfaces with frozen routes and read permissions', () => {
    const routes = new Map(router.getRoutes().map((route) => [String(route.name), route]))
    expect(routes.get('ADM-CHAT-001')).toMatchObject({ path: '/governance/chat-reports', meta: { permission: 'chat.report.read' } })
    expect(routes.get('ADM-REPORT-001')).toMatchObject({ path: '/governance/content-reports', meta: { permission: 'report.read' } })
    expect(routes.get('ADM-APPEAL-001')).toMatchObject({ path: '/governance/appeals', meta: { permission: 'appeal.read' } })
    expect(routes.get('ADM-SUPPORT-001')).toMatchObject({ path: '/support/tickets', meta: { permissions: ['support.read', 'support.manage'] } })
    expect(routes.get('ADM-SUPPORT-002')).toMatchObject({ path: '/support/tickets/:id', meta: { permission: 'support.read' } })
  })

  it('publishes implemented menu entries only to matching operators', () => {
    const pageIds = ['ADM-CHAT-001', 'ADM-SUPPORT-001', 'ADM-SUPPORT-002', 'ADM-REPORT-001', 'ADM-APPEAL-001']
    pageIds.forEach((id) => expect(implementedAdminPageIds.has(id)).toBe(true))
    adminSession.apply({ accessToken: 'token', adminUserId: '15', displayName: '客服', permissionCodes: ['support.read'], mfaRequired: 'NONE' })
    const support = adminPages.find((page) => page.ID === 'ADM-SUPPORT-001')
    const reports = adminPages.find((page) => page.ID === 'ADM-REPORT-001')
    if (!support || !reports) throw new Error('Missing R15 catalog pages')
    expect(canAccessPage(support)).toBe(true)
    expect(canAccessPage(reports)).toBe(false)
  })
})
