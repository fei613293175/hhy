<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import StatusNotice from '../components/StatusNotice.vue'
import {
  adminDomainsApi,
  adminSession,
  isApiRequestError,
  type DomainPage,
  type DomainResource,
  type UpdateDomainRequest,
} from '../services'

const domains = ref<DomainPage>()
const dnsActions = ref<DomainPage>()
const loading = ref(true)
const refreshing = ref(false)
const online = ref(typeof navigator === 'undefined' ? true : navigator.onLine)
const forbidden = ref(false)
const writeForbidden = ref(false)
const staleCodes = ref(new Set<string>())
const busyCode = ref<string>()
const editing = ref<DomainResource>()
const error = ref<{ message: string; requestId?: string }>()
const notice = ref<{ tone: 'success' | 'warning' | 'danger'; title: string; detail: string; requestId?: string }>()
const formError = ref<string>()
const form = reactive({ hostname: '', certificateMode: 'MANAGED', expectedVersion: 0 })
let request: AbortController | undefined

const items = computed(() => domains.value?.items ?? [])
const actionItems = computed(() => dnsActions.value?.items ?? [])
const canRead = computed(() => adminSession.hasPermission('config.manage') || adminSession.hasPermission('domain.read'))
const canWrite = computed(() => (adminSession.hasPermission('config.manage') || adminSession.hasPermission('domain.write')) && !writeForbidden.value)
const canVerify = computed(() => adminSession.hasPermission('config.manage') || adminSession.hasPermission('domain.verify'))

function normalized(value?: string) {
  return value?.trim().toUpperCase() ?? ''
}

function statusLabel(value?: string) {
  const labels: Record<string, string> = {
    PENDING_USER_DNS: '等待 DNS 解析', PENDING_DNS: '等待 DNS 解析',
    PASSED: '已通过', VERIFIED: '已验证', VALID: '证书有效',
    FAILED: '未通过', INVALID: '证书无效', NOT_RUN: '尚未执行',
    DNS_FAILED: 'DNS 失败', TLS_FAILED: 'TLS 失败',
    SERVICE_UNHEALTHY: '业务服务不健康', HEALTHY: '业务健康',
  }
  const key = normalized(value)
  return labels[key] ?? (value || '尚无记录')
}

function gateState(domain: DomainResource) {
  const dns = normalized(domain.dnsStatus)
  const https = normalized(domain.httpsStatus)
  const certificate = normalized(domain.certificateStatus)
  if (!['PASSED', 'VERIFIED'].includes(dns)) return { tone: 'pending', label: '等待 DNS' }
  if (!['PASSED', 'VERIFIED'].includes(https)) return { tone: 'danger', label: 'HTTPS 未通过' }
  if (certificate && !['PASSED', 'VALID', 'VERIFIED'].includes(certificate)) {
    return { tone: 'danger', label: '证书未通过' }
  }
  return { tone: 'warning', label: '等待业务健康确认' }
}

function formatDate(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
    : '尚未完成全层验证'
}

async function load(mode: 'initial' | 'refresh' = 'initial') {
  if (!canRead.value) {
    forbidden.value = true
    loading.value = false
    error.value = { message: '当前会话缺少域名读取权限。' }
    return
  }
  if (!online.value) {
    loading.value = false
    error.value = { message: '当前设备离线，请恢复网络后重试。' }
    return
  }
  request?.abort()
  const current = new AbortController()
  request = current
  if (mode === 'initial') loading.value = true
  else refreshing.value = true
  error.value = undefined
  try {
    const [domainPage, actionPage] = await Promise.all([
      adminDomainsApi.list({ page: 1, pageSize: 20, sort: 'updatedAt:desc' }, { signal: current.signal }),
      adminDomainsApi.dnsActions({ page: 1, pageSize: 20, status: 'PENDING_USER_DNS' }, { signal: current.signal }),
    ])
    domains.value = domainPage
    dnsActions.value = actionPage
    forbidden.value = false
    staleCodes.value = new Set()
  } catch (caught) {
    if (isApiRequestError(caught)) {
      if (caught.code === 'REQUEST_ABORTED') return
      forbidden.value = caught.status === 403
      error.value = { message: caught.message, requestId: caught.requestId }
    } else error.value = { message: '域名配置暂时无法加载，请稍后重试。' }
  } finally {
    if (request === current) request = undefined
    loading.value = false
    refreshing.value = false
  }
}

function openEdit(domain: DomainResource) {
  if (!canWrite.value || !online.value || staleCodes.value.has(domain.code)) return
  editing.value = domain
  form.hostname = domain.hostname
  form.certificateMode = 'MANAGED'
  form.expectedVersion = domain.version
  formError.value = undefined
}

function closeEdit() {
  if (!busyCode.value) editing.value = undefined
}

function applyOutcome(outcome: DomainResource | { status: string }) {
  if (!('code' in outcome) || !domains.value) return false
  domains.value = {
    ...domains.value,
    items: domains.value.items.map((item) => item.code === outcome.code ? outcome : item),
  }
  return true
}

async function submitEdit() {
  if (!editing.value || busyCode.value || !canWrite.value || !online.value) return
  const code = editing.value.code
  const hostname = form.hostname.trim().toLowerCase().replace(/\.$/, '')
  if (!/^[a-z0-9-]+\.orbexa\.cc$/.test(hostname)) {
    formError.value = '主机名必须是 orbexa.cc 下不含协议、端口或路径的受控二级域名。'
    return
  }
  const body: UpdateDomainRequest = {
    hostname,
    certificateMode: form.certificateMode,
    expectedVersion: form.expectedVersion,
  }
  busyCode.value = code
  formError.value = undefined
  try {
    const outcome = await adminDomainsApi.update(code, body)
    const applied = applyOutcome(outcome)
    editing.value = undefined
    notice.value = { tone: 'success', title: '域名计划已更新', detail: '旧健康结果已失效；请按 DNS 待办完成解析后重新验证。' }
    if (!applied) await load('refresh')
  } catch (caught) {
    handleWriteError(code, caught, true)
  } finally {
    busyCode.value = undefined
  }
}

async function verify(domain: DomainResource) {
  if (!canVerify.value || !online.value || busyCode.value || staleCodes.value.has(domain.code)) return
  busyCode.value = domain.code
  notice.value = undefined
  try {
    const outcome = await adminDomainsApi.verify(domain.code, { force: false })
    const applied = applyOutcome(outcome)
    notice.value = {
      tone: 'success', title: '分层验证已执行',
      detail: 'DNS、TLS/证书和业务健康由服务端逐层校验；任一层失败都不会标记可用。',
    }
    if (!applied) await load('refresh')
  } catch (caught) {
    handleWriteError(domain.code, caught, false)
  } finally {
    busyCode.value = undefined
  }
}

function handleWriteError(code: string, caught: unknown, inForm: boolean) {
  if (!isApiRequestError(caught)) {
    const message = '网络异常或服务暂时不可用；写操作没有自动重试，请确认服务端结果后再继续。'
    if (inForm) formError.value = message
    else notice.value = { tone: 'danger', title: '验证结果未确认', detail: message }
    return
  }
  if (caught.status === 401) {
    adminSession.clear()
    editing.value = undefined
    notice.value = { tone: 'danger', title: '管理员会话已失效', detail: '请重新登录后继续。', requestId: caught.requestId }
  } else if (caught.status === 403) {
    writeForbidden.value = true
    editing.value = undefined
    notice.value = { tone: 'danger', title: '域名写权限已收回', detail: caught.message, requestId: caught.requestId }
  } else if (caught.status === 409) {
    staleCodes.value = new Set([...staleCodes.value, code])
    editing.value = undefined
    notice.value = { tone: 'warning', title: '域名版本已变化', detail: '已阻止覆盖服务端新版本，刷新并重新核对后才能继续。', requestId: caught.requestId }
  } else {
    const message = caught.status === 422 ? `当前状态不允许该操作：${caught.message}` : caught.message
    if (inForm) formError.value = message
    else notice.value = { tone: 'danger', title: '分层验证未通过', detail: message, requestId: caught.requestId }
  }
}

function updateOnline() {
  online.value = navigator.onLine
  if (!online.value) request?.abort()
  else if (!domains.value) void load()
}

onMounted(() => {
  window.addEventListener('online', updateOnline)
  window.addEventListener('offline', updateOnline)
  void load()
})
onBeforeUnmount(() => {
  request?.abort()
  window.removeEventListener('online', updateOnline)
  window.removeEventListener('offline', updateOnline)
})
</script>

<template>
  <article class="page domain-page">
    <header class="page-heading">
      <div><div class="eyebrow">配置中心 · 域名与环境</div><h1>orbexa.cc 域名编排</h1><p>管理生产/预发布二级域名、DNS 待办以及 DNS、TLS 和业务健康分层验证。</p></div>
      <div class="page-actions"><button class="ghost-button" :disabled="refreshing || !online || forbidden" @click="load('refresh')">{{ refreshing ? '刷新中…' : '刷新' }}</button></div>
    </header>

    <StatusNotice v-if="!online && domains" tone="warning" title="当前设备离线" detail="正在显示最近一次成功读取的安全快照；离线时禁止修改和验证。" />
    <StatusNotice v-if="notice" :tone="notice.tone" :title="notice.title" :detail="notice.detail" :request-id="notice.requestId" />
    <StatusNotice v-if="staleCodes.size" tone="warning" title="部分域名需要刷新" detail="检测到服务端版本冲突，受影响域名的写操作已暂停。" />

    <section v-if="loading" class="domain-grid" aria-label="正在加载域名配置"><div v-for="index in 6" :key="index" class="card skeleton domain-skeleton" /></section>
    <section v-else-if="error && !domains" class="card error-state" aria-live="polite"><div class="avatar centered-mark">!</div><h2>{{ forbidden ? '无法访问域名配置' : '域名配置暂时无法加载' }}</h2><p>{{ error.message }}</p><small v-if="error.requestId">请求编号：{{ error.requestId }}</small><div><button v-if="!forbidden" class="primary-button" :disabled="!online" @click="load()">重新加载</button></div></section>

    <template v-else-if="domains">
      <section class="domain-summary metric-grid"><div class="metric"><span>受控域名</span><strong>{{ items.length }}</strong></div><div class="metric"><span>DNS 待办</span><strong>{{ actionItems.length }}</strong></div><div class="metric"><span>根域名</span><strong class="metric-value-compact">orbexa.cc</strong></div><div class="metric"><span>可用判定</span><strong class="metric-value-compact">三层全通过</strong></div></section>
      <StatusNotice tone="warning" title="DNS 成功不等于服务可用" detail="域名只有在 DNS 解析、TLS/证书和业务健康全部通过后才能激活；本页不会用 HTTPS 可达替代业务健康。" />

      <section v-if="items.length" class="domain-grid">
        <article v-for="domain in items" :key="domain.code" class="card domain-card">
          <div class="domain-card-head"><div><span class="domain-code">{{ domain.code }}</span><h2>{{ domain.hostname }}</h2></div><span class="domain-gate" :data-tone="gateState(domain).tone">{{ gateState(domain).label }}</span></div>
          <dl class="domain-layers"><div><dt>DNS</dt><dd>{{ statusLabel(domain.dnsStatus) }}</dd></div><div><dt>HTTPS</dt><dd>{{ statusLabel(domain.httpsStatus) }}</dd></div><div><dt>证书</dt><dd>{{ statusLabel(domain.certificateStatus) }}</dd></div></dl>
          <dl class="domain-meta"><dt>环境</dt><dd>{{ domain.environment || '未标注' }}</dd><dt>服务端版本</dt><dd>v{{ domain.version }}</dd><dt>全层验证时间</dt><dd>{{ formatDate(domain.lastVerifiedAt) }}</dd></dl>
          <div class="domain-actions"><button class="secondary-button" :disabled="!canWrite || !online || Boolean(busyCode) || staleCodes.has(domain.code)" @click="openEdit(domain)">修改计划</button><button class="primary-button" :disabled="!canVerify || !online || Boolean(busyCode) || staleCodes.has(domain.code)" @click="verify(domain)">{{ busyCode === domain.code ? '验证中…' : '分层验证' }}</button></div>
        </article>
      </section>
      <section v-else class="card empty-state"><h2>尚无域名配置</h2><p>服务端还未返回 DOMAIN_PLAN 中的受控域名，请先完成数据初始化。</p></section>

      <section class="card dns-actions-card"><div class="section-heading"><div><h2>DNS 用户待办</h2><p>记录类型、主机名、环境和当前验证状态由服务端生成。</p></div><span class="tag" :class="actionItems.length ? 'tag-warning' : 'tag-success'">{{ actionItems.length ? `待处理 ${actionItems.length} 项` : '当前无待办' }}</span></div><div v-if="actionItems.length" class="dns-action-list"><div v-for="item in actionItems" :key="item.code" class="dns-action-row"><div><strong>{{ item.hostname }}</strong><small>{{ item.environment || '未标注环境' }} · CNAME_OR_A</small></div><span class="status-chip" data-status="PENDING_APPROVAL">{{ statusLabel(item.dnsStatus) }}</span></div></div></section>
    </template>

    <div v-if="editing" class="modal-backdrop" @click.self="closeEdit">
      <section class="modal domain-modal" role="dialog" aria-modal="true" aria-labelledby="domain-edit-title">
        <header class="modal-header"><div><h2 id="domain-edit-title">修改域名计划</h2><p>{{ editing.code }} · {{ editing.environment }} · 当前版本 v{{ editing.version }}</p></div><button class="icon-button" aria-label="关闭" :disabled="Boolean(busyCode)" @click="closeEdit">×</button></header>
        <form class="form-stack" novalidate @submit.prevent="submitEdit"><label class="field"><span class="field-label">受控主机名</span><input v-model="form.hostname" class="input" maxlength="253" autocomplete="off" required /><span class="field-help">仅允许 DOMAIN_PLAN 登记的 orbexa.cc 二级域名，禁止协议、端口、路径或用户凭据。</span></label><label class="field"><span class="field-label">证书模式</span><select v-model="form.certificateMode" class="input"><option value="MANAGED">托管证书（MANAGED）</option><option value="EXTERNAL">外部证书（EXTERNAL）</option></select></label><label class="field"><span class="field-label">乐观锁版本</span><input v-model.number="form.expectedVersion" class="input" type="number" readonly /></label><StatusNotice tone="warning" title="更新会重置旧验证结果" detail="保存后域名回到等待 DNS 状态，必须重新通过三层验证。" /><p v-if="formError" class="field-error domain-form-error" role="alert">{{ formError }}</p><div class="modal-footer"><button type="button" class="ghost-button" :disabled="Boolean(busyCode)" @click="closeEdit">取消</button><button class="primary-button" :disabled="Boolean(busyCode) || !canWrite || !online">{{ busyCode ? '保存中…' : '保存并重置验证' }}</button></div></form>
      </section>
    </div>
  </article>
</template>

<style src="../domain-config.css"></style>
