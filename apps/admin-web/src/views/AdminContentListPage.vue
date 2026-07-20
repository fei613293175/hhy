<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import StatusNotice from '../components/StatusNotice.vue'
import { adminContentsApi, adminSession, isApiRequestError, type AdminContentPage } from '../services'

const router = useRouter()
const result = ref<AdminContentPage>()
const loading = ref(true)
const refreshing = ref(false)
const offline = ref(!navigator.onLine)
const error = ref<string>()
const forbidden = ref(false)
const page = ref(1)
const filters = reactive({ keyword: '', status: '', contentType: '', sort: 'createdAt:desc' })
let request: AbortController | undefined

const items = computed(() => result.value?.items ?? [])
const isEmpty = computed(() => !loading.value && !error.value && items.value.length === 0)

function message(caught: unknown) {
  if (isApiRequestError(caught)) {
    if (caught.status === 401) { adminSession.clear(); void router.replace({ path: '/auth/login', query: { redirect: '/contents' } }); return }
    if (caught.status === 403) forbidden.value = true
    if (caught.status === 404) return '内容资源不存在或已下架'
    if (caught.status === 409) return '数据已变化，请重新加载后再操作'
    return caught.message
  }
  return '内容列表暂时无法加载，请稍后重试'
}

async function load(mode: 'initial' | 'refresh' = 'initial') {
  request?.abort(); request = new AbortController()
  if (!navigator.onLine) { offline.value = true; loading.value = false; error.value = '当前设备离线，请恢复网络后重试'; return }
  offline.value = false; error.value = undefined; forbidden.value = false
  if (mode === 'initial') loading.value = true; else refreshing.value = true
  try {
    result.value = await adminContentsApi.list({ page: page.value, pageSize: 20, keyword: filters.keyword.trim() || undefined, status: filters.status || undefined, sort: filters.sort, contentType: (filters.contentType || undefined) as never }, { signal: request.signal })
  } catch (caught) { error.value = message(caught) }
  finally { loading.value = false; refreshing.value = false }
}
function applyFilters() { page.value = 1; void load('refresh') }
function updateOnline() { offline.value = !navigator.onLine; if (navigator.onLine && !result.value) void load() }
onMounted(() => { window.addEventListener('online', updateOnline); window.addEventListener('offline', updateOnline); void load() })
onBeforeUnmount(() => { request?.abort(); window.removeEventListener('online', updateOnline); window.removeEventListener('offline', updateOnline) })
</script>

<template>
  <article class="page users-page">
    <header class="page-heading"><div><div class="eyebrow">内容运营</div><h1>统一内容列表</h1><p>统一查看项目、应用、群聊和团队长内容，并按权限执行运营动作。</p></div><div class="page-actions"><button class="ghost-button" :disabled="refreshing || offline" @click="load('refresh')">{{ refreshing ? '刷新中…' : '刷新' }}</button></div></header>
    <StatusNotice v-if="offline" tone="warning" title="当前设备离线" detail="恢复网络后可以重新加载内容。" />
    <StatusNotice v-if="forbidden" tone="danger" title="无内容读取权限" detail="当前会话无权读取内容列表。" />
    <form class="card user-filters" @submit.prevent="applyFilters"><label class="field"><span class="field-label">关键词</span><input v-model="filters.keyword" class="input" maxlength="100" placeholder="标题、内容编号" /></label><label class="field"><span class="field-label">内容类型</span><select v-model="filters.contentType" class="input"><option value="">全部类型</option><option value="PROJECT">项目</option><option value="APP">应用</option><option value="GROUP_CHAT">群聊</option><option value="TEAM_LEADER">团队长</option></select></label><label class="field"><span class="field-label">状态</span><input v-model="filters.status" class="input" maxlength="64" placeholder="状态" /></label><button class="primary-button" :disabled="refreshing || offline">应用筛选</button></form>
    <section v-if="loading" class="card skeleton-stack" aria-label="正在加载内容列表"><div v-for="n in 6" :key="n" class="skeleton user-row-skeleton" /></section>
    <section v-else-if="error && !result" class="card error-state"><h2>{{ forbidden ? '无法访问内容列表' : '列表暂时无法加载' }}</h2><p>{{ error }}</p><button v-if="!forbidden" class="primary-button" :disabled="offline" @click="load()">重新加载</button></section>
    <section v-else-if="isEmpty" class="card empty-state"><h2>当前筛选没有内容</h2><p>请调整关键词、类型或状态后重新查询。</p><button class="ghost-button" @click="filters.keyword=''; filters.status=''; filters.contentType=''; applyFilters()">清除筛选</button></section>
    <section v-else class="card user-table-card" :aria-busy="refreshing"><div class="section-heading"><div><h2>查询结果</h2><p>当前第 {{ page }} 页</p></div></div><div class="user-table-wrap"><table class="user-table"><thead><tr><th>标题</th><th>类型</th><th>状态</th><th>版本</th><th>更新时间</th><th><span class="sr-only">操作</span></th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td><strong>{{ item.title }}</strong><small>{{ item.id }}</small></td><td>{{ item.contentType }}</td><td><span class="status-chip">{{ item.status }}</span></td><td>v{{ item.version }}</td><td>{{ item.updatedAt || item.createdAt || '暂无' }}</td><td><RouterLink class="secondary-button table-link" :to="`/contents/${item.id}`">查看详情</RouterLink></td></tr></tbody></table></div></section>
  </article>
</template>
