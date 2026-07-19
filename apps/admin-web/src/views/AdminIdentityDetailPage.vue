<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusNotice from '../components/StatusNotice.vue'
import {
  adminIdentitiesApi,
  adminSession,
  isApiRequestError,
  type AdminIdentityCommandOutcome,
  type AdminIdentityResource,
} from '../services'

type IdentityAction = 'review' | 'media' | 'freeze'
const route = useRoute()
const router = useRouter()
const identity = ref<AdminIdentityResource>()
const loading = ref(true)
const submitting = ref(false)
const online = ref(navigator.onLine)
const forbidden = ref(false)
const missing = ref(false)
const stale = ref(false)
const error = ref<string>()
const notice = ref<{ tone: 'success' | 'warning' | 'danger'; title: string; detail: string }>()
const action = ref<IdentityAction>()
const activeTab = ref('基本信息')
const tabs = ['基本信息', '活体与人证', '敏感媒体', '风险', '复核记录', '访问审计']
const form = reactive({ decision: 'APPROVE' as 'APPROVE' | 'REJECT' | 'ESCALATE', reason: '', purpose: '', ttlSeconds: 60 })
let activeRequest: AbortController | undefined

const userId = computed(() => String(route.params.id ?? ''))
const canReview = computed(() => adminSession.hasPermission('identity.review'))
const canViewMedia = computed(() => adminSession.hasPermission('identity.media.view'))
const canFreeze = computed(() => adminSession.hasPermission('identity.freeze'))
const canReviewStatus = computed(() => ['LIVENESS_PENDING', 'PROVIDER_PROCESSING', 'MANUAL_REVIEW'].includes(identity.value?.status ?? ''))
const writable = computed(() => online.value && !submitting.value && !stale.value)

const statusLabels: Record<string, string> = {
  SESSION_CREATED: '待活体检测', LIVENESS_PENDING: '活体检测中', PROVIDER_PROCESSING: '核验中',
  MANUAL_REVIEW: '待人工复核', COMPLETED: '已通过', REJECTED: '未通过', FAILED: '认证失败', EXPIRED: '已过期',
}
const failureLabels: Record<string, string> = {
  FACE_NOT_MATCH: '人脸与证件不一致', LIVENESS_FAILED: '未通过活体检测',
  IDENTITY_MISMATCH: '身份信息不一致', PROVIDER_TIMEOUT: '认证服务响应超时',
}
function statusLabel(value?: string) { return value ? (statusLabels[value] ?? '状态确认中') : '暂无' }
function failureLabel(value?: string) { return value ? (failureLabels[value] ?? '需要人工核查') : '暂无' }
function formatDate(value?: string) { return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'long', timeStyle: 'short' }).format(new Date(value)) : '暂无' }
function businessError(caught: unknown, fallback: string) {
  if (!isApiRequestError(caught)) return fallback
  if (caught.status === 0) return '网络连接不稳定，请检查网络后重试'
  if (caught.status === 403) return '当前账号没有执行该操作的权限'
  if (caught.status === 404) return '认证记录不存在或已不可访问'
  if (caught.status === 409) return '认证数据已经变化，请刷新后重新确认'
  if (caught.status === 422) return caught.message || '当前状态不允许执行该操作'
  if (caught.status === 429) return caught.retryAfter ? `操作过于频繁，请在${caught.retryAfter}秒后重试` : '操作过于频繁，请稍后重试'
  return fallback
}

async function load() {
  if (!online.value) { loading.value = false; error.value = '当前设备离线，请恢复网络后重试'; return }
  activeRequest?.abort()
  const request = new AbortController()
  activeRequest = request
  loading.value = true
  error.value = undefined
  missing.value = false
  try {
    identity.value = await adminIdentitiesApi.detail(userId.value, { signal: request.signal })
    forbidden.value = false
    stale.value = false
  } catch (caught) {
    if (isApiRequestError(caught) && caught.code === 'REQUEST_ABORTED') return
    if (isApiRequestError(caught) && caught.status === 401) {
      adminSession.clear()
      await router.replace({ path: '/auth/login', query: { redirect: route.fullPath } })
      return
    }
    forbidden.value = isApiRequestError(caught) && caught.status === 403
    missing.value = isApiRequestError(caught) && caught.status === 404
    error.value = businessError(caught, '认证详情暂时无法加载，请稍后重试')
  } finally {
    if (activeRequest === request) activeRequest = undefined
    loading.value = false
  }
}

function openAction(next: IdentityAction) {
  action.value = next
  notice.value = undefined
  form.decision = 'APPROVE'
  form.reason = ''
  form.purpose = ''
  form.ttlSeconds = 60
}
function closeAction() { if (!submitting.value) action.value = undefined }
function applyOutcome(outcome: AdminIdentityCommandOutcome) {
  if ('userId' in outcome || 'provider' in outcome || 'failureCode' in outcome) identity.value = outcome as AdminIdentityResource
}
function openPreview(outcome: AdminIdentityCommandOutcome) {
  if (!('businessNo' in outcome) || outcome.status !== 'PREVIEW_READY') return false
  const businessNo = outcome.businessNo
  if (typeof businessNo !== 'string' || businessNo.length === 0) return false
  try {
    const target = new URL(businessNo)
    if (target.protocol !== 'https:') return false
    window.open(target.toString(), '_blank', 'noopener,noreferrer')
    return true
  } catch { return false }
}

async function submitAction() {
  if (!action.value || !identity.value || !writable.value) return
  const currentAction = action.value
  const reason = form.reason.trim()
  const purpose = form.purpose.trim()
  if (currentAction !== 'media' && !reason) { notice.value = { tone: 'danger', title: '请填写操作原因', detail: '原因不能为空，并将用于复核记录。' }; return }
  if (currentAction === 'media' && !purpose) { notice.value = { tone: 'danger', title: '请填写查看用途', detail: '敏感资料只允许按明确业务目的短时查看。' }; return }
  submitting.value = true
  notice.value = undefined
  try {
    let outcome: AdminIdentityCommandOutcome
    if (currentAction === 'review') outcome = await adminIdentitiesApi.review(identity.value.id, {
      decision: form.decision, reason, expectedVersion: identity.value.version, evidenceIds: [],
    })
    else if (currentAction === 'media') outcome = await adminIdentitiesApi.mediaAccess(userId.value, {
      purpose, ttlSeconds: form.ttlSeconds,
    })
    else outcome = await adminIdentitiesApi.freeze(userId.value, { reason, expectedVersion: identity.value.version })
    applyOutcome(outcome)
    if (currentAction === 'media') {
      notice.value = openPreview(outcome)
        ? { tone: 'success', title: '已打开短时预览', detail: '预览窗口关闭或链接到期后需要重新申请。' }
        : { tone: 'danger', title: '预览未打开', detail: '浏览器阻止了新窗口或返回地址不可用，请允许弹窗后重试。' }
    } else if ('businessNo' in outcome && outcome.status === 'PENDING_APPROVAL') {
      notice.value = { tone: 'warning', title: '冻结申请已提交', detail: '需要另一名具备权限的管理员复核后才会生效。' }
    } else {
      notice.value = { tone: 'success', title: currentAction === 'review' ? '复核结论已提交' : '实名冻结已完成', detail: '服务端状态已更新。' }
    }
    action.value = undefined
    if (!('userId' in outcome)) await load()
  } catch (caught) {
    if (isApiRequestError(caught) && caught.status === 401) {
      adminSession.clear()
      await router.replace({ path: '/auth/login', query: { redirect: route.fullPath } })
      return
    }
    if (isApiRequestError(caught) && caught.status === 409) stale.value = true
    if (isApiRequestError(caught) && caught.status === 403) action.value = undefined
    notice.value = { tone: 'danger', title: '操作未完成', detail: businessError(caught, '服务暂时不可用，请稍后重试') }
  } finally { submitting.value = false }
}

function updateOnline() { online.value = navigator.onLine; if (!online.value) activeRequest?.abort(); else if (!identity.value) void load() }
watch(userId, () => void load())
onMounted(() => { window.addEventListener('online', updateOnline); window.addEventListener('offline', updateOnline); void load() })
onBeforeUnmount(() => { activeRequest?.abort(); window.removeEventListener('online', updateOnline); window.removeEventListener('offline', updateOnline) })
</script>

<template>
  <article class="page identity-detail-page">
    <header class="page-heading"><div><div class="eyebrow">身份治理 · 用户 {{ userId }}</div><h1>实名认证详情</h1><p>查看认证结果并按权限执行人工复核和敏感访问。</p></div><div class="page-actions"><RouterLink class="ghost-button table-link" :to="{ path: '/identity', query: route.query }">返回列表</RouterLink><button class="ghost-button" :disabled="loading || !online" @click="load">刷新</button></div></header>
    <StatusNotice v-if="!online && identity" tone="warning" title="当前设备离线" detail="正在显示最近一次成功读取的详情；写操作已暂停。" />
    <StatusNotice v-if="notice" :tone="notice.tone" :title="notice.title" :detail="notice.detail" />
    <StatusNotice v-if="stale" tone="warning" title="数据已经变化" detail="所有写操作已暂停，请刷新并核对最新状态后继续。" />
    <section v-if="loading" class="card skeleton-stack"><div class="skeleton skeleton-tall"/><div class="skeleton"/><div class="skeleton"/></section>
    <section v-else-if="error && !identity" class="card error-state"><div class="avatar centered-mark">!</div><h2>{{ missing ? '认证记录不存在' : forbidden ? '无权查看认证详情' : '认证详情暂时无法加载' }}</h2><p>{{ error }}</p><button v-if="!missing && !forbidden" class="primary-button" :disabled="!online" @click="load">重新加载</button></section>
    <template v-else-if="identity && !forbidden">
      <section class="security-hero"><div class="card user-profile-hero"><div class="summary-top"><span class="avatar user-avatar">认</span><div><strong>用户 {{ identity.userId || userId }}</strong><small>认证记录 {{ identity.id }}</small></div></div><div class="button-row"><span class="status-chip" :data-status="identity.status">{{ statusLabel(identity.status) }}</span><span class="tag">数据版本 v{{ identity.version }}</span></div></div><div class="card account-summary"><strong>认证有效期</strong><span>{{ formatDate(identity.expiresAt) }}</span><small>{{ failureLabel(identity.failureCode) }}</small></div></section>
      <section class="card"><div class="section-heading"><div><h2>复核操作</h2><p>所有操作使用当前数据版本；敏感资料仅允许按用途短时查看。</p></div></div><div class="button-row"><button v-if="canReview && canReviewStatus" class="primary-button" :disabled="!writable" @click="openAction('review')">人工复核</button><button v-if="canViewMedia" class="secondary-button" :disabled="!writable" @click="openAction('media')">查看敏感资料</button><button v-if="canFreeze" class="danger-button" :disabled="!writable" @click="openAction('freeze')">申请冻结实名</button><span v-if="!canReview && !canViewMedia && !canFreeze" class="field-help">当前会话仅有读取权限。</span></div></section>
      <nav class="detail-tabs" aria-label="实名详情分区"><button v-for="tab in tabs" :key="tab" :class="{ active: activeTab === tab }" @click="activeTab = tab">{{ tab }}</button></nav>
      <section class="card detail-panel">
        <dl v-if="activeTab === '基本信息'" class="facts"><dt>用户编号</dt><dd>{{ identity.userId || userId }}</dd><dt>认证状态</dt><dd>{{ statusLabel(identity.status) }}</dd><dt>认证服务</dt><dd>{{ identity.provider ? '实名认证服务' : '暂未分配' }}</dd><dt>失败原因</dt><dd>{{ failureLabel(identity.failureCode) }}</dd><dt>有效期</dt><dd>{{ formatDate(identity.expiresAt) }}</dd><dt>数据版本</dt><dd>v{{ identity.version }}</dd></dl>
        <dl v-else-if="activeTab === '活体与人证'" class="facts"><dt>活体检测</dt><dd>{{ identity.livenessUrl ? '已生成检测入口' : '暂无检测入口' }}</dd><dt>核验结果</dt><dd>{{ statusLabel(identity.status) }}</dd><dt>说明</dt><dd>页面不会展示第三方原始地址或内部失败代码。</dd></dl>
        <div v-else-if="activeTab === '敏感媒体'" class="empty-state compact-empty"><div class="avatar centered-mark">锁</div><h2>敏感资料默认不展示</h2><p>具备权限的复核人员可填写用途，申请短时预览；页面不会保存或导出原图。</p></div>
        <div v-else class="empty-state compact-empty"><div class="avatar centered-mark">0</div><h2>暂无可展示记录</h2><p>当前接口尚未返回“{{ activeTab }}”数据，页面不会用占位内容伪造业务事实。</p></div>
      </section>
    </template>

    <div v-if="action && identity" class="modal-backdrop" @click.self="closeAction"><section class="modal" role="dialog" aria-modal="true" aria-labelledby="identity-action-title"><header class="modal-header"><div><h2 id="identity-action-title">{{ action === 'review' ? '人工复核' : action === 'media' ? '查看敏感资料' : '申请冻结实名' }}</h2><p>目标用户 {{ userId }}，当前状态 {{ statusLabel(identity.status) }}。</p></div><button class="icon-button" aria-label="关闭" :disabled="submitting" @click="closeAction">×</button></header><form class="form-stack" @submit.prevent="submitAction">
      <label v-if="action === 'review'" class="field"><span class="field-label">复核结论</span><select v-model="form.decision" class="input"><option value="APPROVE">通过认证</option><option value="REJECT">不通过</option><option value="ESCALATE">升级复核</option></select></label>
      <label v-if="action !== 'media'" class="field"><span class="field-label">操作原因</span><textarea v-model="form.reason" class="input" maxlength="2000" required placeholder="请说明复核依据或冻结原因"/><span class="field-help">请勿填写与本次处理无关的个人敏感信息。</span></label>
      <template v-else><label class="field"><span class="field-label">查看用途</span><textarea v-model="form.purpose" class="input" maxlength="2000" required placeholder="请说明本次查看的业务目的"/></label><label class="field"><span class="field-label">预览时长</span><select v-model.number="form.ttlSeconds" class="input"><option :value="30">30 秒</option><option :value="60">60 秒</option><option :value="120">2 分钟</option></select></label></template>
      <StatusNotice :tone="action === 'media' ? 'warning' : 'danger'" :title="action === 'media' ? '敏感访问确认' : action === 'freeze' ? '双人审批确认' : '复核结论确认'" :detail="action === 'media' ? '预览会在新窗口短时打开，并记录查看用途。' : action === 'freeze' ? '首位管理员提交申请，必须由另一位管理员复核后才会冻结。' : '提交后将更新认证状态，请确认结论和原因无误。'" />
      <div class="modal-footer"><button type="button" class="ghost-button" :disabled="submitting" @click="closeAction">取消</button><button :class="action === 'media' ? 'primary-button' : 'danger-button'" :disabled="!writable">{{ submitting ? '提交中…' : '确认提交' }}</button></div>
    </form></section></div>
  </article>
</template>
