<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusNotice from '../components/StatusNotice.vue'
import {
  adminIdentitiesApi,
  adminSession,
  isApiRequestError,
  type AdminIdentityPageResource,
} from '../services'

const route = useRoute()
const router = useRouter()
const result = ref<AdminIdentityPageResource>()
const loading = ref(true)
const refreshing = ref(false)
const online = ref(navigator.onLine)
const error = ref<string>()
const page = ref(Math.max(1, Number(route.query.page ?? 1) || 1))
const pageSize = 20
const filters = reactive({
  keyword: String(route.query.keyword ?? ''),
  status: String(route.query.status ?? ''),
  sort: String(route.query.sort ?? 'createdAt:desc'),
})
let activeRequest: AbortController | undefined

const total = computed(() => result.value?.page.total ?? '0')
const hasMore = computed(() => result.value?.page.hasMore === 'true')
const empty = computed(() => !loading.value && !error.value && (result.value?.items.length ?? 0) === 0)

const statusLabels: Record<string, string> = {
  SESSION_CREATED: '待活体检测',
  LIVENESS_PENDING: '活体检测中',
  PROVIDER_PROCESSING: '核验中',
  MANUAL_REVIEW: '待人工复核',
  COMPLETED: '已通过',
  REJECTED: '未通过',
  FAILED: '认证失败',
  EXPIRED: '已过期',
}
const failureLabels: Record<string, string> = {
  FACE_NOT_MATCH: '人脸与证件不一致',
  LIVENESS_FAILED: '未通过活体检测',
  IDENTITY_MISMATCH: '身份信息不一致',
  PROVIDER_TIMEOUT: '认证服务响应超时',
}
function statusLabel(value?: string) { return value ? (statusLabels[value] ?? '状态确认中') : '暂无' }
function failureLabel(value?: string) { return value ? (failureLabels[value] ?? '需要人工核查') : '暂无' }
function providerLabel(value?: string) { return value ? '实名认证服务' : '暂未分配' }
function formatDate(value?: string) {
  return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '暂无'
}
function businessError(caught: unknown) {
  if (!isApiRequestError(caught)) return '实名列表暂时无法加载，请稍后重试'
  if (caught.status === 0) return '网络连接不稳定，请检查网络后重试'
  if (caught.status === 403) return '当前账号没有查看实名记录的权限'
  if (caught.status === 429) return caught.retryAfter ? `查询过于频繁，请在${caught.retryAfter}秒后重试` : '查询过于频繁，请稍后重试'
  return '实名列表暂时无法加载，请稍后重试'
}

async function syncUrl() {
  await router.replace({
    query: {
      ...(page.value > 1 ? { page: String(page.value) } : {}),
      ...(filters.keyword.trim() ? { keyword: filters.keyword.trim() } : {}),
      ...(filters.status ? { status: filters.status } : {}),
      ...(filters.sort !== 'createdAt:desc' ? { sort: filters.sort } : {}),
    },
  })
}

async function load(mode: 'initial' | 'refresh' | 'page' = 'initial') {
  if (!online.value) { loading.value = false; error.value = '当前设备离线，请恢复网络后重试'; return }
  activeRequest?.abort()
  const request = new AbortController()
  activeRequest = request
  if (mode === 'initial') loading.value = true
  else refreshing.value = true
  error.value = undefined
  await syncUrl()
  try {
    result.value = await adminIdentitiesApi.list({
      page: page.value,
      pageSize,
      status: filters.status || undefined,
      keyword: filters.keyword.trim() || undefined,
      sort: filters.sort,
    }, { signal: request.signal })
  } catch (caught) {
    if (isApiRequestError(caught) && caught.code === 'REQUEST_ABORTED') return
    if (isApiRequestError(caught) && caught.status === 401) {
      adminSession.clear()
      await router.replace({ path: '/auth/login', query: { redirect: route.fullPath } })
      return
    }
    error.value = businessError(caught)
  } finally {
    if (activeRequest === request) activeRequest = undefined
    loading.value = false
    refreshing.value = false
  }
}
function applyFilters() { page.value = 1; void load('refresh') }
function clearFilters() { filters.keyword = ''; filters.status = ''; filters.sort = 'createdAt:desc'; applyFilters() }
function changePage(next: number) { if (next < 1 || refreshing.value) return; page.value = next; void load('page') }
function updateOnline() { online.value = navigator.onLine; if (!online.value) activeRequest?.abort(); else void load(result.value ? 'refresh' : 'initial') }
onMounted(() => { window.addEventListener('online', updateOnline); window.addEventListener('offline', updateOnline); void load() })
onBeforeUnmount(() => { activeRequest?.abort(); window.removeEventListener('online', updateOnline); window.removeEventListener('offline', updateOnline) })
</script>

<template>
  <article class="page identity-list-page">
    <header class="page-heading">
      <div><div class="eyebrow">身份治理</div><h1>实名认证</h1><p>查看认证进度并处理需要人工复核的记录。</p></div>
      <div class="page-actions"><button class="ghost-button" :disabled="refreshing || !online" @click="load('refresh')">{{ refreshing ? '刷新中…' : '刷新' }}</button></div>
    </header>
    <StatusNotice v-if="!online && result" tone="warning" title="当前设备离线" detail="正在显示最近一次成功读取的结果；恢复网络后可刷新。" />
    <StatusNotice v-if="error && result" tone="warning" title="刷新未完成" :detail="error" />
    <form class="card user-filters" aria-label="实名记录筛选" @submit.prevent="applyFilters">
      <label class="field"><span class="field-label">关键词</span><input v-model="filters.keyword" class="input" maxlength="100" placeholder="用户编号或认证记录" /></label>
      <label class="field"><span class="field-label">认证状态</span><select v-model="filters.status" class="input"><option value="">全部状态</option><option v-for="(label, value) in statusLabels" :key="value" :value="value">{{ label }}</option></select></label>
      <label class="field"><span class="field-label">排序</span><select v-model="filters.sort" class="input"><option value="createdAt:desc">最近创建</option><option value="createdAt:asc">最早创建</option><option value="status:asc">状态升序</option><option value="status:desc">状态降序</option></select></label>
      <button class="primary-button" :disabled="refreshing || !online">应用筛选</button>
    </form>
    <section v-if="loading" class="card skeleton-stack" aria-label="正在加载实名列表"><div v-for="index in 6" :key="index" class="skeleton user-row-skeleton" /></section>
    <section v-else-if="error && !result" class="card error-state"><div class="avatar centered-mark">!</div><h2>实名列表暂时无法加载</h2><p>{{ error }}</p><button class="primary-button" :disabled="!online" @click="load()">重新加载</button></section>
    <section v-else-if="empty" class="card empty-state"><div class="avatar centered-mark">0</div><h2>当前筛选没有认证记录</h2><p>请调整关键词或认证状态后重新查询。</p><button class="ghost-button" @click="clearFilters">清除筛选</button></section>
    <section v-else-if="result" class="card user-table-card" :aria-busy="refreshing">
      <div class="section-heading"><div><h2>认证记录</h2><p>共 {{ total }} 条，当前第 {{ page }} 页</p></div></div>
      <div class="user-table-wrap"><table class="user-table"><thead><tr><th>记录</th><th>认证状态</th><th>认证服务</th><th>失败原因</th><th>有效期</th><th>数据版本</th><th><span class="sr-only">操作</span></th></tr></thead><tbody><tr v-for="item in result.items" :key="item.id"><td><strong>用户 {{ item.userId || '未知' }}</strong><small>认证记录 {{ item.id }}</small></td><td><span class="status-chip" :data-status="item.status">{{ statusLabel(item.status) }}</span></td><td>{{ providerLabel(item.provider) }}</td><td>{{ failureLabel(item.failureCode) }}</td><td>{{ formatDate(item.expiresAt) }}</td><td>v{{ item.version }}</td><td><RouterLink v-if="item.userId" class="secondary-button table-link" :to="{ path: `/identity/${item.userId}`, query: route.query }">查看详情</RouterLink></td></tr></tbody></table></div>
      <footer class="pagination"><button class="ghost-button" :disabled="page <= 1 || refreshing" @click="changePage(page - 1)">上一页</button><span>第 {{ page }} 页</span><button class="ghost-button" :disabled="!hasMore || refreshing" @click="changePage(page + 1)">下一页</button></footer>
    </section>
  </article>
</template>
