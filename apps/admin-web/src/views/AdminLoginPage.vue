<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthShell from '../components/AuthShell.vue'
import StatusNotice from '../components/StatusNotice.vue'
import { adminSecurityApi, adminSession, isApiRequestError } from '../services'

const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const captchaToken = ref('')
const submitting = ref(false)
const error = ref<{ title: string; detail: string; requestId?: string }>()
const fieldErrors = ref<Record<string, string>>({})
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

const valid = computed(() => username.value.trim().length > 0 && password.value.length >= 8 && password.value.length <= 72)
const blocked = computed(() => forbidden.value || locked.value || rateLimitRemaining.value > 0)
const passwordError = computed(() => fieldErrors.value.password ?? (password.value && (password.value.length < 8 || password.value.length > 72) ? '密码长度须为 8–72 个字符' : ''))

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
  if (!valid.value || submitting.value || !online.value || blocked.value) return
  submitting.value = true
  error.value = undefined
  fieldErrors.value = {}
  try {
    const session = await adminSecurityApi.login({ username: username.value.trim(), password: password.value, captchaToken: captchaToken.value || undefined })
    password.value = ''
    captchaToken.value = ''
    if (session.mfaRequired !== 'NONE') {
      await router.replace({ path: '/auth/mfa', query: { redirect: route.query.redirect } })
      return
    }
    const target = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/me/security'
    await router.replace(target)
  } catch (caught) {
    password.value = ''
    captchaToken.value = ''
    if (isApiRequestError(caught)) {
      fieldErrors.value = Object.fromEntries(caught.details.flatMap((detail) => detail.field ? [[detail.field, detail.message]] : []))
      locked.value = caught.status === 423
        || caught.code.includes('LOCKED')
        || caught.code.includes('ACCOUNT_RESTRICTED')
        || (caught.status === 422 && caught.message === '管理员账号已受限')
      forbidden.value = caught.status === 403
      if (caught.status === 429 && !locked.value) startRateLimit(caught.retryAfter)
      error.value = {
        title: forbidden.value ? '没有后台登录权限' : locked.value ? '管理员账号已锁定' : caught.status === 429 ? '登录暂时受限' : '无法登录',
        detail: forbidden.value ? '当前环境拒绝后台登录请求，请联系系统负责人核对访问权限。' : locked.value ? `${caught.message}。请联系系统负责人处理。` : caught.message,
        requestId: caught.requestId,
      }
    } else {
      error.value = { title: '网络连接失败', detail: '请检查网络后重试，管理员账号已保留，密码不会被存储。' }
    }
  } finally { submitting.value = false }
}
</script>

<template>
  <AuthShell eyebrow="合伙云 Pro · 运营控制台" title="登录管理后台" description="使用管理员账号继续。若账号已启用 MFA，下一步将进入独立的二次验证。">
    <StatusNotice v-if="!online" tone="warning" title="当前处于离线状态" detail="写操作已禁用，恢复网络后可继续登录。" />
    <StatusNotice v-if="error" :tone="rateLimitRemaining ? 'warning' : 'danger'" :title="error.title" :detail="rateLimitRemaining ? `${error.detail}，请在 ${rateLimitRemaining} 秒后重试。` : error.detail" :request-id="error.requestId" />
    <form v-if="!forbidden" class="form-stack" novalidate @submit.prevent="submit">
      <label class="field"><span class="field-label">管理员账号</span><input v-model="username" class="input" name="username" autocomplete="username" maxlength="2000" required autofocus placeholder="请输入管理员账号" :aria-invalid="Boolean(fieldErrors.username)" :aria-describedby="fieldErrors.username ? 'login-username-error' : undefined" /><span v-if="fieldErrors.username" id="login-username-error" class="field-error">{{ fieldErrors.username }}</span></label>
      <label class="field"><span class="field-label">密码</span><input v-model="password" class="input" name="password" type="password" autocomplete="current-password" minlength="8" maxlength="72" required placeholder="请输入登录密码" :aria-invalid="Boolean(passwordError)" :aria-describedby="passwordError ? 'login-password-error' : undefined" /><span v-if="passwordError" id="login-password-error" class="field-error">{{ passwordError }}</span></label>
      <label v-if="captchaToken || fieldErrors.captchaToken" class="field"><span class="field-label">安全验证</span><input v-model="captchaToken" class="input" maxlength="2000" autocomplete="off" /><span v-if="fieldErrors.captchaToken" class="field-error">{{ fieldErrors.captchaToken }}</span></label>
      <button class="primary-button" type="submit" :disabled="!valid || submitting || !online || blocked">{{ submitting ? '正在安全验证…' : rateLimitRemaining ? `${rateLimitRemaining} 秒后重试` : locked ? '账号已锁定' : '登录并继续' }}</button>
    </form>
    <p class="security-note">登录状态仅保存在当前浏览器标签页；刷新和打开后台深链可继续，关闭标签页后自动失效。</p>
  </AuthShell>
</template>
