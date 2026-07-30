// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { adminReviewsApi, adminSession } from './services'
import AdminReviewWorkbenchPage from './views/AdminReviewWorkbenchPage.vue'

const review = {
  id: '71', subjectType: 'CONTENT', subjectId: 'content-71', status: 'REVIEWING',
  riskLevel: 'HIGH', assigneeId: '9', version: 3, createdAt: '2026-07-25T02:00:00Z',
}

function authorize(permissions = ['review.read', 'review.decide', 'review.assign', 'report.read', 'appeal.read']) {
  adminSession.apply({
    accessToken: 'admin-token', adminUserId: '9', displayName: '审核主管',
    permissionCodes: permissions, mfaRequired: 'NONE',
  })
}

function mockReads() {
  vi.spyOn(adminReviewsApi, 'queue').mockResolvedValue({
    items: [review], page: { page: 1, pageSize: 20, total: '1', hasMore: 'false' },
  } as never)
  vi.spyOn(adminReviewsApi, 'detail').mockResolvedValue(review as never)
  vi.spyOn(adminReviewsApi, 'reports').mockResolvedValue({
    items: [{
      id: 'report-8', reporterId: '22', subjectType: 'CONTENT', subjectId: 'content-71',
      reasonCode: 'MISLEADING', status: 'PENDING', version: 1, createdAt: '2026-07-25T01:00:00Z',
    }], page: { page: 1, pageSize: 20, total: '1', hasMore: 'false' },
  } as never)
  vi.spyOn(adminReviewsApi, 'appeals').mockResolvedValue({
    items: [{
      id: 'appeal-3', appellantId: '33', subjectType: 'CONTENT', subjectId: 'content-71',
      reason: '补充材料申请复核', status: 'PENDING', version: 1, createdAt: '2026-07-25T01:30:00Z',
    }], page: { page: 1, pageSize: 20, total: '1', hasMore: 'false' },
  } as never)
}

async function mountPage(path = '/reviews?reviewId=71') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/reviews', component: AdminReviewWorkbenchPage }, { path: '/auth/login', component: { template: '<div>登录</div>' } }],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(AdminReviewWorkbenchPage, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('R12 administrator review workbench', () => {
  afterEach(() => {
    adminSession.clear()
    vi.restoreAllMocks()
  })

  it('renders the frozen three-area workbench with real review, report and appeal data', async () => {
    authorize()
    mockReads()
    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('审核队列')
    expect(wrapper.text()).toContain('任务上下文')
    expect(wrapper.text()).toContain('决定面板')
    expect(wrapper.text()).toContain('高风险')
    expect(wrapper.text()).toContain('举报 report-8')
    expect(wrapper.text()).toContain('补充材料申请复核')
    expect(wrapper.text()).not.toContain('REVIEWING')
    expect(wrapper.text()).not.toContain('HIGH')
    expect(wrapper.text()).not.toContain('request-review')
    wrapper.unmount()
  })

  it('submits the latest server version only after explicit business confirmation', async () => {
    authorize()
    mockReads()
    const decide = vi.spyOn(adminReviewsApi, 'decide').mockResolvedValue({ ...review, status: 'APPROVED', decision: 'APPROVE', version: 4 } as never)
    const { wrapper } = await mountPage()
    const decisionSection = wrapper.findAll('.review-action-section')[0]
    await decisionSection.get('select').setValue('APPROVE')
    await decisionSection.get('textarea').setValue('证据一致，符合发布规则')
    await decisionSection.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="dialog"]').text()).toContain('服务端版本')
    expect(wrapper.get('[role="dialog"]').text()).toContain('v3')
    const confirm = wrapper.findAll('[role="dialog"] button').find((button) => button.text().includes('确认并提交'))
    if (!confirm) throw new Error('Missing review confirmation command')
    await confirm.trigger('click')
    await flushPromises()

    expect(decide).toHaveBeenCalledWith('71', expect.objectContaining({
      decision: 'APPROVE', reason: '证据一致，符合发布规则', expectedVersion: 3,
    }))
    wrapper.unmount()
  })

  it('keeps granular write actions hidden when the operator only has read permission', async () => {
    authorize(['review.read'])
    mockReads()
    const { wrapper } = await mountPage()
    expect(wrapper.text()).toContain('当前角色无审核决定权限')
    expect(wrapper.text()).toContain('当前角色无审核分配权限')
    expect(wrapper.text()).toContain('当前角色无举报证据读取权限')
    expect(wrapper.text()).not.toContain('提交决定')
    wrapper.unmount()
  })

  it('restores filters and selection from the URL', async () => {
    authorize()
    mockReads()
    await mountPage('/reviews?page=2&status=REVIEWING&keyword=content-71&sort=createdAt%3Adesc&reviewId=71')
    expect(adminReviewsApi.queue).toHaveBeenCalledWith(expect.objectContaining({
      page: 2, status: 'REVIEWING', keyword: 'content-71', sort: 'createdAt:desc',
    }), expect.any(Object))
    expect(adminReviewsApi.detail).toHaveBeenCalledWith('71', expect.any(Object))
  })

  it('assigns every selected unclaimed task with its own latest version and reports each outcome', async () => {
    authorize()
    const selected = [
      { ...review, id: '81', subjectId: 'content-81', status: 'PENDING_REVIEW', assigneeId: undefined, version: 4 },
      { ...review, id: '82', subjectId: 'content-82', status: 'REVIEWING', assigneeId: undefined, version: 7 },
    ]
    vi.spyOn(adminReviewsApi, 'queue').mockResolvedValue({
      items: selected, page: { page: 1, pageSize: 20, total: '2', hasMore: 'false' },
    } as never)
    const assign = vi.spyOn(adminReviewsApi, 'assign').mockImplementation(async (id) => ({
      ...selected.find((item) => item.id === id), assigneeId: '27', version: id === '81' ? 5 : 8,
    } as never))

    const { wrapper } = await mountPage('/reviews')
    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    await checkboxes[0].setValue(true)
    await checkboxes[1].setValue(true)
    const batchButton = wrapper.findAll('button').find((button) => button.text().includes('填写分配信息'))
    if (!batchButton) throw new Error('Missing batch assignment entry')
    await batchButton.trigger('click')

    const assignmentForm = wrapper.get('.review-decision-pane .review-action-form')
    await assignmentForm.get('input').setValue('27')
    await assignmentForm.get('textarea').setValue('按专业领域批量分配')
    await assignmentForm.trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="dialog"]').text()).toContain('2 个未领取任务')
    expect(wrapper.get('[role="dialog"]').text()).toContain('81(v4)')
    expect(wrapper.get('[role="dialog"]').text()).toContain('82(v7)')

    const confirm = wrapper.findAll('[role="dialog"] button').find((button) => button.text().includes('确认并提交'))
    if (!confirm) throw new Error('Missing batch assignment confirmation')
    await confirm.trigger('click')
    await flushPromises()

    expect(assign).toHaveBeenNthCalledWith(1, '81', expect.objectContaining({ assigneeId: '27', expectedVersion: 4 }))
    expect(assign).toHaveBeenNthCalledWith(2, '82', expect.objectContaining({ assigneeId: '27', expectedVersion: 7 }))
    expect(wrapper.text()).toContain('任务 81 分配成功')
    expect(wrapper.text()).toContain('任务 82 分配成功')
    wrapper.unmount()
  })
})
