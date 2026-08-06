<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminFinanceApi, type FinanceQuery } from '../services/adminFinance'
import { ApiRequestError } from '../services/apiError'
import StatusNotice from '../components/StatusNotice.vue'

const props = defineProps<{ kind: 'rewards' | 'ledger' | 'withdrawals' | 'accounting'; detail?: boolean }>()
const loading = ref(true); const error = ref(''); const keyword = ref(''); const status = ref(''); const page = ref<any>(); const selected = ref<any>()
const title = computed(() => ({ rewards: '奖励账户', ledger: '奖励流水', withdrawals: props.detail ? '提现详情' : '提现列表', accounting: props.detail ? '会计交易详情与冲正' : '统一会计交易' }[props.kind]))
const items = computed(() => page.value?.items ?? [])
const money = (cent: number | undefined) => cent == null ? '—' : `¥${(cent / 100).toFixed(2)}`
async function load() {
  loading.value = true; error.value = ''
  try {
    const query: FinanceQuery = { page: 1, pageSize: 20, keyword: keyword.value || undefined, status: status.value || undefined, sort: 'createdAt:desc' }
    page.value = props.kind === 'rewards' ? await adminFinanceApi.rewards(query) : props.kind === 'ledger' ? await adminFinanceApi.rewardLedger('current', query) : props.kind === 'withdrawals' ? await adminFinanceApi.withdrawals(query) : await adminFinanceApi.accounting(query)
  } catch (caught) { error.value = caught instanceof ApiRequestError ? caught.message : '数据暂时无法加载' } finally { loading.value = false }
}
function open(item: any) { selected.value = item }
onMounted(load)
</script>
<template>
<article class="page finance-page">
  <header class="page-heading"><div><div class="eyebrow">R24 · 财务运营</div><h1>{{ title }}</h1><p>数据来自服务端权威接口，敏感支付账号默认脱敏。</p></div><button class="ghost-button" :disabled="loading" @click="load">刷新</button></header>
  <StatusNotice v-if="error" tone="danger" title="数据加载失败" :detail="error" />
  <section class="card finance-filters"><label class="field"><span class="field-label">关键词</span><input v-model="keyword" class="input" maxlength="100" placeholder="用户、订单或业务号" /></label><label class="field"><span class="field-label">状态</span><input v-model="status" class="input" maxlength="64" placeholder="按状态筛选" /></label><button class="primary-button" :disabled="loading" @click="load">查询</button></section>
  <section v-if="loading" class="card skeleton-stack"><div v-for="n in 5" :key="n" class="skeleton payment-row-skeleton" /></section>
  <section v-else-if="!items.length" class="card empty-state"><h2>暂无可展示记录</h2><p>当前筛选条件没有真实业务数据，请调整筛选或稍后重试。</p></section>
  <section v-else class="card payment-table-card"><div class="payment-table-wrap"><table class="payment-table"><thead><tr><th v-if="kind === 'rewards' || kind === 'ledger'">用户ID</th><th v-if="kind === 'withdrawals'">提现单号</th><th v-if="kind === 'accounting'">交易ID</th><th>状态</th><th>金额</th><th v-if="kind === 'rewards'">可用/待结算</th><th v-if="kind === 'withdrawals'">手续费/到账</th><th>更新时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in items" :key="item.id || item.userId || item.withdrawalNo"><td v-if="kind === 'rewards' || kind === 'ledger'">{{ item.userId }}</td><td v-if="kind === 'withdrawals'">{{ item.withdrawalNo }}</td><td v-if="kind === 'accounting'">{{ item.id }}</td><td><span class="status-chip">{{ item.status || '—' }}</span></td><td>{{ money(item.amountCent ?? item.availableCent) }}</td><td v-if="kind === 'rewards'">{{ money(item.availableCent) }} / {{ money(item.pendingCent) }}</td><td v-if="kind === 'withdrawals'">{{ money(item.feeCent) }} / {{ money(item.netAmountCent) }}</td><td>{{ item.updatedAt || item.createdAt || '—' }}</td><td><button class="secondary-button" @click="open(item)">查看详情</button></td></tr></tbody></table></div></section>
  <div v-if="selected" class="modal-backdrop" @click.self="selected = undefined"><section class="modal" role="dialog" aria-modal="true"><header class="modal-header"><div><h2>业务详情</h2><p>仅展示当前接口返回字段；服务端版本 {{ selected.version ?? '—' }}</p></div><button class="icon-button" @click="selected = undefined">×</button></header><dl class="detail-grid"><template v-for="(value,key) in selected" :key="key"><dt>{{ key }}</dt><dd>{{ typeof value === 'object' ? JSON.stringify(value) : value }}</dd></template></dl><footer class="modal-footer"><button class="primary-button" @click="selected = undefined">关闭</button></footer></section></div>
</article>
</template>
