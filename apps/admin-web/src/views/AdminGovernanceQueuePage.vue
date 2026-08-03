<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusNotice from '../components/StatusNotice.vue'
import { adminR15Api, adminSession, isApiRequestError, type AdminGovernanceDecision } from '../services'

type Kind = 'chat' | 'report' | 'appeal'
type QueueItem = Record<string, unknown> & { id: string; version: number }
const props = defineProps<{ kind: Kind }>()
const route = useRoute()
const router = useRouter()
const rows = ref<QueueItem[]>([])
const page = ref<{ page?: number; pageSize?: number; total?: string | number; hasMore?: string | boolean }>()
const selected = ref<QueueItem>()
const loading = ref(true)
const refreshing = ref(false)
const submitting = ref(false)
const forbidden = ref(false)
const conflict = ref(false)
const offline = ref(!navigator.onLine)
const error = ref<string>()
const notice = ref<{ tone: 'success' | 'warning' | 'danger'; title: string; detail?: string }>()
const filters = reactive({
  page: positiveInt(route.query.page, 1),
  status: text(route.query.status), keyword: text(route.query.keyword),
  sort: text(route.query.sort) || 'createdAt:desc',
})
const decision = reactive<AdminGovernanceDecision>({ decision: 'APPROVE', reason: '', expectedVersion: 0, evidenceIds: [] })
let request: AbortController | undefined

const config = computed(() => ({
  chat: { title: '聊天举报', subtitle: '核查消息证据并处理聊天举报', read: 'chat.report.read', decide: 'chat.report.decide', empty: '暂无聊天举报' },
  report: { title: '内容举报处理', subtitle: '核查举报对象、证据与用户上下文', read: 'report.read', decide: 'report.decide', empty: '暂无内容举报' },
  appeal: { title: '申诉处理', subtitle: '复核原处理结果与申诉补充材料', read: 'appeal.read', decide: 'appeal.decide', empty: '暂无待处理申诉' },
})[props.kind])
const canDecide = computed(() => adminSession.hasPermission(config.value.decide))
const total = computed(() => Number(page.value?.total ?? rows.value.length))
const pending = computed(() => rows.value.filter((item) => ['PENDING', 'OPEN', 'REVIEWING'].includes(status(item))).length)
const decided = computed(() => rows.value.filter((item) => Boolean(stringValue(item.decision)) || ['DECIDED', 'APPROVED', 'REJECTED', 'CLOSED'].includes(status(item))).length)
const writable = computed(() => canDecide.value && !offline.value && !conflict.value && !submitting.value && Boolean(selected.value))

function text(value: unknown): string { return typeof value === 'string' ? value.slice(0, 100) : '' }
function positiveInt(value: unknown, fallback: number): number { const parsed = Number(value); return Number.isInteger(parsed) && parsed >= 1 ? parsed : fallback }
function stringValue(value: unknown): string { return typeof value === 'string' ? value : '' }
function status(item: QueueItem): string { return stringValue(item.status) }
function label(value: string): string {
  return ({ PENDING: '待处理', OPEN: '待处理', REVIEWING: '处理中', APPROVED: '已支持', REJECTED: '已驳回', ESCALATED: '已升级', CLOSED: '已关闭' } as Record<string, string>)[value] ?? '待核查'
}
function subject(item: QueueItem): string {
  if (props.kind === 'chat') return `会话 ${item.id}`
  return `${stringValue(item.subjectType) || (props.kind === 'appeal' ? '申诉对象' : '内容')} ${stringValue(item.subjectId) || item.id}`
}
function summary(item: QueueItem): string {
  if (props.kind === 'chat') {
    const message = item.lastMessage as Record<string, unknown> | undefined
    return stringValue(message?.preview) || '契约未返回消息摘要，请按会话标识核查'
  }
  return stringValue(item.reason) || stringValue(item.reasonCode) || '未提供补充说明'
}
function actor(item: QueueItem): string { return stringValue(item.reporterId) || stringValue(item.appellantId) || '系统脱敏' }
function created(item: QueueItem): string {
  const value = stringValue(item.createdAt) || stringValue(item.updatedAt)
  return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '暂无时间'
}
function errorMessage(caught: unknown, fallback: string): string {
  if (!isApiRequestError(caught)) return fallback
  if (caught.status === 0) return '网络连接不可用，请恢复网络后重试'
  if (caught.status === 403) return '当前账号没有查看此队列的权限'
  if (caught.status === 404) return '该记录已不存在或不可访问'
  if (caught.status === 409) return '服务端数据已变化，请重新加载后再处理'
  if (caught.status === 422) return caught.message || '当前状态不允许执行该决定'
  if (caught.status === 429) return caught.retryAfter ? `操作过于频繁，请在 ${caught.retryAfter} 秒后重试` : '操作过于频繁，请稍后重试'
  return caught.requestId !== 'missing' ? `${fallback}（请求 ${caught.requestId}）` : fallback
}
async function unauthorized(caught: unknown): Promise<boolean> {
  if (!isApiRequestError(caught) || caught.status !== 401) return false
  adminSession.clear(); await router.replace({ path: '/auth/login', query: { redirect: route.fullPath } }); return true
}
async function syncUrl() {
  await router.replace({ query: { page: filters.page === 1 ? undefined : String(filters.page), status: filters.status || undefined, keyword: filters.keyword.trim() || undefined, sort: filters.sort === 'createdAt:desc' ? undefined : filters.sort } })
}
async function load(mode: 'initial' | 'refresh' = 'initial') {
  request?.abort()
  if (!navigator.onLine) { offline.value = true; loading.value = false; error.value = rows.value.length ? undefined : '当前设备离线，无法读取队列'; return }
  const current = new AbortController(); request = current; offline.value = false; error.value = undefined; forbidden.value = false
  mode === 'initial' ? loading.value = true : refreshing.value = true
  try {
    const query = { page: filters.page, pageSize: 20, status: filters.status || undefined, keyword: filters.keyword.trim() || undefined, sort: filters.sort }
    const result = props.kind === 'chat' ? await adminR15Api.chatReports(query, { signal: current.signal })
      : props.kind === 'report' ? await adminR15Api.contentReports(query, { signal: current.signal })
        : await adminR15Api.appeals(query, { signal: current.signal })
    rows.value = result.items as unknown as QueueItem[]; page.value = result.page
    const selectedId = text(route.query.itemId)
    selected.value = rows.value.find((item) => item.id === (selected.value?.id || selectedId))
    if (selected.value) decision.expectedVersion = selected.value.version
    conflict.value = false
  } catch (caught) {
    if (isApiRequestError(caught) && caught.code === 'REQUEST_ABORTED') return
    if (await unauthorized(caught)) return
    forbidden.value = isApiRequestError(caught) && caught.status === 403
    error.value = errorMessage(caught, `${config.value.title}暂时无法加载`)
  } finally { if (request === current) request = undefined; loading.value = false; refreshing.value = false }
}
async function applyFilters() { filters.page = 1; selected.value = undefined; await syncUrl(); await load() }
async function select(item: QueueItem) {
  selected.value = item; conflict.value = false; decision.decision = 'APPROVE'; decision.reason = ''; decision.expectedVersion = item.version; decision.evidenceIds = []
  await router.replace({ query: { ...route.query, itemId: item.id } })
}
async function submit() {
  if (!selected.value || !decision.reason.trim() || !writable.value) return
  if (!window.confirm(`确认对 ${subject(selected.value)} 提交“${decision.decision}”决定？提交后将进入审计记录。`)) return
  submitting.value = true; notice.value = undefined
  const body = { ...decision, reason: decision.reason.trim(), expectedVersion: selected.value.version, evidenceIds: decision.evidenceIds?.filter(Boolean) }
  try {
    if (props.kind === 'chat') await adminR15Api.decideChat(selected.value.id, body)
    else if (props.kind === 'report') await adminR15Api.decideReport(selected.value.id, body)
    else await adminR15Api.decideAppeal(selected.value.id, body)
    notice.value = { tone: 'success', title: `处理${config.value.title}成功`, detail: '队列和审计状态已按服务端结果刷新。' }
    await load('refresh')
  } catch (caught) {
    if (await unauthorized(caught)) return
    conflict.value = isApiRequestError(caught) && caught.status === 409
    notice.value = { tone: conflict.value ? 'warning' : 'danger', title: conflict.value ? '数据冲突，未覆盖新版本' : '提交失败', detail: errorMessage(caught, '处理决定未提交，请稍后重试') }
  } finally { submitting.value = false }
}
function online() { offline.value = false; void load('refresh') }
function offlineNow() { offline.value = true }
watch(() => props.kind, () => { rows.value = []; selected.value = undefined; void load() })
onMounted(() => { window.addEventListener('online', online); window.addEventListener('offline', offlineNow); void load() })
onBeforeUnmount(() => { request?.abort(); window.removeEventListener('online', online); window.removeEventListener('offline', offlineNow) })
</script>

<template>
  <div class="r15-page">
    <header class="r15-heading"><div><p class="eyebrow">R15 治理工作台</p><h1>{{ config.title }}</h1><p>{{ config.subtitle }}</p></div><button type="button" :disabled="refreshing" @click="load('refresh')">{{ refreshing ? '刷新中' : '刷新' }}</button></header>
    <StatusNotice v-if="offline" tone="warning" title="当前离线" detail="保留已加载内容为只读快照，恢复网络后自动刷新。" />
    <StatusNotice v-if="notice" :tone="notice.tone" :title="notice.title" :detail="notice.detail" />
    <StatusNotice v-if="error && rows.length" tone="warning" title="刷新失败，已保留当前队列" :detail="error" />
    <section class="r15-metrics" aria-label="队列统计"><div><strong>{{ total }}</strong><span>符合筛选</span></div><div><strong>{{ pending }}</strong><span>待处理</span></div><div><strong>{{ decided }}</strong><span>本页已决定</span></div></section>
    <form class="r15-filter" @submit.prevent="applyFilters"><label>状态<select v-model="filters.status"><option value="">全部状态</option><option value="PENDING">待处理</option><option value="REVIEWING">处理中</option><option value="APPROVED">已支持</option><option value="REJECTED">已驳回</option></select></label><label>关键词<input v-model="filters.keyword" maxlength="100" placeholder="编号、对象或用户" /></label><label>排序<select v-model="filters.sort"><option value="createdAt:desc">最新创建</option><option value="createdAt:asc">最早创建</option><option value="updatedAt:desc">最近更新</option></select></label><button type="submit">查询</button></form>
    <StatusNotice v-if="forbidden" tone="danger" title="无访问权限" detail="页面未展示任何敏感队列数据，请联系管理员授予读取权限。" />
    <div v-else-if="loading" class="r15-skeleton" aria-label="正在加载"><span v-for="n in 5" :key="n" /></div>
    <StatusNotice v-else-if="error && !rows.length" tone="danger" title="队列加载失败" :detail="error" />
    <section v-else-if="!rows.length" class="r15-empty"><h2>{{ config.empty }}</h2><p>可调整筛选条件或稍后刷新。</p><button type="button" @click="filters.status = ''; filters.keyword = ''; applyFilters()">清除筛选</button></section>
    <div v-else class="r15-workbench">
      <section class="r15-table-panel"><div class="table-wrap"><table><thead><tr><th>业务对象</th><th>证据摘要</th><th>提交人</th><th>状态</th><th>时间</th><th></th></tr></thead><tbody><tr v-for="item in rows" :key="item.id" :class="{ selected: selected?.id === item.id }"><td><strong>{{ subject(item) }}</strong><small>版本 v{{ item.version }}</small></td><td>{{ summary(item) }}</td><td>{{ actor(item) }}</td><td><span class="status-chip">{{ label(status(item)) }}</span></td><td>{{ created(item) }}</td><td><button type="button" class="table-link" @click="select(item)">核查</button></td></tr></tbody></table></div><footer class="r15-pagination"><span>第 {{ filters.page }} 页，共 {{ total }} 条</span><div><button type="button" :disabled="filters.page <= 1" @click="filters.page--; syncUrl().then(() => load())">上一页</button><button type="button" :disabled="String(page?.hasMore) !== 'true'" @click="filters.page++; syncUrl().then(() => load())">下一页</button></div></footer></section>
      <aside class="r15-decision" aria-label="决定面板"><template v-if="selected"><div class="r15-panel-title"><div><p class="eyebrow">任务上下文</p><h2>{{ subject(selected) }}</h2></div><span>v{{ selected.version }}</span></div><dl><div><dt>当前状态</dt><dd>{{ label(status(selected)) }}</dd></div><div><dt>证据摘要</dt><dd>{{ summary(selected) }}</dd></div><div><dt>提交人</dt><dd>{{ actor(selected) }}</dd></div><div><dt>创建时间</dt><dd>{{ created(selected) }}</dd></div></dl><StatusNotice v-if="conflict" tone="warning" title="服务端版本已变化" detail="本地输入已保留。请重新加载最新数据，禁止盲目覆盖。" /><form class="r15-decision-form" @submit.prevent="submit"><h3>决定面板</h3><label>处理决定<select v-model="decision.decision" :disabled="!writable"><option value="APPROVE">支持并处理</option><option value="REJECT">驳回</option><option value="ESCALATE">升级复核</option></select></label><label>处理原因<textarea v-model="decision.reason" maxlength="500" required :disabled="!writable" placeholder="填写可审计的业务依据" /></label><button v-if="canDecide" type="submit" :disabled="!writable || !decision.reason.trim()">{{ submitting ? '提交中' : '提交决定' }}</button><p v-else class="permission-hint">当前角色只有读取权限，决定入口已隐藏。</p><button v-if="conflict" type="button" @click="load('refresh')">重新加载最新版本</button></form></template><div v-else class="r15-empty compact"><h2>选择一条记录</h2><p>查看证据上下文并执行有权限的操作。</p></div></aside>
    </div>
  </div>
</template>

<style scoped src="../r15-operations.css"></style>
