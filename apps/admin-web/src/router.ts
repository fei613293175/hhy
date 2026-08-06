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
import AdminReviewWorkbenchPage from './views/AdminReviewWorkbenchPage.vue'
import CommerceOrdersPage from './views/CommerceOrdersPage.vue'
import AdminGovernanceQueuePage from './views/AdminGovernanceQueuePage.vue'
import AdminSupportTicketsPage from './views/AdminSupportTicketsPage.vue'
import AdminSupportTicketDetailPage from './views/AdminSupportTicketDetailPage.vue'
import AdminForbiddenPage from './views/AdminForbiddenPage.vue'
import AdminPaymentsPage from './views/AdminPaymentsPage.vue'
import AdminMembershipSkusPage from './views/AdminMembershipSkusPage.vue'
import AdminMembershipUsersPage from './views/AdminMembershipUsersPage.vue'
import AdminPropProductsPage from './views/AdminPropProductsPage.vue'
import AdminPropSlotsPage from './views/AdminPropSlotsPage.vue'
import AdminPropExecutionsPage from './views/AdminPropExecutionsPage.vue'
import AdminRedPacketListPage from './views/AdminRedPacketListPage.vue'
import AdminRedPacketReviewPage from './views/AdminRedPacketReviewPage.vue'
import AdminRedPacketDetailPage from './views/AdminRedPacketDetailPage.vue'
import AdminRedPacketRiskPage from './views/AdminRedPacketRiskPage.vue'
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
    { path: '/reviews', name: 'ADM-REVIEW-001', component: AdminReviewWorkbenchPage, meta: { requiresAuth: true, permission: 'review.read' } },
    { path: '/red-packets', name: 'ADM-RP-001', component: AdminRedPacketListPage, meta: { requiresAuth: true, permission: 'redpacket.read' } },
    { path: '/red-packets/review', name: 'ADM-RP-003', component: AdminRedPacketReviewPage, meta: { requiresAuth: true, permission: 'redpacket.read' } },
    { path: '/red-packets/risk', name: 'ADM-RP-004', component: AdminRedPacketRiskPage, meta: { requiresAuth: true, permission: 'redpacket.read' } },
    { path: '/red-packets/:id', name: 'ADM-RP-002', component: AdminRedPacketDetailPage, meta: { requiresAuth: true, permission: 'redpacket.read' } },
    { path: '/commerce/orders', name: 'ADM-ORDER-001', component: CommerceOrdersPage, meta: { requiresAuth: true, permission: 'order.read' } },
    { path: '/commerce/payments', name: 'ADM-PAY-001', component: AdminPaymentsPage, props: { mode: 'payments' }, meta: { requiresAuth: true, permission: 'payment.read' } },
    { path: '/commerce/reconciliation', name: 'ADM-RECON-001', component: AdminPaymentsPage, props: { mode: 'reconciliation' }, meta: { requiresAuth: true, permission: 'payment.reconcile' } },
    { path: '/commerce/membership/skus', name: 'ADM-MEMBER-001', component: AdminMembershipSkusPage, meta: { requiresAuth: true, permission: 'membership.read' } },
    { path: '/commerce/membership/users', name: 'ADM-MEMBER-002', component: AdminMembershipUsersPage, meta: { requiresAuth: true, permission: 'membership.read' } },
    { path: '/commerce/props/products', name: 'ADM-PROP-001', component: AdminPropProductsPage, meta: { requiresAuth: true, permission: 'prop.read' } },
    { path: '/commerce/props/slots', name: 'ADM-PROP-002', component: AdminPropSlotsPage, meta: { requiresAuth: true, permission: 'prop.slot.read' } },
    { path: '/commerce/props/executions', name: 'ADM-PROP-003', component: AdminPropExecutionsPage, meta: { requiresAuth: true, permission: 'prop.read' } },
    { path: '/governance/chat-reports', name: 'ADM-CHAT-001', component: AdminGovernanceQueuePage, props: { kind: 'chat' }, meta: { requiresAuth: true, permission: 'chat.report.read' } },
    { path: '/governance/content-reports', name: 'ADM-REPORT-001', component: AdminGovernanceQueuePage, props: { kind: 'report' }, meta: { requiresAuth: true, permission: 'report.read' } },
    { path: '/governance/appeals', name: 'ADM-APPEAL-001', component: AdminGovernanceQueuePage, props: { kind: 'appeal' }, meta: { requiresAuth: true, permission: 'appeal.read' } },
    { path: '/support/tickets', name: 'ADM-SUPPORT-001', component: AdminSupportTicketsPage, meta: { requiresAuth: true, permissions: ['support.read', 'support.manage'] } },
    { path: '/support/tickets/:id', name: 'ADM-SUPPORT-002', component: AdminSupportTicketDetailPage, meta: { requiresAuth: true, permission: 'support.read' } },
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
