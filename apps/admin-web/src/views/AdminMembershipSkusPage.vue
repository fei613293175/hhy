<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import StatusNotice from '../components/StatusNotice.vue'
import { adminMembershipApi, adminSession, isApiRequestError } from '../services'

type MembershipRow = {
  id?: string
  skuId?: string
  name?: string
  status: string
  paidValueCent?: number
  benefits?: unknown[]
  version: number
}
const items = ref<MembershipRow[]>([])
const loading = ref(true)
const refreshing = ref(false)
const error = ref('')
const forbidden = ref(false)
const editor = ref<MembershipRow>()
const filters = reactive({ keyword: '', status: '', sort: 'priceCent:asc' })
const activeCount = computed(() => items.value.filter((item) => item.status === 'ACTIVE').length)
const prices = computed(() => items.value.map((item) => item.paidValueCent ?? 0).filter(Boolean))

function money(value?: number) {
  return value === undefined ? '暂无' : new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(value / 100)
}
function statusLabel(value: string) {
  return value === 'ACTIVE' ? '有效' : value === 'DISABLED' ? '停用' : '状态待确认'
}
function message(caught: unknown) {
  if (isApiRequestError(caught)) {
    if (caught.status === 401) adminSession.clear()
    if (caught.status === 403) forbidden.value = true
    return caught.message
  }
  return '会员 SKU 暂时无法加载，请稍后重试'
}
async function load() {
  loading.value = true; error.value = ''; forbidden.value = false
  try {
    const result = await adminMembershipApi.skus({ keyword: filters.keyword.trim(), status: filters.status, sort: filters.sort })
    items.value = result.items as MembershipRow[]
  } catch (caught) { error.value = message(caught) } finally { loading.value = false; refreshing.value = false }
}
function refresh() { refreshing.value = true; void load() }
function openEditor(item: MembershipRow) { editor.value = item }
async function save() {
  if (!editor.value) return
  try {
    await adminMembershipApi.patchSku(editor.value.id || '', {
      name: editor.value.name,
      priceCent: editor.value.paidValueCent ?? undefined,
      expectedVersion: editor.value.version,
    }, 'r18-admin-sku-' + Date.now().toString(36).padEnd(16, '0'))
    editor.value = undefined; await load()
  } catch (caught) { error.value = message(caught) }
}
onMounted(() => { void load() })
</script>

<template>
  <article class="page membership-page">
    <header class="page-heading">
      <div><div class="eyebrow">会员运营</div><h1>会员 SKU</h1><p>按 B11/P01-P03 的信息层级管理 Pro 期限套餐与真实权益。</p></div>
      <button class="ghost-button" :disabled="refreshing || loading" @click="refresh">{{ refreshing ? '刷新中…' : '刷新数据' }}</button>
    </header>
    <StatusNotice v-if="forbidden" tone="danger" title="没有会员读取权限" detail="需要 membership.read 权限。" />
    <StatusNotice v-if="error" tone="danger" title="加载失败" :detail="error" />
    <section class="order-metrics" aria-label="会员统计">
      <div><span>当前结果</span><strong>{{ items.length }}</strong><small>按当前筛选</small></div>
      <div><span>可售套餐</span><strong>{{ activeCount }}</strong><small>状态为有效</small></div>
      <div><span>最低价格</span><strong>{{ money(prices.length ? Math.min(...prices) : undefined) }}</strong><small>服务端价格</small></div>
      <div><span>期限体系</span><strong>3</strong><small>月 / 季 / 年</small></div>
    </section>
    <form class="card order-filters" @submit.prevent="load">
      <label class="field"><span class="field-label">关键词</span><input v-model="filters.keyword" class="input" maxlength="100" placeholder="套餐名称或编号" /></label>
      <label class="field"><span class="field-label">状态</span><select v-model="filters.status" class="input"><option value="">全部状态</option><option value="ACTIVE">有效</option><option value="DISABLED">停用</option></select></label>
      <label class="field"><span class="field-label">排序</span><select v-model="filters.sort" class="input"><option value="priceCent:asc">价格升序</option><option value="priceCent:desc">价格降序</option><option value="updatedAt:desc">最近更新</option></select></label>
      <button class="primary-button" :disabled="loading">查询</button>
    </form>
    <section class="card table-card">
      <div v-if="loading" class="loading-state">正在加载会员 SKU…</div>
      <div v-else-if="!items.length" class="empty-state">暂无符合条件的会员 SKU</div>
      <table v-else class="data-table"><thead><tr><th>套餐</th><th>Pro 身份</th><th>价格</th><th>权益</th><th>状态</th><th>版本</th><th>操作</th></tr></thead>
        <tbody><tr v-for="item in items" :key="item.id">
          <td><strong>{{ item.name || '未命名套餐' }}</strong><small>{{ item.skuId || item.id }}</small></td>
          <td>Pro</td><td>{{ money(item.paidValueCent) }}</td><td>{{ (item.benefits ?? []).length }} 项</td>
          <td><span class="status-chip" :data-status="item.status">{{ statusLabel(item.status) }}</span></td><td>v{{ item.version }}</td>
          <td><button class="secondary-button" @click="openEditor(item)">编辑</button></td>
        </tr></tbody>
      </table>
    </section>
    <div v-if="editor" class="dialog-backdrop" @click.self="editor = undefined">
      <section class="dialog-card"><header><h2>编辑套餐</h2><button class="icon-button" aria-label="关闭" @click="editor = undefined">×</button></header>
        <label class="field"><span class="field-label">名称</span><input v-model="editor.name" class="input" maxlength="255" /></label>
        <p class="dialog-note">价格与权益必须通过服务端合同校验，历史快照不会被覆盖。</p>
        <footer><button class="ghost-button" @click="editor = undefined">取消</button><button class="primary-button" @click="save">保存修改</button></footer>
      </section>
    </div>
  </article>
</template>
