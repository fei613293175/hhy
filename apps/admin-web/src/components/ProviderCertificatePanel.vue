<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  adminProviderCertificatesApi, adminSession, isApiRequestError,
  type ProviderCertificate, type RotateProviderCertificateRequest,
  type UploadProviderCertificateRequest,
} from '../services'
import StatusNotice from './StatusNotice.vue'

type Mode = 'upload' | 'rotate'
const certificates = ref<ProviderCertificate[]>([])
const loading = ref(true)
const submitting = ref(false)
const stale = ref(false)
const error = ref<{ message: string; requestId?: string }>()
const notice = ref<{ tone: 'success' | 'warning'; title: string; detail: string }>()
const mode = ref<Mode>()
const upload = reactive({ certificateType: '', alias: '', encryptedContentBase64: '', passwordSecretRef: '', expiresAt: '' })
const rotate = reactive({ currentId: '', newCertificateId: '', approvalId: '', reason: '', expectedVersion: 0 })
let request: AbortController | undefined

const writable = computed(() => adminSession.hasPermission('config.manage')
  && navigator.onLine && !submitting.value && !stale.value)
const payoutCertificates = computed(() => certificates.value.filter((item) => item.provider === 'payout'))

async function load() {
  request?.abort()
  const current = new AbortController()
  request = current
  loading.value = true
  error.value = undefined
  try {
    const page = await adminProviderCertificatesApi.list(
      { page: 1, pageSize: 100, keyword: 'payout' }, { signal: current.signal },
    )
    certificates.value = page.items
    stale.value = false
  } catch (caught) {
    if (isApiRequestError(caught) && caught.code === 'REQUEST_ABORTED') return
    error.value = isApiRequestError(caught)
      ? { message: caught.message, requestId: caught.requestId }
      : { message: '证书元数据暂时无法加载，请稍后重试。' }
  } finally {
    if (request === current) request = undefined
    loading.value = false
  }
}

function openUpload() {
  if (!writable.value) return
  notice.value = undefined
  Object.assign(upload, { certificateType: 'ALIPAY_PRIVATE_KEY', alias: '', encryptedContentBase64: '', passwordSecretRef: '', expiresAt: '' })
  mode.value = 'upload'
}

function openRotate(item: ProviderCertificate) {
  if (!writable.value || item.status !== 'ACTIVE') return
  notice.value = undefined
  Object.assign(rotate, { currentId: item.id, newCertificateId: '', approvalId: '', reason: '', expectedVersion: Number(item.version) })
  mode.value = 'rotate'
}

function validBase64(value: string): boolean {
  return value.length >= 24 && value.length <= 2000
    && /^[A-Za-z0-9+/]+={0,2}$/.test(value) && value.length % 4 === 0
}

async function submit() {
  if (!mode.value || !writable.value) return
  submitting.value = true
  error.value = undefined
  try {
    if (mode.value === 'upload') {
      if (!upload.certificateType.trim() || !upload.alias.trim()) throw new Error('证书类型和别名不能为空。')
      if (!validBase64(upload.encryptedContentBase64.trim())) throw new Error('加密证书内容必须是有效 Base64，且长度不超过 2000。')
      if (upload.passwordSecretRef && !/^(vault|kms):\/\//i.test(upload.passwordSecretRef.trim())) throw new Error('证书密码只能填写 vault:// 或 kms:// 引用。')
      const body: UploadProviderCertificateRequest = {
        provider: 'payout', certificateType: upload.certificateType.trim(), alias: upload.alias.trim(),
        encryptedContentBase64: upload.encryptedContentBase64.trim(),
        passwordSecretRef: upload.passwordSecretRef.trim() || undefined,
        expiresAt: upload.expiresAt ? new Date(upload.expiresAt).toISOString() : undefined,
      }
      await adminProviderCertificatesApi.upload(body)
      upload.encryptedContentBase64 = ''
      notice.value = { tone: 'success', title: '证书已安全上传', detail: '页面仅保留证书指纹和状态；原始内容不会再次显示。' }
    } else {
      const body: RotateProviderCertificateRequest = {
        newCertificateId: rotate.newCertificateId.trim(), approvalId: rotate.approvalId.trim(),
        reason: rotate.reason.trim(), expectedVersion: rotate.expectedVersion,
      }
      if (!body.newCertificateId || !body.approvalId || !body.reason) throw new Error('新证书、审批单和轮换原因不能为空。')
      await adminProviderCertificatesApi.rotate(rotate.currentId, body)
      notice.value = { tone: 'warning', title: '证书轮换已完成', detail: '原证书已退出生效状态，新证书已按审批结果启用。' }
    }
    mode.value = undefined
    await load()
  } catch (caught) {
    upload.encryptedContentBase64 = ''
    if (caught instanceof Error && !isApiRequestError(caught)) error.value = { message: caught.message }
    else if (isApiRequestError(caught)) {
      if (caught.status === 409) stale.value = true
      error.value = { message: caught.message, requestId: caught.requestId }
    } else error.value = { message: '写入结果未知；系统没有自动重试，请刷新确认后再操作。' }
  } finally {
    submitting.value = false
  }
}

function formatDate(value?: string) {
  return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium' }).format(new Date(value)) : '未提供'
}

onMounted(() => void load())
onBeforeUnmount(() => request?.abort())
</script>

<template>
  <section class="card certificate-panel" aria-labelledby="provider-certificate-title">
    <div class="certificate-heading">
      <div><div class="eyebrow">支付宝证书 · 原文不可回读</div><h2 id="provider-certificate-title">证书上传与轮换</h2><p>只展示指纹、有效期和版本；私钥、证书原文及密码不会回显。</p></div>
      <button class="primary-button" :disabled="!writable" @click="openUpload">上传加密证书</button>
    </div>
    <StatusNotice v-if="stale" tone="warning" title="证书版本已经变化" detail="轮换已暂停，请刷新证书列表后重新确认。" />
    <StatusNotice v-if="notice" :tone="notice.tone" :title="notice.title" :detail="notice.detail" />
    <StatusNotice v-if="error" tone="danger" title="证书操作未完成" :detail="error.message" :request-id="error.requestId" />
    <p v-if="loading">正在加载证书元数据…</p>
    <div v-else-if="!payoutCertificates.length" class="certificate-empty">尚未上传企业付款证书。</div>
    <div v-else class="certificate-list">
      <article v-for="item in payoutCertificates" :key="item.id" class="certificate-item">
        <div><strong>{{ item.alias || item.certificateType }}</strong><span class="status-chip" :data-status="item.status">{{ item.status }}</span></div>
        <dl><dt>证书类型</dt><dd>{{ item.certificateType }}</dd><dt>SHA-256 指纹</dt><dd class="fingerprint">{{ item.fingerprint }}</dd><dt>到期时间</dt><dd>{{ formatDate(item.expiresAt) }}</dd><dt>版本</dt><dd>{{ item.version }}</dd></dl>
        <button class="ghost-button" :disabled="!writable || item.status !== 'ACTIVE'" @click="openRotate(item)">轮换此证书</button>
      </article>
    </div>

    <form v-if="mode" class="certificate-form" @submit.prevent="submit">
      <template v-if="mode === 'upload'">
        <h3>上传加密证书或私钥</h3>
        <label>证书类型<input v-model="upload.certificateType" class="input" maxlength="64" required /></label>
        <label>证书别名<input v-model="upload.alias" class="input" maxlength="200" required /></label>
        <label>加密内容 Base64<textarea v-model="upload.encryptedContentBase64" class="input" maxlength="2000" required autocomplete="off" /></label>
        <label>密码 SecretRef（可选）<input v-model="upload.passwordSecretRef" class="input" maxlength="512" autocomplete="off" placeholder="vault:// 或 kms://" /></label>
        <label>到期时间（可选）<input v-model="upload.expiresAt" class="input" type="datetime-local" /></label>
        <StatusNotice tone="warning" title="敏感材料仅提交一次" detail="提交成功或失败后页面都会立即清空证书原文；服务端响应不会返回原文。" />
      </template>
      <template v-else>
        <h3>审批后轮换证书</h3>
        <label>当前证书<input v-model="rotate.currentId" class="input" readonly /></label>
        <label>待启用证书编号<input v-model="rotate.newCertificateId" class="input" maxlength="64" required /></label>
        <label>审批单编号<input v-model="rotate.approvalId" class="input" maxlength="64" required /></label>
        <label>轮换原因<textarea v-model="rotate.reason" class="input" maxlength="1000" required /></label>
        <label>当前版本<input v-model.number="rotate.expectedVersion" class="input" type="number" readonly /></label>
      </template>
      <div class="certificate-actions"><button type="button" class="ghost-button" :disabled="submitting" @click="mode = undefined">取消</button><button class="primary-button" :disabled="!writable">{{ submitting ? '提交中…' : '确认提交' }}</button></div>
    </form>
  </section>
</template>

<style scoped>
.certificate-panel { display: grid; gap: 18px; }
.certificate-heading, .certificate-item > div, .certificate-actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.certificate-heading h2, .certificate-heading p, .certificate-item strong { margin: 0; }
.certificate-list, .certificate-form { display: grid; gap: 14px; }
.certificate-item { border: var(--hhy-border-standard) solid var(--hhy-color-border-default); border-radius: 16px; padding: 16px; display: grid; gap: 14px; }
.certificate-item dl { display: grid; grid-template-columns: minmax(110px, 0.35fr) 1fr; gap: 8px 14px; margin: 0; }
.certificate-item dt { color: var(--hhy-color-text-secondary); }
.certificate-item dd { margin: 0; min-width: 0; }
.fingerprint { overflow-wrap: anywhere; font-family: ui-monospace, monospace; }
.certificate-form { border-top: var(--hhy-border-standard) solid var(--hhy-color-border-default); padding-top: 18px; }
.certificate-form label { display: grid; gap: 8px; font-weight: 600; }
.certificate-empty { padding: 24px; border-radius: 14px; background: var(--hhy-color-background-page); text-align: center; color: var(--hhy-color-text-secondary); }
@media (max-width: 720px) { .certificate-heading { align-items: stretch; flex-direction: column; } .certificate-item dl { grid-template-columns: 1fr; } }
</style>
