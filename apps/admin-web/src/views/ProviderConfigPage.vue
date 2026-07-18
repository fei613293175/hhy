<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import StatusNotice from '../components/StatusNotice.vue'
import ProviderCertificatePanel from '../components/ProviderCertificatePanel.vue'
import {
  adminProviderConfigsApi,
  adminSession,
  isApiRequestError,
  type ActivateProviderConfigRequest,
  type CreateProviderConfigVersionRequest,
  type ProviderConfigPage,
  type ProviderConfigResource,
  type RollbackProviderConfigRequest,
  type TestProviderConfigRequest,
} from '../services'

type ProviderCode = 'sms' | 'storage' | 'payment' | 'payout' | 'identity'
type ProviderAction = 'create' | 'test' | 'activate' | 'rollback'
const props = defineProps<{ provider?: ProviderCode }>()

const providerMeta: Record<ProviderCode, { name: string; summary: string; mark: string; route: string }> = {
  sms: { name: '阿里云短信', summary: 'AccessKey、Region、签名、模板、限流和发送测试', mark: '短', route: '/system/providers/sms' },
  storage: { name: '对象存储', summary: 'Cloudflare R2、阿里云 OSS、Scope 绑定和迁移状态', mark: '存', route: '/system/providers/storage' },
  payment: { name: '支付网关', summary: '彩虹易支付商户、签名、渠道、回调和只读连接测试', mark: '付', route: '/system/providers/payment' },
  payout: { name: '支付宝企业付款', summary: 'AppID、商家ID、证书引用、网关和只读认证测试', mark: '款', route: '/system/providers/payout' },
  identity: { name: '实名认证', summary: 'APPCODE、活体检测、身份比对、超时和结果映射', mark: '实', route: '/system/providers/identity' },
}

const page = ref<ProviderConfigPage>()
const detail = ref<ProviderConfigResource>()
const loading = ref(true)
const refreshing = ref(false)
const online = ref(navigator.onLine)
const forbidden = ref(false)
const error = ref<{ message: string; requestId?: string }>()
const notice = ref<{ tone: 'success' | 'warning' | 'danger'; title: string; detail: string; requestId?: string }>()
const action = ref<ProviderAction>()
const submitting = ref(false)
const stale = ref(false)
const writeForbidden = ref(false)
const formError = ref<string>()
const modalElement = ref<HTMLElement>()
const createForm = reactive({ environment: 'STAGING', values: '{}', secretRefs: '{}', remark: '' })
const testForm = reactive({ versionId: '', testRecipient: '' })
const activateForm = reactive({ versionId: '', approvalId: '', expectedVersion: 0 })
const rollbackForm = reactive({ targetVersionId: '', approvalId: '', reason: '', expectedVersion: 0 })
let request: AbortController | undefined
let previousFocus: HTMLElement | null = null

const meta = computed(() => props.provider ? providerMeta[props.provider] : undefined)
const items = computed(() => page.value?.items ?? [])
const secretCount = computed(() => detail.value?.configuredSecrets?.filter((item) => item.configured).length ?? 0)
const hasReadPermission = computed(() => adminSession.hasPermission('config.manage'))
const writable = computed(() => Boolean(
  props.provider && hasReadPermission.value && online.value && !submitting.value
  && !stale.value && !writeForbidden.value && !forbidden.value,
))
const canTest = computed(() => Boolean(detail.value?.draftVersion))
const canActivate = computed(() => Boolean(
  detail.value?.draftVersion
  && ['CONNECTION_TESTED', 'PENDING_APPROVAL'].includes(detail.value.connectionStatus ?? ''),
))
const canRollback = computed(() => Boolean(detail.value?.activeVersion))
const actionTitle = computed(() => ({
  create: '创建配置版本', test: '执行真实连接测试', activate: '审批并激活版本', rollback: '回滚供应商配置',
}[action.value ?? 'create']))

function resource(code: ProviderCode) {
  return items.value.find((item) => item.provider === code)
}

function statusLabel(status?: string) {
  const labels: Record<string, string> = {
    DRAFT: '草稿待校验', VALIDATED: '已校验', CONNECTION_TESTED: '连接测试通过',
    PENDING_APPROVAL: '等待双人审批', ACTIVE: '运行中', SUPERSEDED: '已被替代',
    ROLLED_BACK: '已回滚', FAILED: '连接失败',
  }
  return status ? (labels[status] ?? status) : '尚未测试'
}

function formatDate(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
    : '尚无记录'
}

async function load(mode: 'initial' | 'refresh' = 'initial') {
  if (!hasReadPermission.value) {
    forbidden.value = true
    loading.value = false
    error.value = { message: '当前会话缺少 config.manage，供应商配置不会加载。' }
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
    if (props.provider) detail.value = await adminProviderConfigsApi.get(props.provider, { signal: current.signal })
    else page.value = await adminProviderConfigsApi.list({ page: 1, pageSize: 20 }, { signal: current.signal })
    forbidden.value = false
    stale.value = false
  } catch (caught) {
    if (isApiRequestError(caught)) {
      if (caught.code === 'REQUEST_ABORTED') return
      forbidden.value = caught.status === 403
      error.value = { message: caught.message, requestId: caught.requestId }
    } else {
      error.value = { message: '供应商配置暂时无法加载，请稍后重试。' }
    }
  } finally {
    if (request === current) request = undefined
    loading.value = false
    refreshing.value = false
  }
}

function openAction(nextAction: ProviderAction) {
  if (!writable.value || !detail.value) return
  notice.value = undefined
  formError.value = undefined
  action.value = nextAction
  if (nextAction === 'create') {
    createForm.environment = detail.value.environment || 'STAGING'
    createForm.values = '{}'
    createForm.secretRefs = '{}'
    createForm.remark = ''
  } else if (nextAction === 'test') {
    testForm.versionId = detail.value.draftVersion ?? ''
    testForm.testRecipient = ''
  } else if (nextAction === 'activate') {
    activateForm.versionId = detail.value.draftVersion ?? ''
    activateForm.approvalId = ''
    activateForm.expectedVersion = detail.value.version
  } else {
    rollbackForm.targetVersionId = ''
    rollbackForm.approvalId = ''
    rollbackForm.reason = ''
    rollbackForm.expectedVersion = detail.value.version
  }
}

function closeAction() {
  if (!submitting.value) action.value = undefined
}

function parseObject(label: string, source: string): Record<string, unknown> {
  let parsed: unknown
  try {
    parsed = JSON.parse(source)
  } catch {
    throw new Error(`${label}必须是合法 JSON 对象。`)
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new Error(`${label}必须是 JSON 对象，不能是数组或基础值。`)
  }
  return parsed as Record<string, unknown>
}

function createRequest(): CreateProviderConfigVersionRequest {
  const values = parseObject('公开配置', createForm.values)
  const secretRefs = parseObject('秘密引用', createForm.secretRefs)
  const secretLikeKey = Object.keys(values).find((key) => /(secret|token|password|access.?key|appcode|private.?key)/i.test(key))
  if (secretLikeKey) throw new Error(`公开配置包含疑似秘密字段“${secretLikeKey}”，请改用秘密引用。`)
  for (const [key, value] of Object.entries(secretRefs)) {
    if (typeof value !== 'string' || !/^(vault|kms):\/\//i.test(value)) {
      throw new Error(`秘密引用“${key}”只能填写 vault:// 或 kms:// 引用。`)
    }
  }
  const environment = createForm.environment.trim().toUpperCase()
  if (!['DEV', 'TEST', 'STAGING', 'PROD'].includes(environment)) {
    throw new Error('环境只能是 DEV、TEST、STAGING 或 PROD。')
  }
  return {
    environment,
    values: values as CreateProviderConfigVersionRequest['values'],
    secretRefs: secretRefs as NonNullable<CreateProviderConfigVersionRequest['secretRefs']>,
    remark: createForm.remark.trim() || undefined,
  }
}

function applyOutcome(outcome: ProviderConfigResource | { status: string }) {
  if ('provider' in outcome) detail.value = outcome
}

async function submitAction() {
  if (!action.value || !props.provider || !detail.value || !writable.value) return
  formError.value = undefined
  submitting.value = true
  const currentAction = action.value
  try {
    let outcome: ProviderConfigResource | { status: string }
    if (currentAction === 'create') {
      outcome = await adminProviderConfigsApi.createVersion(props.provider, createRequest())
    } else if (currentAction === 'test') {
      const versionId = testForm.versionId.trim()
      if (!versionId) throw new Error('必须选择待测试的草稿版本。')
      const body: TestProviderConfigRequest = { versionId, testRecipient: testForm.testRecipient.trim() || undefined }
      outcome = await adminProviderConfigsApi.testConnection(props.provider, body)
    } else if (currentAction === 'activate') {
      const body: ActivateProviderConfigRequest = {
        versionId: activateForm.versionId.trim(), approvalId: activateForm.approvalId.trim(), expectedVersion: activateForm.expectedVersion,
      }
      if (!body.versionId || !body.approvalId) throw new Error('版本编号和审批单编号均不能为空。')
      outcome = await adminProviderConfigsApi.activate(props.provider, body)
    } else {
      const body: RollbackProviderConfigRequest = {
        targetVersionId: rollbackForm.targetVersionId.trim(), approvalId: rollbackForm.approvalId.trim(),
        reason: rollbackForm.reason.trim(), expectedVersion: rollbackForm.expectedVersion,
      }
      if (!body.targetVersionId || !body.approvalId || !body.reason) throw new Error('目标版本、审批单编号和回滚原因均不能为空。')
      outcome = await adminProviderConfigsApi.rollback(props.provider, body)
    }
    applyOutcome(outcome)
    notice.value = {
      tone: currentAction === 'activate' || currentAction === 'rollback' ? 'warning' : 'success',
      title: `${actionTitle.value}已提交`,
      detail: currentAction === 'activate' || currentAction === 'rollback'
        ? '服务端将校验双人审批、连接测试和乐观锁版本；结果已重新读取。'
        : '写请求未自动重试，服务端返回结果已同步到当前页面。',
    }
    action.value = undefined
    if (!('provider' in outcome)) await load('refresh')
  } catch (caught) {
    if (caught instanceof Error && !isApiRequestError(caught)) {
      formError.value = caught.message
    } else if (isApiRequestError(caught)) {
      if (caught.status === 401) {
        adminSession.clear()
        action.value = undefined
        notice.value = { tone: 'danger', title: '管理员会话已失效', detail: '请重新登录后再继续配置。', requestId: caught.requestId }
      } else if (caught.status === 403) {
        writeForbidden.value = true
        action.value = undefined
        notice.value = { tone: 'danger', title: '配置写权限已收回', detail: caught.message, requestId: caught.requestId }
      } else if (caught.status === 409) {
        stale.value = true
        action.value = undefined
        notice.value = { tone: 'warning', title: '配置版本已经变化', detail: '已阻止覆盖服务端新版本，请刷新后重新确认。', requestId: caught.requestId }
      } else {
        formError.value = caught.status === 422 ? `当前状态不允许该操作：${caught.message}` : caught.message
      }
    } else {
      formError.value = '网络异常或服务暂时不可用；写操作没有自动重试，请人工确认后再试。'
    }
  } finally {
    submitting.value = false
  }
}

function handleModalKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); closeAction(); return }
  if (event.key !== 'Tab' || !modalElement.value) return
  const focusable = [...modalElement.value.querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])')]
  if (!focusable.length) { event.preventDefault(); modalElement.value.focus(); return }
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}

watch(action, async (value, previous) => {
  if (value) {
    if (!previous) previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    await nextTick()
    modalElement.value?.querySelector<HTMLElement>('input, select, textarea, button:not([disabled])')?.focus()
  } else if (previous) {
    previousFocus?.focus()
    previousFocus = null
  }
})

function updateOnline() {
  online.value = navigator.onLine
  if (!online.value) request?.abort()
  else if (!page.value && !detail.value) void load()
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
  <article class="page provider-page">
    <header class="page-heading">
      <div><div class="eyebrow">配置中心 · 版本化与秘密隔离</div><h1>{{ meta?.name ?? '供应商配置中心' }}</h1><p>{{ meta?.summary ?? '集中查看短信、存储、支付、企业出款与实名认证配置；秘密只展示配置状态和脱敏引用。' }}</p></div>
      <div class="page-actions"><RouterLink v-if="provider" class="ghost-button provider-back" to="/system/providers">返回配置中心</RouterLink><button class="ghost-button" :disabled="refreshing || !online || forbidden" @click="load('refresh')">{{ refreshing ? '刷新中…' : '刷新' }}</button></div>
    </header>

    <StatusNotice v-if="!online && (page || detail)" tone="warning" title="当前设备离线" detail="正在显示最近一次成功读取的配置快照；离线时禁止任何写操作。" />
    <StatusNotice v-if="forbidden" tone="danger" title="无配置管理权限" :detail="error?.message ?? '当前会话不能读取供应商配置。'" :request-id="error?.requestId" />
    <StatusNotice v-if="notice" :tone="notice.tone" :title="notice.title" :detail="notice.detail" :request-id="notice.requestId" />
    <StatusNotice v-if="stale" tone="warning" title="当前配置需要刷新" detail="检测到服务端版本冲突，所有写操作已暂停。刷新并核对最新状态后才能继续。" />

    <section v-if="loading" class="provider-grid" aria-label="正在加载供应商配置"><div v-for="index in (provider ? 3 : 4)" :key="index" class="card skeleton skeleton-tall" /></section>
    <section v-else-if="error && !(page || detail)" class="card error-state" aria-live="polite"><div class="avatar centered-mark">!</div><h2>{{ forbidden ? '无法访问供应商配置' : '供应商配置暂时无法加载' }}</h2><p>{{ error.message }}</p><div><button v-if="!forbidden" class="primary-button" :disabled="!online" @click="load()">重新加载</button></div></section>

    <template v-else-if="!provider && page">
      <section class="provider-grid">
        <RouterLink v-for="code in (['sms','storage','payment','payout','identity'] as ProviderCode[])" :key="code" class="card provider-card" :to="providerMeta[code].route">
          <div class="provider-card-top"><span class="provider-mark">{{ providerMeta[code].mark }}</span><span class="status-chip" :data-status="resource(code)?.connectionStatus">{{ statusLabel(resource(code)?.connectionStatus) }}</span></div>
          <h2>{{ providerMeta[code].name }}</h2><p>{{ providerMeta[code].summary }}</p>
          <dl class="provider-facts"><dt>当前环境</dt><dd>{{ resource(code)?.environment ?? '尚未配置' }}</dd><dt>激活版本</dt><dd>{{ resource(code)?.activeVersion ?? '无' }}</dd><dt>草稿版本</dt><dd>{{ resource(code)?.draftVersion ?? '无' }}</dd></dl>
          <span class="provider-link">查看并管理 →</span>
        </RouterLink>
      </section>
      <StatusNotice tone="warning" title="激活受严格门禁保护" detail="新版本必须完成字段校验、真实连接测试和不同管理员的双人审批，才能替换当前运行版本。" />
    </template>

    <template v-else-if="provider && detail">
      <section class="metric-grid provider-metrics"><div class="metric"><span>环境</span><strong class="metric-value-compact">{{ detail.environment }}</strong></div><div class="metric"><span>当前激活版本</span><strong class="metric-value-compact">{{ detail.activeVersion ?? '尚无' }}</strong></div><div class="metric"><span>草稿版本</span><strong class="metric-value-compact">{{ detail.draftVersion ?? '尚无' }}</strong></div><div class="metric"><span>服务端版本</span><strong>v{{ detail.version }}</strong></div></section>
      <section class="content-grid">
        <div class="card"><div class="section-heading"><div><h2>秘密引用状态</h2><p>原文不会从服务端回显，也不会进入页面日志。</p></div><span class="tag tag-success">已配置 {{ secretCount }} 项</span></div><div v-if="detail.configuredSecrets?.length" class="secret-list"><div v-for="secret in detail.configuredSecrets" :key="secret.key" class="secret-row"><div><strong>{{ secret.key }}</strong><small>{{ secret.secretRefMasked ?? '引用信息已完全隐藏' }}</small></div><span class="status-chip" :data-status="secret.configured ? 'ACTIVE' : 'FAILED'">{{ secret.configured ? '已配置' : '未配置' }}</span></div></div><div v-else class="empty-state compact-empty"><h2>尚无秘密引用元数据</h2><p>创建配置版本时只能选择 Vault/KMS SecretRef，不能粘贴明文凭据。</p></div></div>
        <aside class="card account-summary"><div class="section-heading"><div><h2>连接与版本门禁</h2><p>连接失败时禁止激活。</p></div><span v-if="writeForbidden" class="tag tag-warning">写权限已收回</span></div><dl class="facts"><dt>连接状态</dt><dd><span class="status-chip" :data-status="detail.connectionStatus">{{ statusLabel(detail.connectionStatus) }}</span></dd><dt>最近测试</dt><dd>{{ formatDate(detail.lastTestAt) }}</dd><dt>乐观锁版本</dt><dd>v{{ detail.version }}</dd></dl><div class="provider-actions"><button class="primary-button" :disabled="!writable" @click="openAction('create')">创建新版本</button><button class="secondary-button" :disabled="!writable || !canTest" @click="openAction('test')">连接测试</button><button class="secondary-button" :disabled="!writable || !canActivate" @click="openAction('activate')">审批并激活</button><button class="danger-button" :disabled="!writable || !canRollback" @click="openAction('rollback')">回滚版本</button></div><p class="field-help provider-action-help">所有写入均携带幂等键且不自动重试。激活和回滚必须填写由另一名管理员批准的审批单编号。</p></aside>
      </section>
    </template>

    <ProviderCertificatePanel v-if="provider === 'payout' && detail" />

    <div v-if="action && detail && provider" class="modal-backdrop" @click.self="closeAction">
      <section ref="modalElement" class="modal provider-modal" role="dialog" aria-modal="true" aria-labelledby="provider-action-title" tabindex="-1" @keydown="handleModalKeydown">
        <header class="modal-header"><div><h2 id="provider-action-title">{{ actionTitle }}</h2><p>{{ meta?.name }} · {{ detail.environment }}；服务端版本 v{{ detail.version }}。写操作不会自动重试。</p></div><button class="icon-button" aria-label="关闭" :disabled="submitting" @click="closeAction">×</button></header>
        <form class="form-stack" novalidate @submit.prevent="submitAction">
          <template v-if="action === 'create'">
            <label class="field"><span class="field-label">运行环境</span><select v-model="createForm.environment" class="input"><option>DEV</option><option>TEST</option><option>STAGING</option><option>PROD</option></select><span class="field-help">生产环境仍需通过服务端双人审批与连接测试门禁。</span></label>
            <label class="field"><span class="field-label">公开配置（JSON 对象）</span><textarea v-model="createForm.values" class="input code-input" spellcheck="false" required /><span class="field-help">只能填写非秘密配置；疑似密码、令牌、AccessKey 或 APPCODE 字段会在提交前被拒绝。</span></label>
            <label class="field"><span class="field-label">秘密引用（JSON 对象）</span><textarea v-model="createForm.secretRefs" class="input code-input" spellcheck="false" required /><span class="field-help">值只能是 vault:// 或 kms:// 引用，禁止粘贴明文凭据。</span></label>
            <label class="field"><span class="field-label">变更说明（可选）</span><textarea v-model="createForm.remark" class="input" maxlength="500" placeholder="说明本次配置变更的业务目的" /></label>
          </template>
          <template v-else-if="action === 'test'">
            <label class="field"><span class="field-label">待测试版本</span><input v-model="testForm.versionId" class="input" maxlength="128" required /><span class="field-help">默认使用当前草稿版本；测试结果由服务端真实连接器产生。</span></label>
            <label class="field"><span class="field-label">测试接收方（可选）</span><input v-model="testForm.testRecipient" class="input" maxlength="256" placeholder="短信接收号码、存储探针或实名测试标识" /></label>
          </template>
          <template v-else-if="action === 'activate'">
            <label class="field"><span class="field-label">待激活版本</span><input v-model="activateForm.versionId" class="input" maxlength="128" required /></label>
            <label class="field"><span class="field-label">审批单编号</span><input v-model="activateForm.approvalId" class="input" maxlength="128" required placeholder="必须由另一名管理员审批" /></label>
            <label class="field"><span class="field-label">乐观锁版本</span><input v-model.number="activateForm.expectedVersion" class="input" type="number" min="0" step="1" required readonly /></label>
            <StatusNotice tone="warning" title="激活会替换运行配置" detail="只有字段校验、真实连接测试和双人审批全部通过，服务端才会激活该版本。" />
          </template>
          <template v-else>
            <label class="field"><span class="field-label">目标历史版本</span><input v-model="rollbackForm.targetVersionId" class="input" maxlength="128" required placeholder="填写要恢复的版本编号" /></label>
            <label class="field"><span class="field-label">审批单编号</span><input v-model="rollbackForm.approvalId" class="input" maxlength="128" required placeholder="必须由另一名管理员审批" /></label>
            <label class="field"><span class="field-label">回滚原因</span><textarea v-model="rollbackForm.reason" class="input" maxlength="1000" required placeholder="说明故障、影响和恢复目标" /></label>
            <label class="field"><span class="field-label">乐观锁版本</span><input v-model.number="rollbackForm.expectedVersion" class="input" type="number" min="0" step="1" required readonly /></label>
            <StatusNotice tone="danger" title="回滚是高风险操作" detail="服务端将验证目标历史版本、审批人分离和当前数据版本；冲突时不会覆盖新状态。" />
          </template>
          <p v-if="formError" class="field-error provider-form-error" role="alert">{{ formError }}</p>
          <div class="modal-footer"><button type="button" class="ghost-button" :disabled="submitting" @click="closeAction">取消</button><button :class="action === 'rollback' ? 'danger-button' : 'primary-button'" :disabled="!writable">{{ submitting ? '提交中…' : `确认${actionTitle}` }}</button></div>
        </form>
      </section>
    </div>
  </article>
</template>

<style src="../provider-config.css"></style>
