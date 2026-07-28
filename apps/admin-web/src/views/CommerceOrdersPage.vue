<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusNotice from '../components/StatusNotice.vue'
import {
  adminOrdersApi,
  adminSession,
  isApiRequestError,
  type AdminOrderPage,
  type AdminOrderResource,
} from '../services'

const route = useRoute()
const router = useRouter()
const result = ref<AdminOrderPage>()
const selected = ref<AdminOrderResource>()
const loading = ref(true)
const refreshing = ref(false)
const detailLoading = ref(false)
const offline = ref(!navigator.onLine)
const error = ref<string>()
const detailError = ref<string>()
const forbidden = ref(false)
const copied = ref<string>()
const refreshedAt = ref<Date>()
const page = ref(Math.max(1, Number(route.query.page) || 1))
const filters = reactive({
  keyword: typeof route.query.keyword === 'string' ? route.query.keyword : '',
  orderNo: typeof route.query.orderNo === 'string' ? route.query.orderNo : '',
  status: typeof route.query.status === 'string' ? route.query.status : '',
  sort: typeof route.query.sort === 'string' ? route.query.sort : 'createdAt:desc',
})
let listRequest: AbortController | undefined
let detailRequest: AbortController | undefined

const statusLabels: Record<string, string> = {
  PENDING_PAYMENT: '待支付',
  PAYMENT_PROCESSING: '支付处理中',
  PAID: '已支付',
  FULFILLING: '履约中',
  COMPLETED: '已完成',
  PAYMENT_FAILED: '支付失败',
  CLOSED: '已关闭',
  CHANNEL_REVERSAL: '渠道冲正',
}
const statusOptions = Object.entries(statusLabels)
const items = computed(() => result.value?.items ?? [])
const isEmpty = computed(() => !loading.value && !error.value && items.value.length === 0)
const total = computed(() => {
  const value = Number(result.value?.page.total)
  return Number.isFinite(value) ? value : items.value.length
})
const hasMore = computed(() => result.value?.page.hasMore === 'true')
const currentPagePaid = computed(() => items.value.filter((item) => ['PAID', 'FULFILLING', 'COMPLETED'].includes(item.status)).length)
const currentPagePending = computed(() => items.value.filter((item) => ['PENDING_PAYMENT', 'PAYMENT_PROCESSING'].includes(item.status)).length)
const currentPageExceptions = computed(() => items.value.filter((item) => ['PAYMENT_FAILED', 'CHANNEL_REVERSAL'].includes(item.status)).length)

function statusLabel(value: string) {
  return statusLabels[value] ?? '状态确认中'
}

function formatDate(value?: string) {
  if (!value) return '暂无'
  const date = new Date(value)
  return Number.isNaN(date.valueOf())
    ? '时间待确认'
    : new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

function formatMoney(cent?: number, currency = 'CNY') {
  if (cent === undefined) return '暂无'
  try {
    return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format(cent / 100)
  } catch {
    return `${(cent / 100).toFixed(2)} ${currency}`
  }
}

function failureMessage(caught: unknown, context: 'list' | 'detail') {
  if (isApiRequestError(caught)) {
    if (caught.status === 401) {
      adminSession.clear()
      void router.replace({ path: '/auth/login', query: { redirect: route.fullPath } })
      return '登录状态已失效'
    }
    if (caught.status === 403) {
      forbidden.value = true
      return '当前账号没有订单读取权限'
    }
    if (caught.status === 404 && context === 'detail') return '该订单不存在或已不可见'
    return caught.message
  }
  return context === 'detail' ? '订单详情暂时无法加载' : '订单列表暂时无法加载，请稍后重试'
}

async function syncUrl() {
  const query: Record<string, string> = { page: String(page.value), sort: filters.sort }
  if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
  if (filters.orderNo.trim()) query.orderNo = filters.orderNo.trim()
  if (filters.status) query.status = filters.status
  await router.replace({ query })
}

async function load(mode: 'initial' | 'refresh' = 'initial') {
  listRequest?.abort()
  listRequest = new AbortController()
  if (!navigator.onLine) {
    offline.value = true
    loading.value = false
    error.value = '当前设备离线，请恢复网络后重试'
    return
  }
  offline.value = false
  error.value = undefined
  forbidden.value = false
  if (mode === 'initial') loading.value = true
  else refreshing.value = true
  try {
    await syncUrl()
    result.value = await adminOrdersApi.list({
      page: page.value,
      pageSize: 20,
      keyword: filters.orderNo.trim() || filters.keyword.trim() || undefined,
      status: filters.status || undefined,
      sort: filters.sort,
    }, { signal: listRequest.signal })
    refreshedAt.value = new Date()
  } catch (caught) {
    if (!(caught instanceof DOMException && caught.name === 'AbortError')) error.value = failureMessage(caught, 'list')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function applyFilters() {
  page.value = 1
  closeDetail()
  void load('refresh')
}

function resetFilters() {
  filters.keyword = ''
  filters.orderNo = ''
  filters.status = ''
  filters.sort = 'createdAt:desc'
  applyFilters()
}

function changePage(next: number) {
  if (next < 1 || (next > page.value && !hasMore.value)) return
  page.value = next
  closeDetail()
  void load('refresh')
}

async function openDetail(orderNo: string) {
  detailRequest?.abort()
  detailRequest = new AbortController()
  selected.value = undefined
  detailError.value = undefined
  detailLoading.value = true
  try {
    selected.value = await adminOrdersApi.detail(orderNo, { signal: detailRequest.signal })
  } catch (caught) {
    if (!(caught instanceof DOMException && caught.name === 'AbortError')) detailError.value = failureMessage(caught, 'detail')
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  detailRequest?.abort()
  selected.value = undefined
  detailError.value = undefined
  detailLoading.value = false
}

async function copyValue(value: string) {
  try {
    await navigator.clipboard.writeText(value)
    copied.value = value
    globalThis.setTimeout(() => {
      if (copied.value === value) copied.value = undefined
    }, 1600)
  } catch {
    copied.value = undefined
  }
}

function updateOnline() {
  offline.value = !navigator.onLine
  if (navigator.onLine && !result.value) void load()
}

onMounted(() => {
  window.addEventListener('online', updateOnline)
  window.addEventListener('offline', updateOnline)
  void load()
})
onBeforeUnmount(() => {
  listRequest?.abort()
  detailRequest?.abort()
  window.removeEventListener('online', updateOnline)
  window.removeEventListener('offline', updateOnline)
})
</script>

<template>
  <article class="page order-page">
    <header class="page-heading order-heading">
      <div>
        <div class="eyebrow">交易管理</div>
        <h1>订单管理</h1>
        <p>只读查看全平台订单、商品快照与不可退款确认凭证，所有数据均来自订单权威接口。</p>
      </div>
      <div class="order-heading-actions">
        <span>最后刷新：{{ refreshedAt ? formatDate(refreshedAt.toISOString()) : '尚未刷新' }}</span>
        <button class="ghost-button" :disabled="refreshing || offline" @click="load('refresh')">
          {{ refreshing ? '刷新中…' : '刷新数据' }}
        </button>
      </div>
    </header>

    <StatusNotice v-if="offline" tone="warning" title="当前设备离线" detail="列表保留最近一次成功结果，恢复网络后可重新加载。" />
    <StatusNotice v-if="forbidden" tone="danger" title="无订单读取权限" detail="请联系超级管理员授予 order.read 权限。" />

    <section class="order-metrics" aria-label="订单统计">
      <div><span>查询结果</span><strong>{{ total }}</strong><small>符合当前筛选</small></div>
      <div><span>当前页已支付</span><strong>{{ currentPagePaid }}</strong><small>含履约中与已完成</small></div>
      <div><span>当前页待处理</span><strong>{{ currentPagePending }}</strong><small>待支付或支付处理中</small></div>
      <div><span>当前页异常</span><strong>{{ currentPageExceptions }}</strong><small>失败或渠道冲正</small></div>
    </section>

    <form class="card order-filters" aria-label="订单筛选" @submit.prevent="applyFilters">
      <label class="field"><span class="field-label">关键词</span><input v-model="filters.keyword" class="input" maxlength="100" placeholder="用户编号或商品名称" /></label>
      <label class="field"><span class="field-label">订单号</span><input v-model="filters.orderNo" class="input" maxlength="64" placeholder="精确查找订单号" /></label>
      <label class="field"><span class="field-label">订单状态</span><select v-model="filters.status" class="input"><option value="">全部状态</option><option v-for="[value, label] in statusOptions" :key="value" :value="value">{{ label }}</option></select></label>
      <label class="field"><span class="field-label">排序</span><select v-model="filters.sort" class="input"><option value="createdAt:desc">最新创建</option><option value="createdAt:asc">最早创建</option><option value="orderNo:desc">订单号降序</option><option value="orderNo:asc">订单号升序</option></select></label>
      <div class="order-filter-actions"><button type="button" class="ghost-button" @click="resetFilters">重置</button><button class="primary-button" :disabled="refreshing || offline">查询订单</button></div>
    </form>

    <section v-if="loading" class="card skeleton-stack" aria-label="正在加载订单列表"><div v-for="n in 7" :key="n" class="skeleton order-row-skeleton" /></section>
    <section v-else-if="error && !result" class="card error-state"><h2>{{ forbidden ? '无法访问订单列表' : '列表暂时无法加载' }}</h2><p>{{ error }}</p><button v-if="!forbidden" class="primary-button" :disabled="offline" @click="load()">重新加载</button></section>
    <section v-else-if="isEmpty" class="card empty-state"><h2>当前筛选没有订单</h2><p>请调整订单号、关键词或状态后重新查询。</p><button class="ghost-button" @click="resetFilters">清除筛选</button></section>
    <section v-else class="card order-table-card" :aria-busy="refreshing">
      <div v-if="error" class="order-inline-error"><span>{{ error }}</span><button @click="load('refresh')">重试</button></div>
      <div class="order-table-wrap">
        <table class="order-table">
          <thead><tr><th>订单号</th><th>用户</th><th>订单类型</th><th>状态</th><th>应付金额</th><th>实付金额</th><th>创建时间</th><th><span class="sr-only">操作</span></th></tr></thead>
          <tbody>
            <tr v-for="item in items" :key="item.orderNo">
              <td><button class="copyable-id" :title="copied === item.orderNo ? '已复制' : '复制订单号'" @click="copyValue(item.orderNo)"><strong>{{ item.orderNo }}</strong><small>{{ copied === item.orderNo ? '已复制' : '点击复制' }}</small></button></td>
              <td><button class="copyable-id" :title="copied === item.userId ? '已复制' : '复制用户编号'" @click="copyValue(item.userId)"><span>{{ item.userId }}</span><small>{{ copied === item.userId ? '已复制' : '用户编号' }}</small></button></td>
              <td>{{ item.orderType || '普通订单' }}</td>
              <td><span class="status-chip order-status" :data-status="item.status">{{ statusLabel(item.status) }}</span></td>
              <td><strong>{{ formatMoney(item.priceSnapshot.payableAmountCent, item.currency) }}</strong></td>
              <td>{{ formatMoney(item.paidAmountCent, item.currency) }}</td>
              <td>{{ formatDate(item.createdAt) }}</td>
              <td><button class="secondary-button table-link" @click="openDetail(item.orderNo)">查看详情</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="order-pagination"><span>第 {{ page }} 页 · 每页 {{ result?.page.pageSize ?? 20 }} 条</span><div><button class="ghost-button" :disabled="page <= 1 || refreshing" @click="changePage(page - 1)">上一页</button><button class="ghost-button" :disabled="!hasMore || refreshing" @click="changePage(page + 1)">下一页</button></div></footer>
    </section>

    <div v-if="detailLoading || selected || detailError" class="order-drawer-backdrop" @click.self="closeDetail">
      <aside class="order-drawer" aria-label="订单详情" role="dialog" aria-modal="true">
        <header><div><span>订单详情</span><h2>{{ selected?.orderNo ?? '正在读取…' }}</h2></div><button class="icon-button" aria-label="关闭订单详情" @click="closeDetail">×</button></header>
        <div v-if="detailLoading" class="skeleton-stack"><div v-for="n in 6" :key="n" class="skeleton order-detail-skeleton" /></div>
        <div v-else-if="detailError" class="error-state order-detail-error"><h3>详情暂时无法加载</h3><p>{{ detailError }}</p><button class="primary-button" @click="openDetail(filters.orderNo || items[0]?.orderNo || '')">重试</button></div>
        <template v-else-if="selected">
          <section class="order-detail-hero"><span class="status-chip order-status" :data-status="selected.status">{{ statusLabel(selected.status) }}</span><strong>{{ formatMoney(selected.priceSnapshot.payableAmountCent, selected.currency) }}</strong><small>应付金额</small></section>
          <dl class="order-detail-facts">
            <div><dt>用户编号</dt><dd>{{ selected.userId }}</dd></div><div><dt>订单类型</dt><dd>{{ selected.orderType || '普通订单' }}</dd></div>
            <div><dt>实付金额</dt><dd>{{ formatMoney(selected.paidAmountCent, selected.currency) }}</dd></div><div><dt>数据版本</dt><dd>第 {{ selected.version }} 版</dd></div>
            <div><dt>创建时间</dt><dd>{{ formatDate(selected.createdAt) }}</dd></div><div><dt>支付时间</dt><dd>{{ formatDate(selected.paidAt) }}</dd></div>
          </dl>
          <section class="order-drawer-section"><h3>商品明细</h3><div v-for="(item, index) in selected.items" :key="`${item.skuId}-${index}`" class="order-item"><div><strong>{{ item.itemName }}</strong><small>{{ item.skuId ? `商品规格 ${item.skuId}` : '未提供商品规格编号' }}</small></div><span>× {{ item.quantity }}</span><strong>{{ formatMoney(item.subtotalAmountCent, selected.currency) }}</strong></div></section>
          <section class="order-drawer-section"><h3>价格快照</h3><dl class="order-price-lines"><div><dt>原价</dt><dd>{{ formatMoney(selected.priceSnapshot.originalAmountCent, selected.currency) }}</dd></div><div><dt>优惠</dt><dd>- {{ formatMoney(selected.priceSnapshot.discountAmountCent, selected.currency) }}</dd></div><div><dt>服务费</dt><dd>{{ formatMoney(selected.priceSnapshot.serviceFeeCent, selected.currency) }}</dd></div><div class="total"><dt>应付</dt><dd>{{ formatMoney(selected.priceSnapshot.payableAmountCent, selected.currency) }}</dd></div></dl><p>计价规则版本：{{ selected.priceSnapshot.ruleVersions.join('、') || '暂无' }}</p></section>
          <section class="order-drawer-section no-refund-evidence"><h3>不可退款确认凭证</h3><p>{{ selected.noRefundEvidence.confirmed ? '用户已确认不可退款约定' : '尚未确认不可退款约定' }}</p><dl><div><dt>协议版本</dt><dd>{{ selected.noRefundEvidence.agreementVersion || '暂无' }}</dd></div><div><dt>确认时间</dt><dd>{{ formatDate(selected.noRefundEvidence.confirmedAt) }}</dd></div></dl></section>
        </template>
      </aside>
    </div>
  </article>
</template>

<style scoped>
.order-page{max-width:1600px}.order-heading h1{font-size:24px;letter-spacing:0}.order-heading-actions{display:flex;align-items:center;gap:12px}.order-heading-actions>span{color:var(--hhy-color-text-secondary);font-size:12px}.order-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));margin-bottom:20px;border:1px solid var(--hhy-color-border-default);border-radius:12px;background:var(--hhy-color-background-surface);overflow:hidden}.order-metrics>div{display:grid;grid-template-columns:1fr auto;gap:3px 14px;min-height:92px;padding:18px 20px;border-left:1px solid var(--hhy-color-border-default)}.order-metrics>div:first-child{border-left:0}.order-metrics span,.order-metrics small{color:var(--hhy-color-text-secondary);font-size:12px}.order-metrics strong{grid-row:1/3;grid-column:2;align-self:center;font-size:27px}.order-filters{display:grid;grid-template-columns:minmax(220px,1.2fr) minmax(210px,1fr) minmax(150px,.7fr) minmax(150px,.7fr) auto;gap:12px;align-items:end;margin-bottom:20px;border-radius:12px}.order-filter-actions{display:flex;gap:8px}.order-filter-actions button{height:46px;white-space:nowrap}.order-row-skeleton{height:58px}.order-table-card{padding:0;border-radius:12px;overflow:hidden}.order-table-wrap{overflow:auto}.order-table{width:100%;min-width:1120px;border-collapse:collapse}.order-table th,.order-table td{height:58px;padding:10px 16px;border-top:1px solid var(--hhy-color-border-default);text-align:left;font-size:14px;vertical-align:middle}.order-table thead th{height:44px;border-top:0;color:var(--hhy-color-text-secondary);background:var(--hhy-color-background-page);font-size:13px;font-weight:600}.order-table tbody tr:hover{background:color-mix(in srgb,var(--hhy-color-brand-primary) 4%,var(--hhy-color-background-surface))}.copyable-id{display:grid;gap:3px;max-width:210px;padding:0;border:0;background:transparent;color:var(--hhy-color-text-primary);text-align:left}.copyable-id strong,.copyable-id span{overflow:hidden;text-overflow:ellipsis}.copyable-id small{color:var(--hhy-color-text-tertiary);font-size:11px}.order-status[data-status="PAID"],.order-status[data-status="COMPLETED"]{color:var(--hhy-color-status-success);background:color-mix(in srgb,var(--hhy-color-status-success) 10%,var(--hhy-color-background-surface))}.order-status[data-status="PENDING_PAYMENT"],.order-status[data-status="PAYMENT_PROCESSING"],.order-status[data-status="FULFILLING"]{color:var(--hhy-color-status-warning);background:color-mix(in srgb,var(--hhy-color-status-warning) 11%,var(--hhy-color-background-surface))}.order-status[data-status="PAYMENT_FAILED"],.order-status[data-status="CHANNEL_REVERSAL"],.order-status[data-status="CLOSED"]{color:var(--hhy-color-status-error);background:color-mix(in srgb,var(--hhy-color-status-error) 8%,var(--hhy-color-background-surface))}.order-pagination{display:flex;justify-content:space-between;align-items:center;padding:14px 16px;border-top:1px solid var(--hhy-color-border-default)}.order-pagination>span{color:var(--hhy-color-text-secondary);font-size:12px}.order-pagination>div{display:flex;gap:8px}.order-pagination button{min-height:36px}.order-inline-error{display:flex;justify-content:space-between;align-items:center;padding:10px 16px;color:var(--hhy-color-status-error);background:color-mix(in srgb,var(--hhy-color-status-error) 7%,var(--hhy-color-background-surface));font-size:12px}.order-inline-error button{border:0;background:transparent;color:inherit;font-weight:700}.order-drawer-backdrop{position:fixed;inset:0;z-index:30;background:color-mix(in srgb,var(--hhy-color-text-primary) 36%,transparent)}.order-drawer{position:absolute;inset-block:0;right:0;width:min(580px,100%);padding:24px;overflow:auto;background:var(--hhy-color-background-surface);box-shadow:-20px 0 55px color-mix(in srgb,var(--hhy-color-text-primary) 16%,transparent)}.order-drawer>header{display:flex;justify-content:space-between;align-items:flex-start;padding-bottom:18px;border-bottom:1px solid var(--hhy-color-border-default)}.order-drawer>header span{color:var(--hhy-color-text-secondary);font-size:12px}.order-drawer>header h2{margin:6px 0 0;font-size:20px}.order-detail-skeleton{height:62px;margin-top:12px}.order-detail-hero{display:grid;grid-template-columns:1fr auto;gap:6px 14px;padding:22px 0}.order-detail-hero .status-chip{width:max-content}.order-detail-hero strong{grid-row:1/3;grid-column:2;font-size:27px}.order-detail-hero small{color:var(--hhy-color-text-secondary);font-size:12px}.order-detail-facts{display:grid;grid-template-columns:1fr 1fr;gap:1px;margin:0 0 22px;background:var(--hhy-color-border-default);border:1px solid var(--hhy-color-border-default);border-radius:10px;overflow:hidden}.order-detail-facts>div{padding:14px;background:var(--hhy-color-background-surface)}.order-detail-facts dt,.order-price-lines dt,.no-refund-evidence dt{color:var(--hhy-color-text-secondary);font-size:12px}.order-detail-facts dd,.order-price-lines dd,.no-refund-evidence dd{margin:6px 0 0;font-size:13px;overflow-wrap:anywhere}.order-drawer-section{padding:20px 0;border-top:1px solid var(--hhy-color-border-default)}.order-drawer-section h3{margin:0 0 14px;font-size:16px}.order-drawer-section>p{color:var(--hhy-color-text-secondary);font-size:12px;line-height:1.6}.order-item{display:grid;grid-template-columns:1fr auto auto;gap:14px;align-items:center;padding:12px 0;border-top:1px solid var(--hhy-color-border-default);font-size:13px}.order-item:first-of-type{border-top:0}.order-item strong,.order-item small{display:block}.order-item small{margin-top:4px;color:var(--hhy-color-text-secondary);font-size:11px}.order-price-lines{display:grid;gap:11px;margin:0}.order-price-lines>div{display:flex;justify-content:space-between}.order-price-lines dd{margin:0}.order-price-lines .total{padding-top:12px;border-top:1px solid var(--hhy-color-border-default);font-weight:700}.no-refund-evidence>p{padding:12px;border-radius:8px;background:var(--hhy-color-background-page)}.no-refund-evidence dl{display:grid;grid-template-columns:1fr 1fr;gap:12px}.no-refund-evidence dl>div{padding:12px;border:1px solid var(--hhy-color-border-default);border-radius:8px}.order-detail-error{padding-inline:0}
@media(max-width:1200px){.order-filters{grid-template-columns:1fr 1fr 1fr}.order-filter-actions{justify-content:flex-end}}@media(max-width:850px){.order-heading-actions{align-items:flex-start;flex-direction:column}.order-metrics{grid-template-columns:1fr 1fr}.order-metrics>div:nth-child(3){border-left:0;border-top:1px solid var(--hhy-color-border-default)}.order-metrics>div:nth-child(4){border-top:1px solid var(--hhy-color-border-default)}.order-filters{grid-template-columns:1fr 1fr}.order-filter-actions{grid-column:1/-1}}@media(max-width:560px){.order-metrics,.order-filters,.order-detail-facts{grid-template-columns:1fr}.order-metrics>div{border-left:0;border-top:1px solid var(--hhy-color-border-default)}.order-metrics>div:first-child{border-top:0}.order-filter-actions{grid-column:auto}.order-filter-actions button{flex:1}.order-pagination{align-items:flex-start;flex-direction:column}.order-drawer{padding:18px}.no-refund-evidence dl{grid-template-columns:1fr}}
</style>
