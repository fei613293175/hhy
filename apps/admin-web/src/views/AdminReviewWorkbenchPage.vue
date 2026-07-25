<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusNotice from '../components/StatusNotice.vue'
import {
  adminReviewsApi,
  adminSession,
  isApiRequestError,
  type AdminAppealPage,
  type AdminReportPage,
  type AdminReviewCommandOutcome,
  type AdminReviewPage,
  type AdminReviewResource,
} from '../services'

type MobileStep = 'queue' | 'context' | 'decision'
type ConfirmAction = 'decide' | 'assign'
type AssignmentMode = 'single' | 'batch'
type BatchAssignmentResult = { id: string; status: 'success' | 'failed'; detail: string }

const route = useRoute()
const router = useRouter()
const queue = ref<AdminReviewPage>()
const detail = ref<AdminReviewResource>()
const reports = ref<AdminReportPage>()
const appeals = ref<AdminAppealPage>()
const loading = ref(true)
const refreshing = ref(false)
const detailLoading = ref(false)
const relatedLoading = ref(false)
const submitting = ref(false)
const offline = ref(!navigator.onLine)
const stale = ref(false)
const forbidden = ref(false)
const queueError = ref<string>()
const relatedError = ref<string>()
const detailError = ref<string>()
const notice = ref<{ tone: 'success' | 'warning' | 'danger'; title: string; detail: string }>()
const confirmAction = ref<ConfirmAction>()
const assignmentMode = ref<AssignmentMode>('single')
const mobileStep = ref<MobileStep>('queue')
const selectedIds = ref<string[]>([])
const batchResults = ref<BatchAssignmentResult[]>([])
const filters = reactive({
  page: positiveInt(route.query.page, 1),
  status: stringQuery(route.query.status),
  keyword: stringQuery(route.query.keyword),
  sort: stringQuery(route.query.sort) || 'priority:desc,createdAt:asc',
})
const decisionForm = reactive({
  decision: 'APPROVE' as 'APPROVE' | 'REJECT' | 'ESCALATE',
  reason: '',
  evidenceIds: '',
})
const assignForm = reactive({ assigneeId: '', reason: '' })
let queueRequest: AbortController | undefined
let detailRequest: AbortController | undefined
let relatedRequest: AbortController | undefined

function stringQuery(value: unknown): string {
  return typeof value === 'string' ? value.slice(0, 100) : ''
}
function positiveInt(value: unknown, fallback: number): number {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 1 ? parsed : fallback
}
function formatDate(value?: string): string {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
    : '暂无记录'
}

const items = computed(() => queue.value?.items ?? [])
const batchTargets = computed(() => items.value.filter((item) => selectedIds.value.includes(item.id)
  && !item.assigneeId
  && ['PENDING_REVIEW', 'REVIEWING'].includes(item.status)))
const selectedId = computed(() => stringQuery(route.query.reviewId))
const pendingCount = computed(() => items.value.filter((item) => item.status === 'PENDING_REVIEW').length)
const reviewingCount = computed(() => items.value.filter((item) => item.status === 'REVIEWING').length)
const highRiskCount = computed(() => items.value.filter((item) => ['HIGH', 'CRITICAL'].includes(item.riskLevel ?? '')).length)
const total = computed(() => Number(queue.value?.page.total ?? items.value.length))
const hasMore = computed(() => String(queue.value?.page.hasMore ?? 'false') === 'true')
const canDecide = computed(() => adminSession.hasPermission('review.decide'))
const canAssign = computed(() => adminSession.hasPermission('review.assign'))
const canReadReports = computed(() => adminSession.hasPermission('report.read'))
const canReadAppeals = computed(() => adminSession.hasPermission('appeal.read'))
const writable = computed(() => !offline.value && !stale.value && !submitting.value)
const decisionAllowed = computed(() => canDecide.value && detail.value?.status === 'REVIEWING' && writable.value)
const assignmentAllowed = computed(() => canAssign.value && writable.value && (assignmentMode.value === 'batch'
  ? batchTargets.value.length > 0
  : ['PENDING_REVIEW', 'REVIEWING'].includes(detail.value?.status ?? '')))
const isEmpty = computed(() => !loading.value && !queueError.value && items.value.length === 0)
const evidenceIds = computed(() => (reports.value?.items ?? [])
  .filter((item) => item.subjectId === detail.value?.subjectId)
  .map((item) => item.id))
const relatedReports = computed(() => (reports.value?.items ?? [])
  .filter((item) => !detail.value || item.subjectId === detail.value.subjectId))
const relatedAppeals = computed(() => (appeals.value?.items ?? [])
  .filter((item) => !detail.value || item.subjectId === detail.value.subjectId))

const statusLabels: Record<string, string> = {
  PENDING_REVIEW: '待分配', REVIEWING: '审核中', APPROVED: '已通过', REJECTED: '已驳回',
}
const riskLabels: Record<string, string> = {
  CRITICAL: '紧急', HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险',
}
const subjectLabels: Record<string, string> = {
  CONTENT: '内容', IDENTITY: '实名', RED_PACKET: '红包', APPEAL: '申诉',
}
const decisionLabels: Record<string, string> = {
  APPROVE: '通过', REJECT: '驳回', ESCALATE: '转二审',
}
function statusLabel(value?: string) { return value ? (statusLabels[value] ?? '状态待确认') : '暂无' }
function riskLabel(value?: string) { return value ? (riskLabels[value] ?? '风险待评估') : '未标记' }
function subjectLabel(value?: string) { return value ? (subjectLabels[value] ?? '其他业务') : '未知业务' }
function decisionLabel(value?: string) { return value ? (decisionLabels[value] ?? '已处理') : '未决定' }

function businessError(caught: unknown, fallback: string): string {
  if (!isApiRequestError(caught)) return fallback
  if (caught.status === 0) return '网络连接不稳定，请检查网络后重试'
  if (caught.status === 403) return '当前账号没有访问此业务区域的权限'
  if (caught.status === 404) return '审核任务不存在或已不可访问'
  if (caught.status === 409) return '任务数据已变化，请重新加载后再操作'
  if (caught.status === 422) return caught.message || '当前状态不允许执行该操作'
  if (caught.status === 429) return caught.retryAfter
    ? `操作过于频繁，请在 ${caught.retryAfter} 秒后重试`
    : '操作过于频繁，请稍后重试'
  return fallback
}

async function handleUnauthorized(caught: unknown): Promise<boolean> {
  if (!isApiRequestError(caught) || caught.status !== 401) return false
  adminSession.clear()
  await router.replace({ path: '/auth/login', query: { redirect: route.fullPath } })
  return true
}

async function syncUrl(): Promise<void> {
  await router.replace({
    query: {
      ...route.query,
      page: filters.page === 1 ? undefined : String(filters.page),
      status: filters.status || undefined,
      keyword: filters.keyword.trim() || undefined,
      sort: filters.sort === 'priority:desc,createdAt:asc' ? undefined : filters.sort,
    },
  })
}

async function loadQueue(mode: 'initial' | 'refresh' | 'page' = 'initial'): Promise<void> {
  if (!navigator.onLine) {
    offline.value = true
    loading.value = false
    queueError.value = queue.value ? undefined : '当前设备离线，请恢复网络后重试'
    return
  }
  queueRequest?.abort()
  const request = new AbortController()
  queueRequest = request
  offline.value = false
  queueError.value = undefined
  forbidden.value = false
  if (mode === 'initial') loading.value = true
  else refreshing.value = true
  try {
    queue.value = await adminReviewsApi.queue({
      page: filters.page,
      pageSize: 20,
      status: filters.status || undefined,
      keyword: filters.keyword.trim() || undefined,
      sort: filters.sort,
    }, { signal: request.signal })
    selectedIds.value = selectedIds.value.filter((id) => items.value.some((item) => item.id === id))
  } catch (caught) {
    if (isApiRequestError(caught) && caught.code === 'REQUEST_ABORTED') return
    if (await handleUnauthorized(caught)) return
    forbidden.value = isApiRequestError(caught) && caught.status === 403
    queueError.value = businessError(caught, '审核队列暂时无法加载，请稍后重试')
  } finally {
    if (queueRequest === request) queueRequest = undefined
    loading.value = false
    refreshing.value = false
  }
}

async function loadDetail(id: string): Promise<void> {
  detailRequest?.abort()
  relatedRequest?.abort()
  if (!id) { detail.value = undefined; return }
  const request = new AbortController()
  detailRequest = request
  detailLoading.value = true
  detailError.value = undefined
  try {
    detail.value = await adminReviewsApi.detail(id, { signal: request.signal })
    stale.value = false
    decisionForm.reason = ''
    decisionForm.evidenceIds = ''
    assignForm.assigneeId = detail.value.assigneeId ?? ''
    assignForm.reason = ''
    void loadRelated(detail.value.subjectId)
  } catch (caught) {
    if (isApiRequestError(caught) && caught.code === 'REQUEST_ABORTED') return
    if (await handleUnauthorized(caught)) return
    detailError.value = businessError(caught, '任务详情暂时无法加载，请稍后重试')
    if (isApiRequestError(caught) && caught.status === 404) detail.value = undefined
  } finally {
    if (detailRequest === request) detailRequest = undefined
    detailLoading.value = false
  }
}

async function loadRelated(subjectId: string): Promise<void> {
  relatedRequest?.abort()
  const request = new AbortController()
  relatedRequest = request
  relatedLoading.value = true
  relatedError.value = undefined
  const tasks: Promise<void>[] = []
  if (canReadReports.value) tasks.push(adminReviewsApi.reports({
    page: 1, pageSize: 20, keyword: subjectId, sort: 'createdAt:desc',
  }, { signal: request.signal }).then((value) => { reports.value = value }))
  else reports.value = undefined
  if (canReadAppeals.value) tasks.push(adminReviewsApi.appeals({
    page: 1, pageSize: 20, keyword: subjectId, sort: 'createdAt:desc',
  }, { signal: request.signal }).then((value) => { appeals.value = value }))
  else appeals.value = undefined
  try {
    const outcomes = await Promise.allSettled(tasks)
    const failure = outcomes.find((outcome) => outcome.status === 'rejected') as PromiseRejectedResult | undefined
    if (failure && !(isApiRequestError(failure.reason) && failure.reason.code === 'REQUEST_ABORTED')) {
      relatedError.value = businessError(failure.reason, '关联举报或申诉暂时无法加载')
    }
  } finally {
    if (relatedRequest === request) relatedRequest = undefined
    relatedLoading.value = false
  }
}

async function applyFilters(): Promise<void> {
  filters.page = 1
  await syncUrl()
  await loadQueue('refresh')
}
async function changePage(next: number): Promise<void> {
  if (next < 1 || refreshing.value) return
  filters.page = next
  await syncUrl()
  await loadQueue('page')
}
async function selectTask(item: AdminReviewResource): Promise<void> {
  assignmentMode.value = 'single'
  await router.replace({ query: { ...route.query, reviewId: item.id } })
  mobileStep.value = 'context'
}
function toggleSelected(id: string, checked: boolean): void {
  selectedIds.value = checked
    ? [...new Set([...selectedIds.value, id])]
    : selectedIds.value.filter((value) => value !== id)
}

function openBatchAssignment(): void {
  if (!canAssign.value || !batchTargets.value.length) return
  assignmentMode.value = 'batch'
  assignForm.assigneeId = ''
  assignForm.reason = '批量分配'
  batchResults.value = []
  mobileStep.value = 'decision'
}

function openConfirm(action: ConfirmAction, mode: AssignmentMode = 'single'): void {
  notice.value = undefined
  if (action === 'decide') {
    if (!decisionAllowed.value) return
    if (!decisionForm.reason.trim()) {
      notice.value = { tone: 'danger', title: '请填写审核原因', detail: '原因将进入审核记录，不能为空。' }
      return
    }
  } else {
    assignmentMode.value = mode
    if (!assignmentAllowed.value) return
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(assignForm.assigneeId.trim())) {
      notice.value = { tone: 'danger', title: '请填写有效审核员编号', detail: '审核员编号不能为空且不能包含特殊路径字符。' }
      return
    }
  }
  confirmAction.value = action
}
function closeConfirm(): void { if (!submitting.value) confirmAction.value = undefined }

function applyOutcome(outcome: AdminReviewCommandOutcome): void {
  if ('subjectType' in outcome && 'subjectId' in outcome) detail.value = outcome as AdminReviewResource
}

async function submitConfirmed(): Promise<void> {
  if (!confirmAction.value || !writable.value) return
  const action = confirmAction.value
  if (action === 'decide' && !detail.value) return
  if (action === 'assign' && assignmentMode.value === 'single' && !detail.value) return
  submitting.value = true
  notice.value = undefined
  try {
    if (action === 'decide') {
      if (!detail.value) return
      const ids = decisionForm.evidenceIds.split(',').map((value) => value.trim()).filter(Boolean)
      const outcome = await adminReviewsApi.decide(detail.value.id, {
        decision: decisionForm.decision,
        reason: decisionForm.reason.trim(),
        expectedVersion: detail.value.version,
        evidenceIds: ids.length ? [...new Set(ids)].slice(0, 100) : undefined,
      })
      applyOutcome(outcome)
      notice.value = {
        tone: 'success',
        title: `${decisionLabel(decisionForm.decision)}已提交`,
        detail: '当前任务、队列计数和可用操作已按最新结果更新。',
      }
    } else if (assignmentMode.value === 'batch') {
      const targets = [...batchTargets.value]
      const results: BatchAssignmentResult[] = []
      const succeeded = new Set<string>()
      for (const target of targets) {
        try {
          const outcome = await adminReviewsApi.assign(target.id, {
            assigneeId: assignForm.assigneeId.trim(),
            reason: assignForm.reason.trim() || undefined,
            expectedVersion: target.version,
          })
          succeeded.add(target.id)
          results.push({ id: target.id, status: 'success', detail: `已按服务端版本 v${target.version} 完成分配` })
          if (detail.value?.id === target.id) applyOutcome(outcome)
        } catch (caught) {
          if (await handleUnauthorized(caught)) return
          results.push({ id: target.id, status: 'failed', detail: businessError(caught, '分配未完成，请单独重试') })
        }
      }
      batchResults.value = results
      selectedIds.value = selectedIds.value.filter((id) => !succeeded.has(id))
      const failed = results.length - succeeded.size
      notice.value = {
        tone: failed === 0 ? 'success' : succeeded.size ? 'warning' : 'danger',
        title: failed === 0 ? '批量分配已完成' : '批量分配部分完成',
        detail: `成功 ${succeeded.size} 项，失败 ${failed} 项；逐项结果已保留在分配面板。`,
      }
    } else {
      if (!detail.value) return
      const outcome = await adminReviewsApi.assign(detail.value.id, {
        assigneeId: assignForm.assigneeId.trim(),
        reason: assignForm.reason.trim() || undefined,
        expectedVersion: detail.value.version,
      })
      applyOutcome(outcome)
      notice.value = { tone: 'success', title: '审核员已分配', detail: '任务已进入处理状态并更新服务端版本。' }
    }
    stale.value = false
    confirmAction.value = undefined
    await loadQueue('refresh')
    if (assignmentMode.value === 'batch') {
      const succeeded = new Set(batchResults.value.filter((result) => result.status === 'success').map((result) => result.id))
      selectedIds.value = selectedIds.value.filter((id) => !succeeded.has(id))
    }
    if (detail.value?.id === selectedId.value) await loadDetail(selectedId.value)
  } catch (caught) {
    if (await handleUnauthorized(caught)) return
    stale.value = isApiRequestError(caught) && caught.status === 409
    notice.value = {
      tone: stale.value ? 'warning' : 'danger',
      title: stale.value ? '任务数据已经变化' : '操作未完成',
      detail: businessError(caught, '操作暂时无法完成，输入内容已保留，请稍后重试'),
    }
    if (isApiRequestError(caught) && caught.status === 403) confirmAction.value = undefined
  } finally {
    submitting.value = false
  }
}

function updateNetwork(): void {
  offline.value = !navigator.onLine
  if (!offline.value && !queue.value) void loadQueue()
}

watch(selectedId, (id) => { void loadDetail(id) }, { immediate: true })
onMounted(() => {
  window.addEventListener('online', updateNetwork)
  window.addEventListener('offline', updateNetwork)
  void loadQueue()
})
onBeforeUnmount(() => {
  queueRequest?.abort()
  detailRequest?.abort()
  relatedRequest?.abort()
  window.removeEventListener('online', updateNetwork)
  window.removeEventListener('offline', updateNetwork)
})
</script>

<template>
  <article class="page review-page">
    <header class="page-heading review-heading">
      <div>
        <div class="eyebrow">审核管理</div>
        <h1>统一审核工作台</h1>
        <p>集中处理内容、实名、红包与申诉任务，分配、证据和决定保持在同一业务上下文。</p>
      </div>
      <div class="page-actions">
        <button class="ghost-button" :disabled="refreshing || offline" @click="loadQueue('refresh')">
          {{ refreshing ? '刷新中...' : '刷新队列' }}
        </button>
      </div>
    </header>

    <StatusNotice v-if="offline" tone="warning" title="当前设备离线" detail="已加载内容保持只读，恢复网络后可刷新；所有写操作已禁用。" />
    <StatusNotice v-if="forbidden" tone="danger" title="无审核读取权限" detail="当前会话不能查看审核队列，页面不会泄露任务是否存在。" />
    <StatusNotice v-if="notice" :tone="notice.tone" :title="notice.title" :detail="notice.detail" />

    <section class="review-metrics" aria-label="本页审核概览">
      <div><span>队列总量</span><strong>{{ total }}</strong><small>当前筛选</small></div>
      <div><span>待分配</span><strong>{{ pendingCount }}</strong><small>本页任务</small></div>
      <div><span>审核中</span><strong>{{ reviewingCount }}</strong><small>本页任务</small></div>
      <div><span>高风险</span><strong>{{ highRiskCount }}</strong><small>需优先关注</small></div>
    </section>

    <form class="review-filters" @submit.prevent="applyFilters">
      <label class="field review-search-field"><span class="field-label">关键词</span><input v-model="filters.keyword" class="input" maxlength="100" placeholder="任务编号、主体编号" /></label>
      <label class="field"><span class="field-label">状态</span><select v-model="filters.status" class="input"><option value="">全部状态</option><option value="PENDING_REVIEW">待分配</option><option value="REVIEWING">审核中</option><option value="APPROVED">已通过</option><option value="REJECTED">已驳回</option></select></label>
      <label class="field"><span class="field-label">排序</span><select v-model="filters.sort" class="input"><option value="priority:desc,createdAt:asc">风险优先 · 先提交先处理</option><option value="createdAt:desc">最新提交</option><option value="createdAt:asc">最早提交</option><option value="updatedAt:desc">最近更新</option></select></label>
      <button class="primary-button" :disabled="refreshing || offline">应用筛选</button>
    </form>

    <nav class="review-mobile-steps" aria-label="审核工作台步骤">
      <button :class="{ active: mobileStep === 'queue' }" @click="mobileStep = 'queue'">队列</button>
      <button :class="{ active: mobileStep === 'context' }" :disabled="!detail" @click="mobileStep = 'context'">任务上下文</button>
      <button :class="{ active: mobileStep === 'decision' }" :disabled="!detail && assignmentMode !== 'batch'" @click="mobileStep = 'decision'">处理决定</button>
    </nav>

    <section class="review-workbench" :data-mobile-step="mobileStep">
      <section class="review-pane review-queue-pane">
        <div class="review-pane-heading">
          <div><h2>审核队列</h2><p>第 {{ filters.page }} 页 · 已选择 {{ selectedIds.length }} 项</p></div>
          <span class="review-live-indicator">实时队列</span>
        </div>
        <div v-if="canAssign && selectedIds.length" class="review-batch-bar">
          <span>仅允许批量分配未领取任务，最终决定必须逐项完成。</span>
          <button class="secondary-button" @click="openBatchAssignment">填写分配信息</button>
        </div>
        <div v-if="loading" class="review-queue-loading" aria-label="正在加载审核队列"><div v-for="n in 6" :key="n" class="skeleton review-row-skeleton" /></div>
        <div v-else-if="queueError && !queue" class="review-pane-state"><strong>{{ forbidden ? '无法访问审核队列' : '队列暂时无法加载' }}</strong><p>{{ queueError }}</p><button v-if="!forbidden" class="primary-button" :disabled="offline" @click="loadQueue()">重新加载</button></div>
        <div v-else-if="isEmpty" class="review-pane-state"><strong>当前筛选没有审核任务</strong><p>调整状态或关键词后重新查询，筛选条件会保留在地址中。</p><button class="ghost-button" @click="filters.status=''; filters.keyword=''; applyFilters()">清除筛选</button></div>
        <template v-else>
          <div v-if="queueError" class="review-inline-error"><span>{{ queueError }}</span><button @click="loadQueue('refresh')">重试刷新</button></div>
          <div class="review-table-wrap" :aria-busy="refreshing">
            <table class="review-table">
              <thead><tr><th><span class="sr-only">选择</span></th><th>任务</th><th>风险</th><th>状态</th><th>处理人</th></tr></thead>
              <tbody>
                <tr v-for="item in items" :key="item.id" :class="{ selected: selectedId === item.id }" @click="selectTask(item)">
                  <td><input type="checkbox" :checked="selectedIds.includes(item.id)" :disabled="Boolean(item.assigneeId) || !['PENDING_REVIEW', 'REVIEWING'].includes(item.status)" :aria-label="`选择任务 ${item.id}`" @click.stop @change="toggleSelected(item.id, ($event.target as HTMLInputElement).checked)" /></td>
                  <td><button class="review-task-link" @click.stop="selectTask(item)"><strong>{{ subjectLabel(item.subjectType) }} · {{ item.subjectId }}</strong><small>任务 {{ item.id }} · v{{ item.version }}</small></button></td>
                  <td><span class="risk-chip" :data-risk="item.riskLevel">{{ riskLabel(item.riskLevel) }}</span></td>
                  <td><span class="status-chip" :data-status="item.status">{{ statusLabel(item.status) }}</span></td>
                  <td>{{ item.assigneeId ? `审核员 ${item.assigneeId}` : '待领取' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="review-pagination"><button class="ghost-button" :disabled="filters.page <= 1 || refreshing" @click="changePage(filters.page - 1)">上一页</button><span>第 {{ filters.page }} 页</span><button class="ghost-button" :disabled="!hasMore || refreshing" @click="changePage(filters.page + 1)">下一页</button></div>
        </template>
      </section>

      <section class="review-pane review-context-pane">
        <div class="review-pane-heading"><div><h2>任务上下文</h2><p>业务对象、证据与处理历史</p></div><span v-if="detail" class="status-chip" :data-status="detail.status">{{ statusLabel(detail.status) }}</span></div>
        <div v-if="!selectedId" class="review-pane-state"><strong>从队列选择一项任务</strong><p>选中后会加载最新服务端版本以及有权限查看的举报和申诉。</p></div>
        <div v-else-if="detailLoading" class="review-detail-loading"><div v-for="n in 5" :key="n" class="skeleton" /></div>
        <div v-else-if="detailError" class="review-pane-state"><strong>任务上下文暂时不可用</strong><p>{{ detailError }}</p><button class="primary-button" :disabled="offline" @click="loadDetail(selectedId)">重新加载</button></div>
        <template v-else-if="detail">
          <section class="review-context-summary">
            <div class="review-subject-mark">{{ subjectLabel(detail.subjectType).slice(0, 1) }}</div>
            <div><span>{{ subjectLabel(detail.subjectType) }}</span><strong>主体 {{ detail.subjectId }}</strong><small>任务 {{ detail.id }} · 服务端版本 {{ detail.version }}</small></div>
          </section>
          <dl class="review-facts">
            <div><dt>风险等级</dt><dd>{{ riskLabel(detail.riskLevel) }}</dd></div>
            <div><dt>当前处理人</dt><dd>{{ detail.assigneeId ? `审核员 ${detail.assigneeId}` : '尚未分配' }}</dd></div>
            <div><dt>最近决定</dt><dd>{{ decisionLabel(detail.decision) }}</dd></div>
            <div><dt>进入队列</dt><dd>{{ formatDate(detail.createdAt) }}</dd></div>
          </dl>

          <section class="review-context-section">
            <div class="review-section-title"><div><h3>证据预览</h3><p>只展示接口实际返回的关联举报</p></div><span>{{ relatedReports.length }} 项</span></div>
            <div v-if="relatedLoading" class="skeleton review-evidence-skeleton" />
            <div v-else-if="!canReadReports" class="review-empty-inline">当前角色无举报证据读取权限。</div>
            <div v-else-if="!relatedReports.length" class="review-empty-inline">当前接口未返回关联举报或证据附件，不展示推测内容。</div>
            <ul v-else class="review-related-list"><li v-for="report in relatedReports" :key="report.id"><div><strong>{{ report.reasonCode || '举报原因待分类' }}</strong><small>举报 {{ report.id }} · {{ formatDate(report.createdAt) }}</small></div><span class="status-chip" :data-status="report.status">{{ statusLabel(report.status) }}</span></li></ul>
          </section>

          <section class="review-context-section">
            <div class="review-section-title"><div><h3>规则与历史</h3><p>审核结果、申诉与业务原因</p></div><span>{{ relatedAppeals.length }} 项申诉</span></div>
            <p v-if="detail.reason" class="review-reason">{{ detail.reason }}</p>
            <div v-if="!canReadAppeals" class="review-empty-inline">当前角色无申诉读取权限。</div>
            <div v-else-if="!relatedAppeals.length" class="review-empty-inline">暂无关联申诉；完整状态时间线接口尚未返回，不生成虚假历史。</div>
            <ul v-else class="review-related-list"><li v-for="appeal in relatedAppeals" :key="appeal.id"><div><strong>{{ appeal.reason || '申诉原因待核查' }}</strong><small>申诉 {{ appeal.id }} · {{ formatDate(appeal.createdAt) }}</small></div><span class="status-chip" :data-status="appeal.status">{{ statusLabel(appeal.status) }}</span></li></ul>
            <div v-if="relatedError" class="review-inline-error"><span>{{ relatedError }}</span><button @click="loadRelated(detail.subjectId)">重试</button></div>
          </section>
          <aside class="review-conflict-note"><strong>利益冲突与二审要求</strong><p>申请人或历史处置人存在利益冲突时不得继续处理。转二审后必须重新分配，由被分配的二审员作出最终决定。</p></aside>
        </template>
      </section>

      <aside class="review-pane review-decision-pane">
        <div class="review-pane-heading"><div><h2>决定面板</h2><p>所有写操作都绑定最新服务端版本</p></div><span v-if="assignmentMode === 'batch'">{{ batchTargets.length }} 项</span><span v-else-if="detail">v{{ detail.version }}</span></div>
        <div v-if="!detail && assignmentMode !== 'batch'" class="review-pane-state"><strong>尚未选择任务</strong><p>处理入口会在加载任务上下文并校验权限后显示。</p></div>
        <template v-else>
          <StatusNotice v-if="stale && detail" tone="warning" title="任务版本已过期" detail="写操作已冻结。重新加载并确认差异后才能继续。" />
          <button v-if="stale && detail" class="primary-button review-full-button" :disabled="offline" @click="loadDetail(detail.id)">重新加载最新版本</button>

          <section v-if="detail" class="review-action-section">
            <div class="review-section-title"><div><h3>审核决定</h3><p>仅审核中任务可提交最终决定或转二审</p></div></div>
            <div v-if="!canDecide" class="review-empty-inline">当前角色无审核决定权限。</div>
            <form v-else class="review-action-form" @submit.prevent="openConfirm('decide')">
              <label class="field"><span class="field-label">决定</span><select v-model="decisionForm.decision" class="input"><option value="APPROVE">通过</option><option value="REJECT">驳回</option><option value="ESCALATE">转二审</option></select></label>
              <label class="field"><span class="field-label">原因</span><textarea v-model="decisionForm.reason" class="input" maxlength="2000" placeholder="说明证据判断、影响和处置依据" /></label>
              <label class="field"><span class="field-label">证据编号</span><input v-model="decisionForm.evidenceIds" class="input" maxlength="2000" :placeholder="evidenceIds.length ? evidenceIds.join(', ') : '可选，多个编号以逗号分隔'" /></label>
              <button class="primary-button" :disabled="!decisionAllowed">提交决定</button>
              <small v-if="detail.status !== 'REVIEWING'">当前任务不是审核中状态，决定入口已锁定。</small>
            </form>
          </section>

          <section class="review-action-section">
            <div class="review-section-title"><div><h3>{{ assignmentMode === 'batch' ? `批量分配 ${batchTargets.length} 项` : '分配审核员' }}</h3><p>{{ assignmentMode === 'batch' ? '仅处理已选中的未领取任务，按各自版本逐项提交' : '支持待分配和审核中任务转派' }}</p></div></div>
            <div v-if="!canAssign" class="review-empty-inline">当前角色无审核分配权限。</div>
            <form v-else class="review-action-form" @submit.prevent="openConfirm('assign', assignmentMode)">
              <label class="field"><span class="field-label">审核员编号</span><input v-model="assignForm.assigneeId" class="input" maxlength="64" placeholder="输入有效管理员编号" /></label>
              <label class="field"><span class="field-label">分配原因</span><textarea v-model="assignForm.reason" class="input" maxlength="2000" placeholder="说明专业领域、二审或转派原因" /></label>
              <button class="secondary-button" :disabled="!assignmentAllowed">确认分配</button>
            </form>
            <ul v-if="batchResults.length" class="review-batch-results" aria-label="批量分配结果">
              <li v-for="result in batchResults" :key="result.id" :data-status="result.status"><strong>任务 {{ result.id }} {{ result.status === 'success' ? '分配成功' : '分配失败' }}</strong><span>{{ result.detail }}</span></li>
            </ul>
          </section>
        </template>
      </aside>
    </section>

    <div v-if="confirmAction && (detail || assignmentMode === 'batch')" class="modal-backdrop" role="presentation" @click.self="closeConfirm">
      <section class="modal review-confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="review-confirm-title">
        <header class="modal-header"><div><h2 id="review-confirm-title">确认{{ confirmAction === 'decide' ? '提交审核决定' : '分配审核员' }}</h2><p>请核对目标、影响、原因和服务端版本。提交后将触发通知与审计。</p></div><button class="icon-button" aria-label="关闭确认窗口" :disabled="submitting" @click="closeConfirm">x</button></header>
        <dl class="review-confirm-facts">
          <div><dt>目标任务</dt><dd>{{ assignmentMode === 'batch' && confirmAction === 'assign' ? `${batchTargets.length} 个未领取任务：${batchTargets.map((item) => item.id).join('、')}` : `${detail?.id} · ${subjectLabel(detail?.subjectType)} ${detail?.subjectId}` }}</dd></div>
          <div><dt>服务端版本</dt><dd>{{ assignmentMode === 'batch' && confirmAction === 'assign' ? batchTargets.map((item) => `${item.id}(v${item.version})`).join('、') : `v${detail?.version}` }}</dd></div>
          <div><dt>影响</dt><dd>{{ confirmAction === 'decide' ? `${decisionLabel(decisionForm.decision)}并更新任务状态` : `${assignmentMode === 'batch' ? `逐项分配 ${batchTargets.length} 个任务` : '当前任务'}给审核员 ${assignForm.assigneeId}` }}</dd></div>
          <div><dt>原因</dt><dd>{{ confirmAction === 'decide' ? decisionForm.reason : (assignForm.reason || '按当前队列安排分配') }}</dd></div>
        </dl>
        <StatusNotice tone="warning" title="提交前最后检查" detail="审核决定不可直接修改；版本冲突时系统会拒绝覆盖并要求重新加载。" />
        <footer class="modal-footer"><button class="ghost-button" :disabled="submitting" @click="closeConfirm">取消</button><button :class="confirmAction === 'decide' && decisionForm.decision === 'REJECT' ? 'danger-button' : 'primary-button'" :disabled="submitting" @click="submitConfirmed">{{ submitting ? '提交中...' : '确认并提交' }}</button></footer>
      </section>
    </div>
  </article>
</template>
