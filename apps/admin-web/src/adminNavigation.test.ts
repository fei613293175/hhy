import { afterEach, describe, expect, it } from 'vitest'
import { adminPages } from './catalog'
import { canAccessPage, isImplementedMenuPage, menuGroupLabels } from './adminNavigation'
import { adminSession } from './services'

function page(id: string) {
  const found = adminPages.find((item) => item.ID === id)
  if (!found) throw new Error(`Missing admin page ${id}`)
  return found
}

describe('admin navigation', () => {
  afterEach(() => adminSession.clear())

  it('shows only implemented pages and renders Chinese group labels', () => {
    expect(isImplementedMenuPage(page('ADM-CONFIG-004'))).toBe(true)
    expect(isImplementedMenuPage(page('ADM-DASH-001'))).toBe(false)
    expect(menuGroupLabels.system).toBe('系统管理')
    expect(menuGroupLabels.contents).toBe('内容运营')
  })

  it('uses provider and domain permissions instead of requiring config.manage', () => {
    adminSession.apply({
      accessToken: 'access', adminUserId: '1', displayName: '超级管理员',
      permissionCodes: ['provider.config.read', 'domain.read'], mfaRequired: 'NONE',
    })
    expect(canAccessPage(page('ADM-CONFIG-004'))).toBe(true)
    expect(canAccessPage(page('ADM-CONFIG-008'))).toBe(true)
    expect(canAccessPage(page('ADM-USER-001'))).toBe(false)
  })
})
