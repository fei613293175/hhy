<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusNotice from '../components/StatusNotice.vue'
import { adminPropsApi, adminSession, isApiRequestError, type AdminPropResource } from '../services'
const route = useRoute(); const router = useRouter(); const items = ref<AdminPropResource[]>([]); const loading = ref(true); const error = ref('')
const filters = reactive({ page: Number(route.query.page) || 1, pageSize: Number(route.query.pageSize) || 20, keyword: String(route.query.keyword ?? ''), status: String(route.query.status ?? ''), sort: String(route.query.sort ?? 'createdAt:desc') })
const successCount = computed(() => items.value.filter((item) => item.status === 'SUCCEEDED' || item.status === 'SUCCESS').length)
function config(item: AdminPropResource) { return (item.configuration ?? {}) as Record<string, unknown> }
function statusLabel(value: string) { return value === 'SUCCEEDED' || value === 'SUCCESS' ? '执行成功' : value === 'FAILED' ? '执行失败' : value === 'PENDING' || value === 'RUNNING' ? '执行中' : '结果待确认' }
function typeLabel(name?: string) { const value = String(name ?? ''); return value.includes('REFRESH') ? '内容刷新' : value.includes('HEADLINE') ? '头条曝光' : value.includes('COLOR') ? '主题色' : '道具执行' }
function dateLabel(value?: string) { if (!value) return '时间待确认'; const date = new Date(value); return Number.isNaN(date.getTime()) ? '时间待确认' : new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(date) }
function errorSummary(item: AdminPropResource) { const value = config(item).error; return typeof value === 'string' && value ? value.slice(0, 120) : '无错误' }
function message(caught: unknown) { if (isApiRequestError(caught)) { if (caught.status === 401) adminSession.clear(); return caught.message } return '执行日志暂时无法加载，请稍后重试' }
async function syncQuery() { await router.replace({ query: { page: String(filters.page), pageSize: String(filters.pageSize), keyword: filters.keyword || undefined, status: filters.status || undefined, sort: filters.sort } }) }
async function load() { loading.value = true; error.value = ''; try { await syncQuery(); items.value = (await adminPropsApi.executions(filters)).items } catch (caught) { error.value = message(caught) } finally { loading.value = false } }
function reset() { Object.assign(filters, { page: 1, pageSize: 20, keyword: '', status: '', sort: 'createdAt:desc' }); void load() }
onMounted(() => { void load() })
</script>
<template><article class="page prop-admin-page"><header class="page-heading"><div><div class="eyebrow">道具审计</div><h1>执行日志</h1><p>只读查看道具执行结果与关联业务标识。</p></div><button class="ghost-button" :disabled="loading" @click="load">刷新数据</button></header><StatusNotice v-if="error" tone="danger" title="加载失败" :detail="error" />
<section class="order-metrics"><div><span>执行记录</span><strong>{{ items.length }}</strong><small>当前筛选</small></div><div><span>执行成功</span><strong>{{ successCount }}</strong><small>服务端结果</small></div><div><span>执行失败</span><strong>{{ items.filter((item) => item.status === 'FAILED').length }}</strong><small>需要人工定位</small></div><div><span>页面能力</span><strong>只读</strong><small>无重试或删除</small></div></section>
<form class="card order-filters" @submit.prevent="filters.page = 1; load()"><label class="field"><span class="field-label">关键词</span><input v-model="filters.keyword" class="input" maxlength="100" placeholder="业务标识或道具名称" /></label><label class="field"><span class="field-label">结果</span><select v-model="filters.status" class="input"><option value="">全部结果</option><option value="SUCCEEDED">执行成功</option><option value="FAILED">执行失败</option><option value="PENDING">执行中</option></select></label><label class="field"><span class="field-label">排序</span><select v-model="filters.sort" class="input"><option value="createdAt:desc">最近执行</option><option value="createdAt:asc">最早执行</option></select></label><button class="primary-button">查询</button><button class="ghost-button" type="button" @click="reset">重置</button></form>
<section class="card table-card"><div v-if="loading" class="loading-state">正在加载执行日志…</div><div v-else-if="!items.length" class="empty-state">暂无符合条件的执行日志</div><table v-else class="data-table"><thead><tr><th>业务标识</th><th>道具类型</th><th>目标摘要</th><th>执行时间</th><th>结果</th><th>错误摘要</th><th>版本</th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td><strong>#{{ item.id }}</strong></td><td>{{ typeLabel(item.name) }}</td><td>{{ item.name || '关联内容待确认' }}</td><td>{{ dateLabel(item.expiresAt) }}</td><td><span class="status-chip" :data-status="item.status">{{ statusLabel(item.status) }}</span></td><td>{{ errorSummary(item) }}</td><td>v{{ item.version }}</td></tr></tbody></table></section>
</article></template>
