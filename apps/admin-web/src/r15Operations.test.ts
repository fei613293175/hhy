// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiRequestError } from './services/apiError'
import { adminR15Api, adminSession } from './services'
import AdminGovernanceQueuePage from './views/AdminGovernanceQueuePage.vue'
import AdminSupportTicketsPage from './views/AdminSupportTicketsPage.vue'
import AdminSupportTicketDetailPage from './views/AdminSupportTicketDetailPage.vue'

const meta = { page: 1, pageSize: 20, total: '1', hasMore: 'false' }
const ticket = { id: 'ticket-7', ticketNo: 'TK-20260803-7', category: 'ACCOUNT', subject: '账号无法登录', status: 'OPEN', assignee: 'agent-2', createdAt: '2026-08-03T01:00:00Z', lastMessageAt: '2026-08-03T02:00:00Z', version: 5 }
function authorize(permissions: string[]) { adminSession.apply({ accessToken: 'token', adminUserId: '15', displayName: '运营专员', permissionCodes: permissions, mfaRequired: 'NONE' }) }
async function renderGovernance(kind: 'chat' | 'report' | 'appeal', path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path, component: AdminGovernanceQueuePage, props: { kind } }, { path: '/auth/login', component: { template: '<div>登录</div>' } }] })
  await router.push(path); await router.isReady(); const wrapper = mount(AdminGovernanceQueuePage, { props: { kind }, global: { plugins: [router] } }); await flushPromises(); return wrapper
}

describe('R15 admin product surfaces', () => {
  beforeEach(() => vi.spyOn(window, 'confirm').mockReturnValue(true))
  afterEach(() => { adminSession.clear(); vi.restoreAllMocks() })

  it('renders real chat evidence and hides decide controls without granular permission', async () => {
    authorize(['chat.report.read'])
    vi.spyOn(adminR15Api, 'chatReports').mockResolvedValue({ items: [{ id: 'chat-1', unreadCount: 2, lastMessage: { messageId: 'm-9', messageType: 'TEXT', preview: '对方持续发送违规引流内容', createdAt: '2026-08-03T01:00:00Z' }, version: 4 }], page: meta } as never)
    const wrapper = await renderGovernance('chat', '/governance/chat-reports')
    expect(wrapper.text()).toContain('聊天举报'); expect(wrapper.text()).toContain('违规引流内容')
    await wrapper.get('button.table-link').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('当前角色只有读取权限'); expect(wrapper.text()).not.toContain('提交决定')
  })

  it('keeps report input and requires reload after a 409 conflict', async () => {
    authorize(['report.read', 'report.decide'])
    vi.spyOn(adminR15Api, 'contentReports').mockResolvedValue({ items: [{ id: 'report-4', reporterId: 'user-8', subjectType: 'CONTENT', subjectId: 'content-9', reasonCode: 'MISLEADING', status: 'PENDING', createdAt: '2026-08-03T01:00:00Z', version: 6 }], page: meta } as never)
    vi.spyOn(adminR15Api, 'decideReport').mockRejectedValue(new ApiRequestError({ status: 409, code: 'COMMON-409-VERSION_CONFLICT', message: 'version conflict', requestId: 'conflict-1' }))
    const wrapper = await renderGovernance('report', '/governance/content-reports'); await wrapper.get('button.table-link').trigger('click')
    await wrapper.get('textarea').setValue('举报证据与内容事实一致'); await wrapper.get('.r15-decision-form').trigger('submit'); await flushPromises()
    expect(wrapper.text()).toContain('数据冲突，未覆盖新版本'); expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('举报证据与内容事实一致')
    expect(adminR15Api.decideReport).toHaveBeenCalledWith('report-4', expect.objectContaining({ expectedVersion: 6 }))
  })

  it('renders the appeal queue with appellant context and Chinese status', async () => {
    authorize(['appeal.read', 'appeal.decide'])
    vi.spyOn(adminR15Api, 'appeals').mockResolvedValue({ items: [{ id: 'appeal-2', appellantId: 'user-3', subjectType: 'CONTENT', subjectId: 'content-1', reason: '补充了原创证明', status: 'PENDING', createdAt: '2026-08-03T01:00:00Z', version: 2 }], page: meta } as never)
    const wrapper = await renderGovernance('appeal', '/governance/appeals')
    expect(wrapper.text()).toContain('申诉处理'); expect(wrapper.text()).toContain('补充了原创证明'); expect(wrapper.text()).toContain('待处理'); expect(wrapper.text()).not.toContain('PENDING')
  })

  it('renders the support list and preserves its URL as detail return context', async () => {
    authorize(['support.read']); vi.spyOn(adminR15Api, 'supportTickets').mockResolvedValue({ items: [ticket], page: meta } as never)
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/support/tickets', component: AdminSupportTicketsPage }, { path: '/support/tickets/:id', component: { template: '<div />' } }, { path: '/auth/login', component: { template: '<div />' } }] })
    await router.push('/support/tickets?status=OPEN'); await router.isReady(); const wrapper = mount(AdminSupportTicketsPage, { global: { plugins: [router] } }); await flushPromises()
    expect(wrapper.text()).toContain('TK-20260803-7'); expect(wrapper.text()).toContain('待受理')
    await wrapper.get('button.table-link').trigger('click'); await flushPromises()
    expect(router.currentRoute.value.path).toBe('/support/tickets/ticket-7'); expect(router.currentRoute.value.query.from).toContain('status=OPEN')
  })

  it('uses the latest ticket version for assignment and exposes only granted commands', async () => {
    authorize(['support.read', 'support.assign']); vi.spyOn(adminR15Api, 'supportTicket').mockResolvedValue(ticket as never); const assign = vi.spyOn(adminR15Api, 'assignTicket').mockResolvedValue({ ...ticket, assignee: 'agent-9', version: 6 } as never)
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/support/tickets/:id', component: AdminSupportTicketDetailPage }, { path: '/support/tickets', component: { template: '<div />' } }, { path: '/auth/login', component: { template: '<div />' } }] })
    await router.push('/support/tickets/ticket-7'); await router.isReady(); const wrapper = mount(AdminSupportTicketDetailPage, { global: { plugins: [router] } }); await flushPromises()
    expect(wrapper.text()).toContain('会话与附件'); expect(wrapper.text()).toContain('当前角色无客服回复权限'); expect(wrapper.text()).toContain('当前角色无关闭工单权限')
    const assignSection = wrapper.findAll('.ticket-actions section')[0]; await assignSection.get('input').setValue('agent-9'); await assignSection.get('button').trigger('click'); await flushPromises()
    expect(assign).toHaveBeenCalledWith('ticket-7', expect.objectContaining({ assigneeId: 'agent-9', expectedVersion: 5 }))
  })
})
