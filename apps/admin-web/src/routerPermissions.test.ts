import { describe, expect, it } from 'vitest'
import { resolveAdminRouteRedirect } from './routerAccess'

describe('admin router permissions', () => {
  it('allows provider readers without the legacy config.manage permission', () => {
    const result = resolveAdminRouteRedirect({
      isAuthenticated: true,
      isMfaPendingRoute: false,
      isPublicRoute: false,
      requiresAuth: true,
      requiredPermissionAlternatives: ['provider.config.read', 'config.manage'],
      targetPath: '/system/providers/storage',
      hasMfaTicket: false,
      hasPermission: (permission) => permission === 'provider.config.read',
    })
    expect(result).toBeUndefined()
  })

  it('uses the explicit forbidden page when a protected route is denied', () => {
    const result = resolveAdminRouteRedirect({
      isAuthenticated: true,
      isMfaPendingRoute: false,
      isPublicRoute: false,
      requiresAuth: true,
      requiredPermission: 'user.read',
      targetPath: '/users',
      hasMfaTicket: false,
      hasPermission: (permission) => permission === 'admin.self.read',
    })
    expect(result).toEqual({ path: '/forbidden', replace: true })
  })
})
