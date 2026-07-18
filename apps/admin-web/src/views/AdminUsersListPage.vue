<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import StatusNotice from '../components/StatusNotice.vue'
import {
  adminSession,
  adminUsersApi,
  isApiRequestError,
  type AdminUserPageResource,
} from '../services'

const router = useRouter()
const result = ref<AdminUserPageResource>()
const loading = ref(true)
const refreshing = ref(false)
const forbidden = ref(false)
const online = ref(navigator.onLine)
const error = ref<{ message: string; requestId?: string }>()
const filters = reactive({ keyword: '', status: '', sort: 'createdAt:desc' })
const page = ref(1)
const pageSize = 20
let activeRequest: AbortController | undefined

const total = computed(() => result.value?.page.total ?? '0')
const hasMore = computed(() => result.value?.page.hasMore === 'true')
const isEmpty = computed(() => !loading.value && !error.value && (result.value?.items.length ?? 0) === 0)

const statusLabels: Record<string, string> = {
  ACTIVE: '正常',
  RESTRICTED: '受限',
  FROZEN: '已冻结',
  CANCEL_PENDING: '注销处理中',
  CANCELLED: '已注销',
}

function statusLabel(value?: string) {
  return value ? (statusLabels[value] ?? '其他状态') : '暂无'
}

function formatDate(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
    : '暂无'
}

async function load(mode: 'initial' | 'refresh' | 'page' = 'initial') {
  if (!online.value) {
    loading.value = false
    error.value = { message: '当前设备离线，请恢复网络后重试' }
    return
  }
  activeRequest?.abort()
  const request = new AbortController()
  activeRequest = request
  if (mode === 'initial') loading.value = true
  else refreshing.value = true
  error.value = undefined
  try {
    result.value = await adminUsersApi.listUsers({
      page: page.value,
      pageSize,
      status: filters.status || undefined,
      keyword: filters.keyword.trim() || undefined,
      sort: filters.sort,
    }, { signal: request.signal })
    forbidden.value = false
  } catch (caught) {
    if (isApiRequestError(caught)) {
      if (caught.code === 'REQUEST_ABORTED') return
      if (caught.status === 401) {
        adminSession.clear()
        await router.replace({ path: '/auth/login', query: { redirect: '/users' } })
        return
      }
      if (caught.status === 403) forbidden.value = true
      error.value = { message: caught.message, requestId: caught.requestId }
    } else {
      error.value = { message: '用户列表加载失败，请稍后重试' }
    }
  } finally {
    if (activeRequest === request) activeRequest = undefined
    loading.value = false
    refreshing.value = false
  }
}

function submitFilters() {
  page.value = 1
  void load('refresh')
}

function changePage(nextPage: number) {
  if (nextPage < 1 || refreshing.value) return
  page.value = nextPage
  void load('page')
}

function updateOnline() {
  online.value = navigator.onLine
  if (!online.value) activeRequest?.abort()
  else if (!result.value) void load()
}

onMounted(() => {
  window.addEventListener('online', updateOnline)
  window.addEventListener('offline', updateOnline)
  void load()
})
onBeforeUnmount(() => {
  activeRequest?.abort()
  window.removeEventListener('online', updateOnline)
  window.removeEventListener('offline', updateOnline)
})
</script>

<template>
  <article class="page users-page">
    <header class="page-heading">
      <div><div class="eyebrow">用户运营 · 安全脱敏</div><h1>用户列表</h1><p>查询账号状态和基础资料；手机号始终使用服务端脱敏结果。</p></div>
      <div class="page-actions"><button class="ghost-button" :disabled="refreshing || !online" @click="load('refresh')">{{ refreshing ? '刷新中…' : '刷新' }}</button></div>
    </header>

    <StatusNotice v-if="!online && result" tone="warning" title="当前设备离线" detail="正在显示最近一次成功读取的结果；恢复网络后可刷新。" />
    <StatusNotice v-if="forbidden" tone="danger" title="无用户读取权限" detail="当前会话缺少 user.read，列表内容不会继续展示。" :request-id="error?.requestId" />

    <form class="card user-filters" aria-label="用户筛选" @submit.prevent="submitFilters">
      <label class="field"><span class="field-label">关键词</span><input v-model="filters.keyword" class="input" maxlength="100" placeholder="用户编号、手机号或昵称" /></label>
      <label class="field"><span class="field-label">账号状态</span><select v-model="filters.status" class="input"><option value="">全部状态</option><option value="ACTIVE">正常</option><option value="RESTRICTED">受限</option><option value="FROZEN">已冻结</option><option value="CANCEL_PENDING">注销处理中</option><option value="CANCELLED">已注销</option></select></label>
      <label class="field"><span class="field-label">排序</span><select v-model="filters.sort" class="input"><option value="createdAt:desc">最近创建</option><option value="createdAt:asc">最早创建</option><option value="status:asc">状态升序</option><option value="id:desc">编号降序</option></select></label>
      <button class="primary-button" :disabled="refreshing || !online">应用筛选</button>
    </form>

    <section v-if="loading" class="card skeleton-stack" aria-label="正在加载用户列表"><div v-for="index in 6" :key="index" class="skeleton user-row-skeleton" /></section>
    <section v-else-if="error && !result" class="card error-state" aria-live="polite"><div class="avatar centered-mark">!</div><h2>{{ forbidden ? '无法访问用户列表' : '列表暂时无法加载' }}</h2><p>{{ error.message }}</p><div><button v-if="!forbidden" class="primary-button" :disabled="!online" @click="load()">重新加载</button></div></section>
    <section v-else-if="isEmpty" class="card empty-state"><div class="avatar centered-mark">0</div><h2>当前筛选没有用户</h2><p>请调整关键词或状态条件后重新查询。</p><button class="ghost-button" @click="filters.keyword=''; filters.status=''; submitFilters()">清除筛选</button></section>
    <section v-else-if="result && !forbidden" class="card user-table-card" :aria-busy="refreshing">
      <div class="section-heading"><div><h2>查询结果</h2><p>共 {{ total }} 条，当前第 {{ page }} 页</p></div><span class="tag tag-success">已脱敏</span></div>
      <div class="user-table-wrap"><table class="user-table"><thead><tr><th>用户</th><th>账号状态</th><th>实名</th><th>会员</th><th>创建时间</th><th>版本</th><th><span class="sr-only">操作</span></th></tr></thead><tbody><tr v-for="user in result.items" :key="user.id"><td><div class="user-cell"><span class="avatar">{{ (user.nickname || user.id).slice(0,1) }}</span><span><strong>{{ user.nickname || `用户 ${user.id}` }}</strong><small>{{ user.phoneMasked || '未绑定手机号' }} · 编号 {{ user.id }}</small><em v-if="user.bio">{{ user.bio }}</em></span></div></td><td><span class="status-chip" :data-status="user.status">{{ statusLabel(user.status) }}</span></td><td>{{ statusLabel(user.identityStatus) }}</td><td>{{ statusLabel(user.membershipStatus) }}</td><td>{{ formatDate(user.createdAt) }}</td><td>v{{ user.version }}</td><td><RouterLink class="secondary-button table-link" :to="`/users/${user.id}`">查看详情</RouterLink></td></tr></tbody></table></div>
      <footer class="pagination"><button class="ghost-button" :disabled="page <= 1 || refreshing" @click="changePage(page-1)">上一页</button><span>第 {{ page }} 页</span><button class="ghost-button" :disabled="!hasMore || refreshing" @click="changePage(page+1)">下一页</button></footer>
    </section>
  </article>
</template>
