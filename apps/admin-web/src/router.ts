import { createRouter, createWebHistory } from 'vue-router'
import { adminPages } from './catalog'
import { adminSession } from './services'
import CatalogPage from './views/CatalogPage.vue'
import AdminLoginPage from './views/AdminLoginPage.vue'
import AdminMfaPage from './views/AdminMfaPage.vue'
import AdminSecurityPage from './views/AdminSecurityPage.vue'
import AdminUsersListPage from './views/AdminUsersListPage.vue'
import AdminUserDetailPage from './views/AdminUserDetailPage.vue'
import ProviderConfigPage from './views/ProviderConfigPage.vue'
import DomainConfigPage from './views/DomainConfigPage.vue'
import AdminIdentityListPage from './views/AdminIdentityListPage.vue'
import AdminIdentityDetailPage from './views/AdminIdentityDetailPage.vue'
import AdminContentListPage from './views/AdminContentListPage.vue'
import AdminContentDetailPage from './views/AdminContentDetailPage.vue'
import AdminContentDictionariesPage from './views/AdminContentDictionariesPage.vue'
import AdminForbiddenPage from './views/AdminForbiddenPage.vue'
import { implementedAdminPageIds } from './adminNavigation'
import { resolveAdminRouteRedirect } from './routerAccess'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/auth/login', name: 'ADM-AUTH-001', component: AdminLoginPage, meta: { authLayout: true, public: true } },
    { path: '/auth/mfa', name: 'ADM-AUTH-002', component: AdminMfaPage, meta: { authLayout: true, mfaPending: true } },
    { path: '/forbidden', name: 'ADM-FORBIDDEN', component: AdminForbiddenPage, meta: { requiresAuth: true } },
    { path: '/me/security', name: 'ADM-SECURITY-001', component: AdminSecurityPage, meta: { requiresAuth: true, permission: 'admin.self.read' } },
    { path: '/users', name: 'ADM-USER-001', component: AdminUsersListPage, meta: { requiresAuth: true, permission: 'user.read' } },
    { path: '/users/:id', name: 'ADM-USER-002', component: AdminUserDetailPage, meta: { requiresAuth: true, permission: 'user.read' } },
    { path: '/system/providers', name: 'ADM-CONFIG-002', component: ProviderConfigPage, meta: { requiresAuth: true, permissions: ['provider.config.read', 'config.manage'] } },
    { path: '/system/providers/sms', name: 'ADM-CONFIG-003', component: ProviderConfigPage, props: { provider: 'sms' }, meta: { requiresAuth: true, permissions: ['provider.config.read', 'config.manage'] } },
    { path: '/system/providers/storage', name: 'ADM-CONFIG-004', component: ProviderConfigPage, props: { provider: 'storage' }, meta: { requiresAuth: true, permissions: ['provider.config.read', 'config.manage'] } },
    { path: '/system/providers/payment', name: 'ADM-CONFIG-005', component: ProviderConfigPage, props: { provider: 'payment' }, meta: { requiresAuth: true, permissions: ['provider.config.read', 'config.manage'] } },
    { path: '/system/providers/payout', name: 'ADM-CONFIG-006', component: ProviderConfigPage, props: { provider: 'payout' }, meta: { requiresAuth: true, permissions: ['provider.config.read', 'config.manage'] } },
    { path: '/system/providers/identity', name: 'ADM-CONFIG-007', component: ProviderConfigPage, props: { provider: 'identity' }, meta: { requiresAuth: true, permissions: ['provider.config.read', 'config.manage'] } },
    { path: '/system/domains', name: 'ADM-CONFIG-008', component: DomainConfigPage, meta: { requiresAuth: true, permissions: ['domain.read', 'config.manage'] } },
    { path: '/identity', name: 'ADM-ID-001', component: AdminIdentityListPage, meta: { requiresAuth: true, permission: 'identity.read' } },
    { path: '/identity/:id', name: 'ADM-ID-002', component: AdminIdentityDetailPage, meta: { requiresAuth: true, permission: 'identity.read' } },
    { path: '/contents', name: 'ADM-CONTENT-001', component: AdminContentListPage, meta: { requiresAuth: true, permission: 'content.read' } },
    { path: '/contents/dictionaries', name: 'ADM-CONTENT-003', component: AdminContentDictionariesPage, meta: { requiresAuth: true, permission: 'content.read' } },
    { path: '/contents/:id', name: 'ADM-CONTENT-002', component: AdminContentDetailPage, meta: { requiresAuth: true, permission: 'content.read' } },
    ...adminPages.filter((page) => !implementedAdminPageIds.has(page.ID)).map((page) => ({
      path: page.路由,
      name: page.ID,
      component: CatalogPage,
      props: { page },
      meta: { requiresAuth: true },
    })),
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach((to) => {
  return resolveAdminRouteRedirect({
    isAuthenticated: adminSession.isAuthenticated,
    isMfaPendingRoute: Boolean(to.meta.mfaPending),
    isPublicRoute: Boolean(to.meta.public),
    requiresAuth: Boolean(to.meta.requiresAuth),
    requiredPermission: typeof to.meta.permission === 'string' ? to.meta.permission : undefined,
    requiredPermissionAlternatives: Array.isArray(to.meta.permissions) ? to.meta.permissions : undefined,
    targetPath: to.fullPath,
    hasMfaTicket: Boolean(adminSession.mfaTicket),
    hasPermission: (permission) => adminSession.hasPermission(permission),
  })
})
