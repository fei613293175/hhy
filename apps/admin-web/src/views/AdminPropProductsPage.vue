<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusNotice from '../components/StatusNotice.vue'
import { adminPropsApi, adminSession, isApiRequestError, type AdminPropResource } from '../services'

const route = useRoute(); const router = useRouter()
const items = ref<AdminPropResource[]>([]); const loading = ref(true); const error = ref(''); const conflict = ref('')
const canWrite = computed(() => adminSession.hasPermission('prop.write'))
const editor = ref<AdminPropResource>(); const edit = reactive({ status: 'ACTIVE', reason: '' })
const filters = reactive({ page: number(route.query.page) || 1, pageSize: number(route.query.pageSize) || 20, keyword: String(route.query.keyword ?? ''), status: String(route.query.status ?? ''), sort: String(route.query.sort ?? 'createdAt:desc') })
const activeCount = computed(() => items.value.filter((item) => item.status === 'ACTIVE').length)
function number(value: unknown) { const parsed = Number(value); return Number.isFinite(parsed) ? parsed : 0 }
function config(item: AdminPropResource) { return (item.configuration ?? {}) as Record<string, unknown> }
function money(value: unknown) { return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(number(value) / 100) }
function duration(value: unknown) { const seconds = number(value); return seconds ? `${Math.round(seconds / 3600)} 小时` : '按使用规则' }
function typeLabel(value: string) { return ({ REFRESH: '内容刷新', HEADLINE: '头条曝光', COLOR_THEME: '主题色' } as Record<string, string>)[value] ?? '其他道具' }
function statusLabel(value: string) { return value === 'ACTIVE' ? '可用' : value === 'INACTIVE' ? '停用' : '待确认' }
function message(caught: unknown) { if (isApiRequestError(caught)) { if (caught.status === 401) adminSession.clear(); return caught.message } return '道具商品暂时无法加载，请稍后重试' }
async function syncQuery() { await router.replace({ query: { page: String(filters.page), pageSize: String(filters.pageSize), keyword: filters.keyword || undefined, status: filters.status || undefined, sort: filters.sort } }) }
async function load() { loading.value = true; error.value = ''; try { await syncQuery(); const result = await adminPropsApi.props(filters); items.value = result.items } catch (caught) { error.value = message(caught) } finally { loading.value = false } }
function reset() { Object.assign(filters, { page: 1, pageSize: 20, keyword: '', status: '', sort: 'createdAt:desc' }); void load() }
function openEditor(item: AdminPropResource) { editor.value = item; edit.status = item.status; edit.reason = ''; conflict.value = '' }
async function save() { if (!editor.value || !edit.reason.trim()) { error.value = '请填写修改原因'; return } error.value = ''; conflict.value = ''; try { const updated = await adminPropsApi.patch(editor.value.id, { reason: edit.reason.trim(), expectedVersion: editor.value.version, payload: { status: edit.status } }, `r19-prop-patch-${Date.now().toString(36)}`); if ('id' in updated) items.value = items.value.map((item) => item.id === updated.id ? updated : item); editor.value = undefined } catch (caught) { if (isApiRequestError(caught) && caught.status === 409) { conflict.value = '服务端版本已变化，已重载最新行；请核对差异后再次提交。'; await load() } else error.value = message(caught) } }
onMounted(() => { void load() })
</script>

<template><article class="page prop-admin-page">
  <header class="page-heading"><div><div class="eyebrow">道具运营</div><h1>道具商品</h1><p>按真实 SKU 状态、售价与使用范围管理道具。</p></div><button class="ghost-button" :disabled="loading" @click="load">刷新数据</button></header>
  <StatusNotice v-if="error" tone="danger" title="操作未完成" :detail="error" />
  <section class="order-metrics"><div><span>当前结果</span><strong>{{ items.length }}</strong><small>按当前筛选</small></div><div><span>可用商品</span><strong>{{ activeCount }}</strong><small>服务端有效状态</small></div><div><span>道具类型</span><strong>{{ new Set(items.map((item) => item.propType)).size }}</strong><small>当前页去重</small></div><div><span>写入能力</span><strong>{{ canWrite ? '可修改' : '只读' }}</strong><small>按当前权限</small></div></section>
  <form class="card order-filters" @submit.prevent="filters.page = 1; load()"><label class="field"><span class="field-label">关键词</span><input v-model="filters.keyword" class="input" maxlength="100" placeholder="名称或编号" /></label><label class="field"><span class="field-label">状态</span><select v-model="filters.status" class="input"><option value="">全部状态</option><option value="ACTIVE">可用</option><option value="INACTIVE">停用</option></select></label><label class="field"><span class="field-label">排序</span><select v-model="filters.sort" class="input"><option value="createdAt:desc">最近创建</option><option value="updatedAt:desc">最近更新</option><option value="name:asc">名称顺序</option></select></label><button class="primary-button" :disabled="loading">查询</button><button class="ghost-button" type="button" @click="reset">重置</button></form>
  <section class="card table-card"><div v-if="loading" class="loading-state">正在加载道具商品…</div><div v-else-if="!items.length" class="empty-state">暂无符合条件的道具商品</div><table v-else class="data-table"><thead><tr><th>商品</th><th>类型</th><th>售价</th><th>使用时长</th><th>状态</th><th>范围</th><th>版本</th><th v-if="canWrite">操作</th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td><strong>{{ item.name || '未命名道具' }}</strong><small>#{{ item.id }}</small></td><td>{{ typeLabel(item.propType) }}</td><td>{{ money(config(item).priceCent) }}</td><td>{{ duration(config(item).durationSeconds) }}</td><td><span class="status-chip" :data-status="item.status">{{ statusLabel(item.status) }}</span></td><td>{{ Object.keys((config(item).scope as object) || {}).length }} 项约束</td><td>v{{ item.version }}</td><td v-if="canWrite"><button class="secondary-button" @click="openEditor(item)">修改道具</button></td></tr></tbody></table></section>
  <div v-if="editor" class="dialog-backdrop" @click.self="editor = undefined"><section class="dialog-card"><header><h2>修改道具</h2><button class="icon-button" aria-label="关闭" @click="editor = undefined">×</button></header><p><strong>{{ editor.name }}</strong> · 当前版本 v{{ editor.version }}</p><StatusNotice v-if="conflict" tone="warning" title="版本冲突" :detail="conflict" /><label class="field"><span class="field-label">状态</span><select v-model="edit.status" class="input"><option value="ACTIVE">可用</option><option value="INACTIVE">停用</option></select></label><label class="field"><span class="field-label">修改原因</span><textarea v-model="edit.reason" class="input" maxlength="500"></textarea></label><footer><button class="ghost-button" @click="editor = undefined">取消</button><button class="primary-button" @click="save">确认修改</button></footer></section></div>
</article></template>
