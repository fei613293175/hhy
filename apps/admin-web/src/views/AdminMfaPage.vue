<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthShell from '../components/AuthShell.vue'
import StatusNotice from '../components/StatusNotice.vue'
import { adminSecurityApi, adminSession, isApiRequestError } from '../services'

const route = useRoute()
const router = useRouter()
const code = ref('')
const submitting = ref(false)
const error = ref<{ title: string; detail: string; requestId?: string }>()
const codeError = ref('')
const rateLimitRemaining = ref(0)
const locked = ref(false)
const forbidden = ref(false)
let rateLimitTimer: number | undefined
const online = ref(navigator.onLine)
const updateOnline = () => { online.value = navigator.onLine }
window.addEventListener('online', updateOnline)
window.addEventListener('offline', updateOnline)
onBeforeUnmount(() => {
  window.removeEventListener('online', updateOnline)
  window.removeEventListener('offline', updateOnline)
  if (rateLimitTimer !== undefined) window.clearInterval(rateLimitTimer)
})
const valid = computed(() => /^\d{4,10}$/.test(code.value))
const blocked = computed(() => forbidden.value || locked.value || rateLimitRemaining.value > 0)

function startRateLimit(seconds?: number) {
  rateLimitRemaining.value = Math.max(1, Math.ceil(seconds ?? 60))
  if (rateLimitTimer !== undefined) window.clearInterval(rateLimitTimer)
  rateLimitTimer = window.setInterval(() => {
    rateLimitRemaining.value = Math.max(0, rateLimitRemaining.value - 1)
    if (rateLimitRemaining.value === 0 && rateLimitTimer !== undefined) {
      window.clearInterval(rateLimitTimer)
      rateLimitTimer = undefined
    }
  }, 1_000)
}

async function submit() {
  const ticket = adminSession.mfaTicket
  if (!ticket || !valid.value || submitting.value || !online.value || blocked.value) return
  submitting.value = true
  error.value = undefined
  codeError.value = ''
  try {
    await adminSecurityApi.verifyMfa({ mfaTicket: ticket, code: code.value })
    code.value = ''
    const target = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/me/security'
    await router.replace(target)
  } catch (caught) {
    code.value = ''
    if (isApiRequestError(caught)) {
      codeError.value = caught.details.find((detail) => detail.field === 'code')?.message ?? ''
      locked.value = caught.status === 423 || caught.code.includes('LOCKED')
      forbidden.value = caught.status === 403
      if (caught.status === 429 && !locked.value) startRateLimit(caught.retryAfter)
      error.value = {
        title: forbidden.value ? '没有二次验证权限' : locked.value ? '验证流程已锁定' : caught.status === 429 ? '验证暂时受限' : '验证未通过',
        detail: forbidden.value ? '当前会话无权继续验证，请返回登录并联系系统负责人。' : locked.value ? `${caught.message}。请返回登录并联系系统负责人。` : caught.message,
        requestId: caught.requestId,
      }
    } else error.value = { title: '网络连接失败', detail: '请恢复网络并获取新的验证码后重试。' }
  } finally { submitting.value = false }
}

function cancel() { adminSession.clear(); void router.replace('/auth/login') }
</script>

<template>
  <AuthShell eyebrow="安全验证" title="完成二次验证" description="打开您的身份验证器，输入当前 6 位动态验证码。验证码只用于本次会话升级。">
    <StatusNotice v-if="!online" tone="warning" title="当前处于离线状态" detail="二次验证已禁用；恢复网络后请使用新的动态验证码。" />
    <StatusNotice v-if="!adminSession.mfaTicket" tone="warning" title="验证流程已失效" detail="未找到有效的 MFA 验证票据，请返回登录页重新开始。" />
    <StatusNotice v-if="error" :tone="rateLimitRemaining ? 'warning' : 'danger'" :title="error.title" :detail="rateLimitRemaining ? `${error.detail}，请在 ${rateLimitRemaining} 秒后重试。` : error.detail" :request-id="error.requestId" />
    <form v-if="!forbidden" class="form-stack" novalidate @submit.prevent="submit">
      <label class="field"><span class="field-label">动态验证码</span><input v-model="code" class="input otp-input" name="code" inputmode="numeric" autocomplete="one-time-code" minlength="4" maxlength="10" pattern="[0-9]*" required autofocus placeholder="000000" :aria-invalid="Boolean(codeError)" :aria-describedby="codeError ? 'mfa-code-error mfa-code-help' : 'mfa-code-help'" /><span v-if="codeError" id="mfa-code-error" class="field-error">{{ codeError }}</span><span id="mfa-code-help" class="field-help">验证码通常每 30 秒更新，请勿连续重复提交同一验证码。</span></label>
      <button class="primary-button" type="submit" :disabled="!adminSession.mfaTicket || !valid || submitting || !online || blocked">{{ submitting ? '正在验证…' : rateLimitRemaining ? `${rateLimitRemaining} 秒后重试` : locked ? '验证已锁定' : '验证并进入后台' }}</button>
      <button class="ghost-button" type="button" @click="cancel">返回登录</button>
    </form>
    <button v-else class="ghost-button" type="button" @click="cancel">安全返回登录</button>
    <p class="security-note">MFA 票据与验证码均不会写入 URL、日志或浏览器持久存储。</p>
  </AuthShell>
</template>
