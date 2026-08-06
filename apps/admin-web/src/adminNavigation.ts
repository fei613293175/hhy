import type { AdminPage } from './catalog'
import { adminSession } from './services'

export const implementedAdminPageIds = new Set([
  'ADM-AUTH-001', 'ADM-AUTH-002', 'ADM-SECURITY-001', 'ADM-USER-001', 'ADM-USER-002',
  'ADM-CONFIG-002', 'ADM-CONFIG-003', 'ADM-CONFIG-004', 'ADM-CONFIG-005',
  'ADM-CONFIG-006', 'ADM-CONFIG-007', 'ADM-CONFIG-008',
  'ADM-ID-001', 'ADM-ID-002',
  'ADM-CONTENT-001', 'ADM-CONTENT-002', 'ADM-CONTENT-003',
  'ADM-REVIEW-001',
  'ADM-ORDER-001',
  'ADM-PAY-001', 'ADM-RECON-001',
  'ADM-CHAT-001', 'ADM-REPORT-001', 'ADM-APPEAL-001',
  'ADM-SUPPORT-001', 'ADM-SUPPORT-002', 'ADM-MEMBER-001', 'ADM-MEMBER-002', 'ADM-PROP-001', 'ADM-PROP-002', 'ADM-PROP-003',
  'ADM-RP-001', 'ADM-RP-002', 'ADM-RP-003', 'ADM-RP-004',
  'ADM-REWARD-001', 'ADM-REWARD-002', 'ADM-WD-001', 'ADM-WD-002', 'ADM-ACCOUNTING-001', 'ADM-ACCOUNTING-002',
])

export const menuGroupLabels: Record<string, string> = {
  dashboard: '数据概览', users: '用户管理', identity: '实名管理', contents: '内容运营',
  reviews: '审核管理', 'red-packets': '红包运营', commerce: '交易管理', finance: '财务管理',
  growth: '增长运营', governance: '治理风控', support: '客户服务', operations: '运营配置',
  delivery: '交付发布', system: '系统管理', profile: '个人中心',
}

export const pagePermissionAlternatives: Record<string, string[]> = {
  'ADM-CONFIG-002': ['provider.config.read', 'config.manage'],
  'ADM-CONFIG-003': ['provider.config.read', 'config.manage'],
  'ADM-CONFIG-004': ['provider.config.read', 'config.manage'],
  'ADM-CONFIG-005': ['provider.config.read', 'config.manage'],
  'ADM-CONFIG-006': ['provider.config.read', 'config.manage'],
  'ADM-CONFIG-007': ['provider.config.read', 'config.manage'],
  'ADM-CONFIG-008': ['domain.read', 'config.manage'],
  'ADM-SUPPORT-001': ['support.read', 'support.manage'],
}

export function permissionsForPage(page: AdminPage): string[] {
  return pagePermissionAlternatives[page.ID] ?? (page.读取权限 ? [page.读取权限] : [])
}

export function canAccessPage(page: AdminPage): boolean {
  const permissions = permissionsForPage(page)
  return permissions.length === 0 || permissions.some((permission) => adminSession.hasPermission(permission))
}

export function isImplementedMenuPage(page: AdminPage): boolean {
  return implementedAdminPageIds.has(page.ID) && page.菜单分组 !== 'hidden' && !page.路由.includes(':id')
}
