<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusNotice from '../components/StatusNotice.vue'
import { adminR15Api, adminSession, isApiRequestError, type AdminSupportTicketPage } from '../services'

const route = useRoute(); const router = useRouter()
const result = ref<AdminSupportTicketPage>(); const loading = ref(true); const refreshing = ref(false)
const forbidden = ref(false); const offline = ref(!navigator.onLine); const error = ref<string>(); let request: AbortController | undefined
const filters = reactive({ page: positive(route.query.page), status: value(route.query.status), keyword: value(route.query.keyword), sort: value(route.query.sort) || 'updatedAt:desc' })
const tickets = computed(() => result.value?.items ?? []); const total = computed(() => Number(result.value?.page.total ?? tickets.value.length))
const openCount = computed(() => tickets.value.filter((item) => !['CLOSED', 'RESOLVED'].includes(item.status)).length)
function value(input: unknown) { return typeof input === 'string' ? input.slice(0, 100) : '' }
function positive(input: unknown) { const number = Number(input); return Number.isInteger(number) && number > 0 ? number : 1 }
function statusLabel(input: string) { return ({ OPEN: '待受理', ASSIGNED: '已分配', IN_PROGRESS: '处理中', WAITING_USER: '等待用户', RESOLVED: '已解决', CLOSED: '已关闭' } as Record<string, string>)[input] ?? '待确认' }
function format(input?: string) { return input ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(input)) : '暂无记录' }
function message(caught: unknown) {
  if (!isApiRequestError(caught)) return '工单列表暂时无法加载，请稍后重试'
  if (caught.status === 0) return '网络连接不可用，请恢复网络后重试'
  if (caught.status === 403) return '当前账号没有查看工单的权限'
  if (caught.status === 429) return caught.retryAfter ? `请求过于频繁，请在 ${caught.retryAfter} 秒后重试` : '请求过于频繁，请稍后重试'
  return caught.requestId !== 'missing' ? `工单列表加载失败（请求 ${caught.requestId}）` : '工单列表暂时无法加载，请稍后重试'
}
async function sync() { await router.replace({ query: { page: filters.page === 1 ? undefined : String(filters.page), status: filters.status || undefined, keyword: filters.keyword.trim() || undefined, sort: filters.sort === 'updatedAt:desc' ? undefined : filters.sort } }) }
async function load(mode: 'initial' | 'refresh' = 'initial') {
  request?.abort(); if (!navigator.onLine) { offline.value = true; loading.value = false; error.value = result.value ? undefined : '当前设备离线，无法读取工单'; return }
  const current = new AbortController(); request = current; offline.value = false; forbidden.value = false; error.value = undefined
  mode === 'initial' ? loading.value = true : refreshing.value = true
  try { result.value = await adminR15Api.supportTickets({ page: filters.page, pageSize: 20, status: filters.status || undefined, keyword: filters.keyword.trim() || undefined, sort: filters.sort }, { signal: current.signal }) }
  catch (caught) {
    if (isApiRequestError(caught) && caught.code === 'REQUEST_ABORTED') return
    if (isApiRequestError(caught) && caught.status === 401) { adminSession.clear(); await router.replace({ path: '/auth/login', query: { redirect: route.fullPath } }); return }
    forbidden.value = isApiRequestError(caught) && caught.status === 403; error.value = message(caught)
  } finally { if (request === current) request = undefined; loading.value = false; refreshing.value = false }
}
async function apply() { filters.page = 1; await sync(); await load() }
async function open(id: string) { await router.push({ path: `/support/tickets/${encodeURIComponent(id)}`, query: { from: route.fullPath } }) }
function online() { offline.value = false; void load('refresh') }
function offlineNow() { offline.value = true }
onMounted(() => { window.addEventListener('online', online); window.addEventListener('offline', offlineNow); void load() })
onBeforeUnmount(() => { request?.abort(); window.removeEventListener('online', online); window.removeEventListener('offline', offlineNow) })
</script>

<template><div class="r15-page"><header class="r15-heading"><div><p class="eyebrow">R15 客户服务</p><h1>工单列表</h1><p>集中查看分配状态、处理进度与最近回复。</p></div><button type="button" :disabled="refreshing" @click="load('refresh')">{{ refreshing ? '刷新中' : '刷新' }}</button></header>
<StatusNotice v-if="offline" tone="warning" title="当前离线" detail="已加载工单保持只读，恢复网络后自动刷新。" /><StatusNotice v-if="error && tickets.length" tone="warning" title="刷新失败，已保留现有工单" :detail="error" />
<section class="r15-metrics"><div><strong>{{ total }}</strong><span>符合筛选</span></div><div><strong>{{ openCount }}</strong><span>本页处理中</span></div><div><strong>{{ tickets.length - openCount }}</strong><span>本页已结束</span></div></section>
<form class="r15-filter" @submit.prevent="apply"><label>状态<select v-model="filters.status"><option value="">全部状态</option><option value="OPEN">待受理</option><option value="ASSIGNED">已分配</option><option value="IN_PROGRESS">处理中</option><option value="WAITING_USER">等待用户</option><option value="CLOSED">已关闭</option></select></label><label>关键词<input v-model="filters.keyword" maxlength="100" placeholder="工单号、主题或分类" /></label><label>排序<select v-model="filters.sort"><option value="updatedAt:desc">最近更新</option><option value="createdAt:desc">最新创建</option><option value="createdAt:asc">最早创建</option></select></label><button type="submit">查询</button></form>
<StatusNotice v-if="forbidden" tone="danger" title="无访问权限" detail="未展示任何工单内容，请联系管理员授予 support.read 权限。" /><div v-else-if="loading" class="r15-skeleton"><span v-for="n in 5" :key="n" /></div><StatusNotice v-else-if="error && !tickets.length" tone="danger" title="工单加载失败" :detail="error" />
<section v-else-if="!tickets.length" class="r15-empty"><h2>暂无符合条件的工单</h2><p>清除筛选或稍后刷新查看新工单。</p><button type="button" @click="filters.status = ''; filters.keyword = ''; apply()">清除筛选</button></section>
<section v-else class="r15-table-panel"><div class="table-wrap"><table><thead><tr><th>工单</th><th>分类与主题</th><th>状态</th><th>处理人</th><th>最近消息</th><th></th></tr></thead><tbody><tr v-for="ticket in tickets" :key="ticket.id"><td><strong>{{ ticket.ticketNo }}</strong><small>v{{ ticket.version }}</small></td><td><strong>{{ ticket.subject || '未填写主题' }}</strong><small>{{ ticket.category || '未分类' }}</small></td><td><span class="status-chip">{{ statusLabel(ticket.status) }}</span></td><td>{{ ticket.assignee || '待分配' }}</td><td>{{ format(ticket.lastMessageAt || ticket.createdAt) }}</td><td><button type="button" class="table-link" @click="open(ticket.id)">查看详情</button></td></tr></tbody></table></div><footer class="r15-pagination"><span>第 {{ filters.page }} 页，共 {{ total }} 条</span><div><button type="button" :disabled="filters.page <= 1" @click="filters.page--; sync().then(() => load())">上一页</button><button type="button" :disabled="String(result?.page.hasMore) !== 'true'" @click="filters.page++; sync().then(() => load())">下一页</button></div></footer></section></div></template>
<style scoped src="../r15-operations.css"></style>
