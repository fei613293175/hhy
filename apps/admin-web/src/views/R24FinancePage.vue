<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminFinanceApi, type FinanceQuery } from '../services/adminFinance'
import { adminSession } from '../services/adminSession'
import { ApiRequestError } from '../services/apiError'
import StatusNotice from '../components/StatusNotice.vue'

type Kind = 'rewards' | 'ledger' | 'withdrawals' | 'accounting'
type Action = 'adjustment' | 'risk' | 'finance' | 'payout' | 'query' | 'reversal'
const props = defineProps<{ kind: Kind; detail?: boolean }>()
const route = useRoute(); const router = useRouter()
const loading = ref(true); const saving = ref(false); const error = ref(''); const notice = ref('')
const keyword = ref(''); const status = ref(''); const page = ref<any>({ items: [] }); const detail = ref<any>()
const action = ref<Action>(); const target = ref<any>()
const form = ref<{ direction: 'CREDIT' | 'DEBIT'; amountCent: number; decision: 'APPROVE' | 'REJECT' | 'ESCALATE'; reason: string; evidenceIds: string; payoutChannel: string; reasonCode: string; approvalId: string }>({ direction: 'CREDIT', amountCent: 0, decision: 'APPROVE', reason: '', evidenceIds: '', payoutChannel: 'ALIPAY_ENTERPRISE', reasonCode: '', approvalId: '' })
const detailId = computed(() => String(route.params.id ?? ''))
const ledgerUserId = computed(() => String(route.query.userId ?? keyword.value).trim())
const title = computed(() => ({ rewards: '奖励账户', ledger: '奖励流水', withdrawals: props.detail ? '提现详情' : '提现列表', accounting: props.detail ? '会计交易详情与冲正' : '统一会计交易' }[props.kind]))
const items = computed<any[]>(() => page.value?.items ?? [])
const summary = computed(() => items.value.reduce((value, item) => ({ available: value.available + Number(item.availableCent ?? 0), pending: value.pending + Number(item.pendingCent ?? 0), frozen: value.frozen + Number(item.frozenCent ?? 0) }), { available: 0, pending: 0, frozen: 0 }))
const can = (permission: string) => adminSession.hasPermission(permission)
const money = (cent: number | undefined) => cent == null ? '—' : `¥${(Number(cent) / 100).toFixed(2)}`
const date = (value?: string) => value ? new Date(value).toLocaleString('zh-CN') : '—'
const label = (value?: string) => ({ RISK_REVIEWING: '风控审核', FINANCE_REVIEWING: '财务审核', APPROVED: '待出款', PAYOUT_PROCESSING: '出款中', SUCCESS: '成功', FAILED: '失败', REJECTED: '已驳回', POSTED: '已过账' }[value ?? ''] ?? value ?? '—')
const message = (caught: unknown) => caught instanceof ApiRequestError ? caught.message : '数据暂时无法加载'

async function load() {
  loading.value = true; error.value = ''; notice.value = ''
  try {
    if (props.detail) {
      if (!detailId.value) throw new Error('missing id')
      detail.value = props.kind === 'withdrawals' ? await adminFinanceApi.withdrawal(detailId.value) : await adminFinanceApi.accountingTransaction(detailId.value)
      return
    }
    const query: FinanceQuery = { page: 1, pageSize: 20, keyword: keyword.value || undefined, status: status.value || undefined, sort: 'createdAt:desc' }
    if (props.kind === 'rewards') page.value = await adminFinanceApi.rewards(query)
    else if (props.kind === 'ledger') page.value = ledgerUserId.value ? await adminFinanceApi.rewardLedger(ledgerUserId.value, query) : { items: [], page: { page: 1, pageSize: 20, hasNext: false } }
    else if (props.kind === 'withdrawals') page.value = await adminFinanceApi.withdrawals(query)
    else page.value = await adminFinanceApi.accounting(query)
  } catch (caught) { error.value = message(caught) } finally { loading.value = false }
}

function open(item: any) {
  if (props.kind === 'rewards') router.push({ name: 'ADM-REWARD-002', query: { userId: item.userId } })
  else if (props.kind === 'withdrawals') router.push({ name: 'ADM-WD-002', params: { id: item.id } })
  else if (props.kind === 'accounting') router.push({ name: 'ADM-ACCOUNTING-002', params: { id: item.id } })
}
function openAction(next: Action, item: any = detail.value) {
  action.value = next; target.value = item
  form.value = { direction: 'CREDIT', amountCent: 0, decision: 'APPROVE', reason: '', evidenceIds: '', payoutChannel: 'ALIPAY_ENTERPRISE', reasonCode: '', approvalId: '' }
}
function closeAction() { if (!saving.value) { action.value = undefined; target.value = undefined } }
const evidence = () => form.value.evidenceIds.split(',').map((value) => value.trim()).filter(Boolean)
async function submitAction() {
  if (!action.value || !target.value) return
  saving.value = true; error.value = ''; notice.value = ''
  try {
    const version = Number(target.value.version ?? 0)
    if (action.value === 'adjustment') await adminFinanceApi.rewardAdjustment({ userId: String(target.value.userId), direction: form.value.direction, amountCent: Number(form.value.amountCent), reason: form.value.reason })
    else if (action.value === 'risk') await adminFinanceApi.riskReview(String(target.value.id), { decision: form.value.decision, reason: form.value.reason, expectedVersion: version, evidenceIds: evidence() })
    else if (action.value === 'finance') await adminFinanceApi.financeReview(String(target.value.id), { decision: form.value.decision, reason: form.value.reason, expectedVersion: version, evidenceIds: evidence() })
    else if (action.value === 'payout') await adminFinanceApi.payout(String(target.value.id), { payoutChannel: form.value.payoutChannel, expectedVersion: version })
    else if (action.value === 'query') await adminFinanceApi.queryPayout(String(target.value.id), { reason: form.value.reason, expectedVersion: version, payload: {} })
    else await adminFinanceApi.reverseAccounting(String(target.value.id), { reasonCode: form.value.reasonCode, reason: form.value.reason, evidenceIds: evidence(), approvalId: form.value.approvalId, expectedVersion: version })
    notice.value = action.value === 'adjustment' || action.value === 'reversal' ? '申请已提交，等待独立审批或执行结果。' : '操作已提交，页面已同步最新状态。'
    closeAction(); await load()
  } catch (caught) { error.value = message(caught) } finally { saving.value = false }
}

watch(() => route.fullPath, load)
onMounted(load)
</script>

<template>
<article class="page finance-page">
  <header class="page-heading"><div><div class="eyebrow">R24 · 财务运营</div><h1>{{ title }}</h1><p>奖励、提现与复式记账均以服务端状态和版本为准。</p></div><div class="page-actions"><button v-if="detail" class="ghost-button" @click="router.back()">返回</button><button class="ghost-button" :disabled="loading" @click="load">刷新</button></div></header>
  <StatusNotice v-if="error" tone="danger" title="请求未完成" :detail="error" />
  <StatusNotice v-if="notice" tone="success" title="操作结果" :detail="notice" />

  <template v-if="!detail">
    <section v-if="kind === 'rewards'" class="finance-metrics" aria-label="当前页奖励摘要"><div class="finance-metric primary"><span>可用奖励</span><strong>{{ money(summary.available) }}</strong><small>当前页账户合计</small></div><div class="finance-metric"><span>待结算</span><strong>{{ money(summary.pending) }}</strong><small>等待业务结算</small></div><div class="finance-metric"><span>冻结</span><strong>{{ money(summary.frozen) }}</strong><small>受限奖励金额</small></div></section>
    <form class="card finance-filters" @submit.prevent="load"><label class="field"><span class="field-label">{{ kind === 'ledger' ? '用户 ID' : '关键词' }}</span><input v-model="keyword" class="input" maxlength="100" :placeholder="kind === 'ledger' ? '输入用户 ID' : '用户、提现单或业务号'" /></label><label v-if="kind !== 'ledger'" class="field"><span class="field-label">状态</span><input v-model="status" class="input" maxlength="64" placeholder="按状态筛选" /></label><button class="primary-button" :disabled="loading">查询</button></form>
    <section v-if="loading" class="card skeleton-stack"><div v-for="n in 5" :key="n" class="skeleton payment-row-skeleton" /></section>
    <section v-else-if="!items.length" class="card empty-state"><h2>暂无可展示记录</h2><p>当前条件没有返回真实业务数据。</p></section>
    <section v-else class="card payment-table-card"><div class="payment-table-wrap"><table class="payment-table"><thead><tr><th>{{ kind === 'withdrawals' ? '提现单号' : kind === 'accounting' ? '交易号' : '用户 / 来源' }}</th><th>状态</th><th>主金额</th><th>资金拆分</th><th>业务时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in items" :key="item.id || item.userId"><td><strong>{{ item.withdrawalNo || item.transactionNo || item.sourceType || item.userId }}</strong><small v-if="item.businessType || item.bizId">{{ item.businessType || item.bizId }}</small></td><td><span class="status-chip" :data-status="item.status">{{ label(item.status) }}</span></td><td>{{ money(item.amountCent ?? item.availableCent ?? item.debitTotalCent) }}</td><td><template v-if="kind === 'rewards'">待结算 {{ money(item.pendingCent) }} / 冻结 {{ money(item.frozenCent) }}</template><template v-else-if="kind === 'withdrawals'">手续费 {{ money(item.feeCent) }} / 到账 {{ money(item.netAmountCent) }}</template><template v-else-if="kind === 'accounting'">借 {{ money(item.debitTotalCent) }} / 贷 {{ money(item.creditTotalCent) }}</template><template v-else>余额 {{ money(item.balanceAfterCent) }}</template></td><td>{{ date(item.postedAt || item.completedAt || item.createdAt || item.updatedAt) }}</td><td class="button-row"><button v-if="kind !== 'ledger'" class="secondary-button" @click="open(item)">{{ kind === 'rewards' ? '查看流水' : '查看详情' }}</button><button v-if="kind === 'rewards' && can('reward.adjust.request')" class="ghost-button" @click="openAction('adjustment', item)">奖励调整</button></td></tr></tbody></table></div></section>
  </template>

  <template v-else>
    <section v-if="loading" class="card skeleton-stack"><div v-for="n in 5" :key="n" class="skeleton payment-row-skeleton" /></section>
    <section v-else-if="detail" class="finance-detail">
      <div class="detail-status-band"><div><span>{{ kind === 'withdrawals' ? '提现状态' : '会计状态' }}</span><strong>{{ label(detail.status) }}</strong></div><div><span>主金额</span><strong>{{ money(detail.amountCent ?? detail.debitTotalCent) }}</strong></div><div><span>服务端版本</span><strong>v{{ detail.version }}</strong></div></div>
      <dl class="facts-grid card"><div v-for="(value, key) in detail" v-show="key !== 'entries'" :key="key"><dt>{{ key }}</dt><dd>{{ key.toString().includes('Cent') ? money(Number(value)) : value || '—' }}</dd></div></dl>
      <section v-if="kind === 'accounting'" class="card detail-panel"><h2>复式记账分录</h2><table class="payment-table"><thead><tr><th>账户</th><th>方向</th><th>金额</th><th>业务</th><th>时间</th></tr></thead><tbody><tr v-for="entry in detail.entries || []" :key="entry.id"><td>{{ entry.accountCode }}<small>{{ entry.accountName }}</small></td><td>{{ entry.direction === 'DEBIT' ? '借' : '贷' }}</td><td>{{ money(entry.amountCent) }}</td><td>{{ entry.businessType }} / {{ entry.businessId }}</td><td>{{ date(entry.createdAt) }}</td></tr></tbody></table></section>
      <footer class="finance-action-bar"><template v-if="kind === 'withdrawals'"><button v-if="detail.status === 'RISK_REVIEWING' && can('withdrawal.risk')" class="primary-button" @click="openAction('risk')">风控审核</button><button v-if="detail.status === 'FINANCE_REVIEWING' && can('withdrawal.finance')" class="primary-button" @click="openAction('finance')">财务审核</button><button v-if="detail.status === 'APPROVED' && can('withdrawal.payout')" class="primary-button" @click="openAction('payout')">发起出款</button><button v-if="detail.status === 'PAYOUT_PROCESSING' && can('withdrawal.payout')" class="secondary-button" @click="openAction('query')">主动查单</button></template><button v-else-if="detail.status === 'POSTED' && !detail.reversalOfTransactionId && can('accounting.reversal.request')" class="danger-button" @click="openAction('reversal')">申请冲正</button></footer>
    </section>
  </template>

  <div v-if="action && target" class="modal-backdrop" @click.self="closeAction"><section class="modal" role="dialog" aria-modal="true"><header class="modal-header"><div><h2>{{ action === 'adjustment' ? '奖励调整申请' : action === 'risk' ? '风控审核' : action === 'finance' ? '财务审核' : action === 'payout' ? '发起出款' : action === 'query' ? '主动查单' : '会计冲正申请' }}</h2><p>目标 {{ target.withdrawalNo || target.transactionNo || target.userId }} · 服务端版本 v{{ target.version ?? 0 }}</p></div><button class="icon-button" aria-label="关闭" :disabled="saving" @click="closeAction">×</button></header><form class="form-stack" @submit.prevent="submitAction">
    <label v-if="action === 'adjustment'" class="field"><span class="field-label">调整方向</span><select v-model="form.direction" class="input"><option value="CREDIT">增加</option><option value="DEBIT">扣减</option></select></label>
    <label v-if="action === 'adjustment'" class="field"><span class="field-label">金额（分）</span><input v-model.number="form.amountCent" class="input" type="number" min="1" required /></label>
    <label v-if="action === 'risk' || action === 'finance'" class="field"><span class="field-label">审核决定</span><select v-model="form.decision" class="input"><option value="APPROVE">通过</option><option value="REJECT">驳回</option><option v-if="action === 'risk'" value="ESCALATE">升级复核</option></select></label>
    <label v-if="action === 'payout'" class="field"><span class="field-label">出款通道</span><select v-model="form.payoutChannel" class="input"><option value="ALIPAY_ENTERPRISE">支付宝企业付款</option><option value="ALIPAY">支付宝</option></select></label>
    <label v-if="action === 'reversal'" class="field"><span class="field-label">原因代码</span><input v-model="form.reasonCode" class="input" maxlength="64" required /></label>
    <label v-if="action === 'reversal'" class="field"><span class="field-label">审批单 ID</span><input v-model="form.approvalId" class="input" maxlength="64" required /></label>
    <label v-if="action !== 'payout'" class="field"><span class="field-label">原因</span><textarea v-model="form.reason" class="input" maxlength="2000" :required="action !== 'query'" /></label>
    <label v-if="action === 'risk' || action === 'finance' || action === 'reversal'" class="field"><span class="field-label">证据 ID（逗号分隔）</span><input v-model="form.evidenceIds" class="input" maxlength="1000" /></label>
    <StatusNotice tone="warning" title="提交前检查" detail="写操作绑定当前服务端版本和独立幂等键；版本冲突不会覆盖新数据。" />
    <footer class="modal-footer"><button type="button" class="ghost-button" :disabled="saving" @click="closeAction">取消</button><button class="primary-button" :disabled="saving">{{ saving ? '提交中…' : '确认提交' }}</button></footer>
  </form></section></div>
</article>
</template>

<style scoped>
.finance-metrics,.detail-status-band{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-bottom:16px}.finance-metric,.detail-status-band>div{display:grid;gap:5px;padding:18px 20px;border:1px solid var(--hhy-color-border-default);background:var(--hhy-color-background-surface)}.finance-metric:first-child{border-radius:8px 0 0 8px}.finance-metric:last-child{border-radius:0 8px 8px 0}.finance-metric.primary{color:var(--hhy-color-text-inverse);border-color:var(--hhy-color-brand-primary);background:var(--hhy-color-brand-primary)}.finance-metric span,.detail-status-band span{font-size:13px;color:var(--hhy-color-text-secondary)}.finance-metric.primary span,.finance-metric.primary small{color:var(--hhy-color-text-inverse)}.finance-metric strong,.detail-status-band strong{font-size:24px}.finance-metric small{font-size:12px;color:var(--hhy-color-text-secondary)}.payment-table td strong,.payment-table td small{display:block}.payment-table td small{margin-top:4px;color:var(--hhy-color-text-secondary)}.finance-detail{display:grid;gap:16px}.facts-grid{margin:0}.detail-panel h2{margin-top:0;font-size:18px}.finance-action-bar{position:sticky;bottom:0;display:flex;justify-content:flex-end;gap:10px;padding:14px 0;background:var(--hhy-color-background-page)}@media(max-width:760px){.finance-metrics,.detail-status-band{grid-template-columns:1fr}.finance-metric:first-child,.finance-metric:last-child{border-radius:8px}.finance-action-bar{position:static;flex-wrap:wrap}}
</style>
