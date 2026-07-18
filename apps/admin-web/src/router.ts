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

const implemented = new Set([
  'ADM-AUTH-001', 'ADM-AUTH-002', 'ADM-SECURITY-001', 'ADM-USER-001', 'ADM-USER-002',
  'ADM-CONFIG-002', 'ADM-CONFIG-003', 'ADM-CONFIG-004', 'ADM-CONFIG-007',
])

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/auth/login', name: 'ADM-AUTH-001', component: AdminLoginPage, meta: { authLayout: true, public: true } },
    { path: '/auth/mfa', name: 'ADM-AUTH-002', component: AdminMfaPage, meta: { authLayout: true, mfaPending: true } },
    { path: '/me/security', name: 'ADM-SECURITY-001', component: AdminSecurityPage, meta: { requiresAuth: true, permission: 'admin.self.read' } },
    { path: '/users', name: 'ADM-USER-001', component: AdminUsersListPage, meta: { requiresAuth: true, permission: 'user.read' } },
    { path: '/users/:id', name: 'ADM-USER-002', component: AdminUserDetailPage, meta: { requiresAuth: true, permission: 'user.read' } },
    { path: '/system/providers', name: 'ADM-CONFIG-002', component: ProviderConfigPage, meta: { requiresAuth: true, permission: 'config.manage' } },
    { path: '/system/providers/sms', name: 'ADM-CONFIG-003', component: ProviderConfigPage, props: { provider: 'sms' }, meta: { requiresAuth: true, permission: 'config.manage' } },
    { path: '/system/providers/storage', name: 'ADM-CONFIG-004', component: ProviderConfigPage, props: { provider: 'storage' }, meta: { requiresAuth: true, permission: 'config.manage' } },
    { path: '/system/providers/identity', name: 'ADM-CONFIG-007', component: ProviderConfigPage, props: { provider: 'identity' }, meta: { requiresAuth: true, permission: 'config.manage' } },
    ...adminPages.filter((page) => !implemented.has(page.ID)).map((page) => ({
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
  if (to.meta.mfaPending && !adminSession.mfaTicket) return { path: '/auth/login', replace: true }
  if (to.meta.requiresAuth && !adminSession.isAuthenticated) {
    return { path: '/auth/login', query: { redirect: to.fullPath }, replace: true }
  }
  if (to.meta.public && adminSession.isAuthenticated) return { path: '/me/security', replace: true }
})
