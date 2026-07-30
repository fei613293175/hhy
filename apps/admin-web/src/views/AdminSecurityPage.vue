<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import StatusNotice from '../components/StatusNotice.vue'
import { adminSecurityApi, adminSession, isApiRequestError, type AdminMfaEnrollmentResource, type AdminSelfSecurityResource } from '../services'

type Modal = 'password' | 'enroll' | 'confirm' | 'disable' | 'logout'
const router = useRouter()
const data = ref<AdminSelfSecurityResource>()
const loading = ref(true)
const refreshing = ref(false)
const submitting = ref(false)
const modal = ref<Modal>()
const modalElement = ref<HTMLElement>()
let previousFocus: HTMLElement | null = null
const enrollment = ref<AdminMfaEnrollmentResource>()
const notice = ref<{ tone: 'success' | 'warning' | 'danger'; title: string; detail?: string; requestId?: string }>()
const forbidden = ref(false)
const writeForbidden = ref(false)
const online = ref(navigator.onLine)
const cooldownSeconds = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | undefined
let activeWrite: AbortController | undefined
const updateOnline = () => {
  online.value = navigator.onLine
  if (!online.value) {
    activeWrite?.abort()
    activeWrite=undefined
    submitting.value=false
    data.value=undefined
    modal.value=undefined
    scrubSensitive()
  }
  else if (!data.value) void load()
}
window.addEventListener('online', updateOnline)
window.addEventListener('offline', updateOnline)
onBeforeUnmount(() => { window.removeEventListener('online', updateOnline); window.removeEventListener('offline', updateOnline); if(cooldownTimer) clearInterval(cooldownTimer); activeWrite?.abort(); scrubSensitive(); previousFocus?.focus() })

const passwordForm = reactive({ currentPassword: '', newPassword: '', mfaCode: '' })
const confirmCode = ref('')
const disableForm = reactive({ code: '', reason: '' })
const fieldErrors = reactive<Record<string, string>>({})
const canWrite = computed(() => !writeForbidden.value && adminSession.hasPermission('admin.self.security'))
const canPost = computed(() => online.value && cooldownSeconds.value===0 && !submitting.value)
const canSecurityPost = computed(() => canPost.value && canWrite.value)
const riskScore = computed(() => data.value?.mfaEnabled ? 92 : 64)

function formatDate(value?: string) { return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '暂无记录' }
function clearFieldErrors() { Object.keys(fieldErrors).forEach((key) => { delete fieldErrors[key] }) }
function clearSensitive() { passwordForm.currentPassword=''; passwordForm.newPassword=''; passwordForm.mfaCode=''; confirmCode.value=''; disableForm.code=''; disableForm.reason=''; clearFieldErrors() }
function scrubSensitive() { clearSensitive(); enrollment.value=undefined }
function closeModal() { if (!submitting.value) { modal.value=undefined; clearSensitive(); enrollment.value=undefined } }
function beginWrite(requiresSecurityPermission=true) {
  if (!(requiresSecurityPermission ? canSecurityPost.value : canPost.value)) return
  submitting.value=true
  activeWrite=new AbortController()
  return activeWrite
}
function finishWrite(controller: AbortController) {
  if(activeWrite===controller) activeWrite=undefined
  submitting.value=false
}
function startCooldown(seconds?: number) {
  cooldownSeconds.value=Math.max(1, seconds ?? 1)
  if(cooldownTimer) clearInterval(cooldownTimer)
  cooldownTimer=setInterval(() => {
    cooldownSeconds.value=Math.max(0, cooldownSeconds.value-1)
    if(cooldownSeconds.value===0 && cooldownTimer) { clearInterval(cooldownTimer); cooldownTimer=undefined }
  }, 1000)
}
watch(modal, async (current, previous) => {
  if (current) {
    if (!previous) previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    await nextTick()
    const target = modalElement.value?.querySelector<HTMLElement>('input, textarea, button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])')
    ;(target ?? modalElement.value)?.focus()
  } else if (previous) {
    previousFocus?.focus()
    previousFocus = null
  }
})
function handleModalKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); closeModal(); return }
  if (event.key !== 'Tab' || !modalElement.value) return
  const focusable = [...modalElement.value.querySelectorAll<HTMLElement>('a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])')]
  if (focusable.length === 0) { event.preventDefault(); modalElement.value.focus(); return }
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}
async function failure(caught: unknown, fallback: string) {
  if (isApiRequestError(caught)) {
    clearFieldErrors()
    caught.details.forEach((detail) => { if (detail.field) fieldErrors[detail.field]=detail.message })
    if (caught.status === 401) { adminSession.clear(); void router.replace({ path:'/auth/login', query:{ redirect:'/me/security' } }); return }
    if (caught.status === 403) { writeForbidden.value=true; modal.value=undefined; enrollment.value=undefined; clearSensitive() }
    if (caught.status === 429) startCooldown(caught.retryAfter)
    if (caught.status === 409) {
      try {
        const latest=await adminSecurityApi.getSecurity()
        if(!online.value) return
        data.value=latest
        notice.value={tone:'warning',title:'状态冲突，已刷新最新状态',detail:caught.message,requestId:caught.requestId}
      } catch(refreshError) {
        data.value=undefined
        if(isApiRequestError(refreshError) && refreshError.status===429) startCooldown(refreshError.retryAfter)
        notice.value={tone:'danger',title:'状态冲突且刷新失败',detail:isApiRequestError(refreshError)?refreshError.message:'网络连接失败，请恢复网络后重试。',requestId:isApiRequestError(refreshError)?refreshError.requestId:caught.requestId}
      }
      return
    }
    notice.value={ tone: caught.status===409 || caught.status===429 ? 'warning':'danger', title: caught.status===403 ? '权限已变化' : fallback, detail:caught.message, requestId:caught.requestId }
  } else notice.value={ tone:'danger', title:fallback, detail:'网络连接失败，请检查网络后重试。' }
}

async function load(refresh=false) {
  if (!online.value) { loading.value=false; notice.value={tone:'warning',title:'当前处于离线状态',detail:'安全数据不使用持久缓存，请恢复网络后重新加载。'}; return }
  refresh ? refreshing.value=true : loading.value=true
  forbidden.value=false
  if (!refresh) notice.value=undefined
  try { const latest=await adminSecurityApi.getSecurity(); if(online.value) data.value=latest; if(refresh&&online.value) notice.value={tone:'success',title:'安全状态已刷新'} }
  catch(caught){ forbidden.value=isApiRequestError(caught)&&caught.status===403; await failure(caught,'无法加载安全状态') }
  finally{ loading.value=false; refreshing.value=false }
}

async function startEnrollment() {
  const controller=beginWrite(); if(!controller) return
  notice.value=undefined; clearFieldErrors()
  try { const latest=await adminSecurityApi.enrollMfa({signal:controller.signal}); if(online.value){ enrollment.value=latest; modal.value='confirm' } }
  catch(caught){ if(online.value) await failure(caught,'无法开始 MFA 绑定') }
  finally{ finishWrite(controller) }
}
async function confirmEnrollment() {
  if(!enrollment.value || !/^\d{4,10}$/.test(confirmCode.value)) return
  const controller=beginWrite(); if(!controller) return
  clearFieldErrors()
  try { const latest=await adminSecurityApi.confirmMfa({enrollmentId:enrollment.value.enrollmentId,code:confirmCode.value},{signal:controller.signal}); if(online.value){ data.value=latest; notice.value={tone:'success',title:'MFA 已启用',detail:'后续登录将要求二次验证。'}; modal.value=undefined; scrubSensitive() } }
  catch(caught){ confirmCode.value=''; if(online.value) await failure(caught,'MFA 验证码未通过') }
  finally{finishWrite(controller)}
}
async function changePassword() {
  if(!passwordForm.currentPassword || passwordForm.newPassword.length<8 || !passwordForm.mfaCode) return
  const controller=beginWrite(); if(!controller) return
  clearFieldErrors()
  try { await adminSecurityApi.changePassword({...passwordForm},{signal:controller.signal}); if(online.value){ clearSensitive(); modal.value=undefined; await router.replace('/auth/login') } }
  catch(caught){ passwordForm.currentPassword=''; passwordForm.newPassword=''; passwordForm.mfaCode=''; if(online.value) await failure(caught,'密码修改失败') }
  finally{finishWrite(controller)}
}
async function disableMfa() {
  if(!disableForm.code || disableForm.reason.trim().length===0) return
  const controller=beginWrite(); if(!controller) return
  clearFieldErrors()
  try { const latest=await adminSecurityApi.disableMfa({code:disableForm.code,reason:disableForm.reason.trim()},{signal:controller.signal}); if(online.value){ data.value=latest; clearSensitive(); modal.value=undefined; notice.value={tone:'success',title:'MFA 已解绑',detail:'建议尽快重新绑定新的身份验证器。'} } }
  catch(caught){ disableForm.code=''; if(online.value) await failure(caught,'MFA 解绑失败') }
  finally{finishWrite(controller)}
}
async function logout() {
  const controller=beginWrite(false); if(!controller) return
  try { await adminSecurityApi.logout({reason:'管理员主动退出'},{signal:controller.signal}); if(online.value) await router.replace('/auth/login') }
  catch(caught){ if(online.value) await failure(caught,'退出失败') }
  finally{finishWrite(controller)}
}
onMounted(()=>load())
</script>

<template>
  <article class="page">
    <header class="page-heading"><div><p class="eyebrow">账号安全中心</p><h1>管理员安全设置</h1><p>查看当前安全事实，并管理仅属于您自己的登录凭证与 MFA。</p></div><div class="page-actions"><button class="ghost-button" :disabled="refreshing" @click="load(true)">{{refreshing?'刷新中…':'刷新状态'}}</button><button class="ghost-button" :disabled="!canPost" @click="modal='logout'">退出会话</button></div></header>
    <StatusNotice v-if="!online" tone="warning" title="当前处于离线状态" detail="安全写操作已全部禁用；页面不会展示过期缓存。" />
    <StatusNotice v-else-if="cooldownSeconds>0" tone="warning" title="安全写操作冷却中" :detail="`${cooldownSeconds} 秒后可重试；冷却期间所有提交操作均已禁用。`" />
    <StatusNotice v-if="notice" :tone="notice.tone" :title="notice.title" :detail="notice.detail" :request-id="notice.requestId" />
    <template v-if="loading"><section class="card skeleton-stack" aria-label="正在加载"><div class="skeleton skeleton-tall"/><div class="skeleton"/><div class="skeleton"/><div class="skeleton"/></section></template>
    <section v-else-if="forbidden" class="card error-state"><div class="brand-mark centered-mark">403</div><h2>没有查看安全设置的权限</h2><p>当前会话缺少 admin.self.read 权限。页面未展示任何缓存或敏感信息，请联系系统负责人授权。</p></section>
    <section v-else-if="!data" class="card error-state"><div class="brand-mark centered-mark">!</div><h2>安全状态暂时不可用</h2><p>请恢复网络后重新加载；如问题持续，请联系系统管理员。</p><button class="primary-button" @click="load()">重新加载</button></section>
    <template v-else>
      <div class="security-hero"><section class="card risk-card"><div class="risk-row"><div class="risk-score">{{riskScore}}</div><div class="risk-copy"><p class="eyebrow">安全评分</p><h2>{{data.mfaEnabled?'关键保护已启用':'建议启用 MFA'}}</h2><p>{{data.mfaEnabled?'管理员账号已启用二次验证，当前安全状态良好。':'当前账号仅受密码保护，绑定验证器可显著降低凭证泄露风险。'}}</p></div></div></section><section class="card account-summary"><div class="summary-top"><div class="avatar">{{data.username.slice(0,1).toUpperCase()}}</div><div><strong>{{data.username}}</strong><small>管理员 ID · {{data.adminId}}</small></div></div><span class="tag" :class="data.mfaEnabled?'tag-success':'tag-warning'">{{data.mfaEnabled?'MFA 已启用':'MFA 未启用'}}</span></section></div>
      <div class="metric-grid"><div class="metric"><span>活跃会话</span><strong>{{data.activeSessionCount}}</strong></div><div class="metric"><span>剩余恢复码</span><strong>{{data.recoveryCodesRemaining}}</strong></div><div class="metric"><span>MFA 方式</span><strong class="metric-value-compact">{{data.mfaMethods.join('、')||'未绑定'}}</strong></div><div class="metric"><span>上次登录 IP</span><strong class="metric-value-compact">{{data.lastLoginIpMasked||'暂无'}}</strong></div></div>
      <div class="content-grid"><section class="card"><div class="section-heading"><div><h2>安全操作</h2><p>写操作需要 admin.self.security 权限，并由服务端再次校验。</p></div><span v-if="!canWrite" class="tag tag-warning">只读权限</span></div><div class="security-items"><div class="security-item"><div><h3>登录密码</h3><p>上次修改：{{formatDate(data.lastPasswordChangedAt)}}</p></div><button v-if="canWrite" class="secondary-button" :disabled="!canSecurityPost" @click="modal='password'">修改密码</button></div><div class="security-item"><div><h3>多因素认证</h3><p>{{data.mfaEnabled?'已绑定 '+data.mfaMethods.join('、'):'使用 TOTP 身份验证器保护后台登录'}}</p></div><div class="button-row" v-if="canWrite"><button v-if="!data.mfaEnabled" class="secondary-button" :disabled="!canSecurityPost" @click="modal='enroll'">开始绑定</button><button v-else class="ghost-button" :disabled="!canSecurityPost" @click="modal='disable'">解绑 MFA</button></div></div></div></section><aside class="card"><div class="section-heading"><div><h2>最近安全事实</h2><p>敏感字段按冻结契约脱敏</p></div></div><dl class="facts"><dt>上次登录</dt><dd>{{formatDate(data.lastLoginAt)}}</dd><dt>上次修改密码</dt><dd>{{formatDate(data.lastPasswordChangedAt)}}</dd><dt>登录 IP</dt><dd>{{data.lastLoginIpMasked||'暂无记录'}}</dd><dt>活跃会话</dt><dd>{{data.activeSessionCount}} 个</dd><dt>恢复码</dt><dd>{{data.recoveryCodesRemaining}} 个可用</dd></dl></aside></div>
    </template>
  </article>

  <div v-if="modal" class="modal-backdrop" @click.self="closeModal"><section ref="modalElement" class="modal" role="dialog" aria-modal="true" aria-labelledby="security-modal-title" tabindex="-1" @keydown="handleModalKeydown"><header class="modal-header"><div><h2 id="security-modal-title">{{modal==='password'?'修改管理员密码':modal==='enroll'?'绑定 MFA':modal==='confirm'?'确认 MFA 绑定':modal==='disable'?'解绑 MFA':'退出当前会话'}}</h2><p>{{modal==='password'?'修改后全部管理员会话将失效，需要重新登录。':modal==='enroll'||modal==='confirm'?'验证器密钥只在本次绑定流程中短暂展示。':modal==='disable'?'解绑会降低账号安全等级，请说明原因并完成二次验证。':'退出后当前访问令牌将立即失效。'}}</p></div><button class="icon-button" aria-label="关闭" @click="closeModal">×</button></header>
    <form v-if="modal==='password'" class="form-stack" @submit.prevent="changePassword"><label class="field"><span class="field-label">当前密码</span><input v-model="passwordForm.currentPassword" type="password" class="input" autocomplete="current-password" maxlength="72" required /><span v-if="fieldErrors.currentPassword" class="field-error">{{fieldErrors.currentPassword}}</span></label><label class="field"><span class="field-label">新密码</span><input v-model="passwordForm.newPassword" type="password" class="input" autocomplete="new-password" minlength="8" maxlength="72" required /><span v-if="fieldErrors.newPassword" class="field-error">{{fieldErrors.newPassword}}</span><span class="field-help">至少 8 个字符，建议混合大小写、数字和符号。</span></label><label class="field"><span class="field-label">MFA 验证码</span><input v-model="passwordForm.mfaCode" class="input otp-input" inputmode="numeric" maxlength="10" autocomplete="one-time-code" required /><span v-if="fieldErrors.mfaCode" class="field-error">{{fieldErrors.mfaCode}}</span></label><div class="modal-footer"><button type="button" class="ghost-button" @click="closeModal">取消</button><button class="primary-button" :disabled="!canSecurityPost">{{submitting?'提交中…':'确认修改'}}</button></div></form>
    <div v-else-if="modal==='enroll'"><StatusNotice tone="warning" title="绑定前确认" detail="请准备支持 TOTP 的身份验证器。原绑定（如有）不会被复用。"/><div class="modal-footer"><button class="ghost-button" @click="closeModal">取消</button><button class="primary-button" :disabled="!canSecurityPost" @click="startEnrollment">{{submitting?'正在创建…':'创建绑定流程'}}</button></div></div>
    <form v-else-if="modal==='confirm'&&enrollment" class="form-stack" @submit.prevent="confirmEnrollment"><div class="mfa-enrollment"><strong>{{enrollment.method}} 验证器</strong><span>手工密钥：<code>{{enrollment.manualKeyMasked}}</code></span><span>有效期至：{{formatDate(enrollment.expiresAt)}}</span><a class="secondary-button enrollment-link" :href="enrollment.secretQrCodeUrl">在验证器中打开</a></div><label class="field"><span class="field-label">验证器动态码</span><input v-model="confirmCode" class="input otp-input" inputmode="numeric" maxlength="10" autocomplete="one-time-code" required /><span v-if="fieldErrors.code" class="field-error">{{fieldErrors.code}}</span></label><div class="modal-footer"><button type="button" class="ghost-button" @click="closeModal">取消</button><button class="primary-button" :disabled="!canSecurityPost">{{submitting?'验证中…':'确认启用'}}</button></div></form>
    <form v-else-if="modal==='disable'" class="form-stack" @submit.prevent="disableMfa"><label class="field"><span class="field-label">MFA 验证码</span><input v-model="disableForm.code" class="input otp-input" inputmode="numeric" maxlength="10" autocomplete="one-time-code" required /><span v-if="fieldErrors.code" class="field-error">{{fieldErrors.code}}</span></label><label class="field"><span class="field-label">解绑原因</span><textarea v-model="disableForm.reason" class="input" maxlength="500" required placeholder="请说明解绑原因（必填）" /><span v-if="fieldErrors.reason" class="field-error">{{fieldErrors.reason}}</span></label><div class="modal-footer"><button type="button" class="ghost-button" @click="closeModal">取消</button><button class="danger-button" :disabled="!canSecurityPost">{{submitting?'提交中…':'确认解绑'}}</button></div></form>
    <div v-else-if="modal==='logout'"><StatusNotice tone="warning" title="确认退出当前会话" detail="退出不会影响其他活跃会话。"/><div class="modal-footer"><button class="ghost-button" @click="closeModal">取消</button><button class="danger-button" :disabled="!canPost" @click="logout">{{submitting?'退出中…':'确认退出'}}</button></div></div>
  </section></div>
</template>
