import { createRouter, createWebHistory } from 'vue-router'
import { adminPages } from './catalog'
import { adminSession } from './services'
import CatalogPage from './views/CatalogPage.vue'
import AdminLoginPage from './views/AdminLoginPage.vue'
import AdminMfaPage from './views/AdminMfaPage.vue'
import AdminSecurityPage from './views/AdminSecurityPage.vue'

const implemented = new Set(['ADM-AUTH-001', 'ADM-AUTH-002', 'ADM-SECURITY-001'])

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/auth/login', name: 'ADM-AUTH-001', component: AdminLoginPage, meta: { authLayout: true, public: true } },
    { path: '/auth/mfa', name: 'ADM-AUTH-002', component: AdminMfaPage, meta: { authLayout: true, mfaPending: true } },
    { path: '/me/security', name: 'ADM-SECURITY-001', component: AdminSecurityPage, meta: { requiresAuth: true, permission: 'admin.self.read' } },
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
