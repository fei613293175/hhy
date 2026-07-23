export interface AdminRouteAccessInput {
  isAuthenticated: boolean
  isMfaPendingRoute: boolean
  isPublicRoute: boolean
  requiresAuth: boolean
  requiredPermission?: string
  requiredPermissionAlternatives?: unknown[]
  targetPath: string
  hasMfaTicket: boolean
  hasPermission: (permission: string) => boolean
}

export function resolveAdminRouteRedirect(input: AdminRouteAccessInput) {
  if (input.isMfaPendingRoute && !input.hasMfaTicket) {
    return { path: '/auth/login', replace: true }
  }
  if (input.requiresAuth && !input.isAuthenticated) {
    return { path: '/auth/login', query: { redirect: input.targetPath }, replace: true }
  }
  if (input.requiresAuth && input.requiredPermission && !input.hasPermission(input.requiredPermission)) {
    return { path: '/forbidden', replace: true }
  }
  if (input.requiresAuth && Array.isArray(input.requiredPermissionAlternatives)
      && !input.requiredPermissionAlternatives.some((permission) => (
        typeof permission === 'string' && input.hasPermission(permission)
      ))) {
    return { path: '/forbidden', replace: true }
  }
  if (input.isPublicRoute && input.isAuthenticated) {
    return { path: '/me/security', replace: true }
  }
}
