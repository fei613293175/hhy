<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import StatusNotice from '../components/StatusNotice.vue'
import {
  adminProviderConfigsApi,
  adminSession,
  isApiRequestError,
  type ProviderConfigPage,
  type ProviderConfigResource,
} from '../services'

type ProviderCode = 'sms' | 'storage' | 'identity'
const props = defineProps<{ provider?: ProviderCode }>()

const providerMeta: Record<ProviderCode, { name: string; summary: string; mark: string; route: string }> = {
  sms: { name: '阿里云短信', summary: 'AccessKey、Region、签名、模板、限流和发送测试', mark: '短', route: '/system/providers/sms' },
  storage: { name: '对象存储', summary: 'Cloudflare R2、阿里云 OSS、Scope 绑定和迁移状态', mark: '存', route: '/system/providers/storage' },
  identity: { name: '实名认证', summary: 'APPCODE、活体检测、身份比对、超时和结果映射', mark: '实', route: '/system/providers/identity' },
}

const page = ref<ProviderConfigPage>()
const detail = ref<ProviderConfigResource>()
const loading = ref(true)
const refreshing = ref(false)
const online = ref(navigator.onLine)
const forbidden = ref(false)
const error = ref<{ message: string; requestId?: string }>()
let request: AbortController | undefined

const meta = computed(() => props.provider ? providerMeta[props.provider] : undefined)
const items = computed(() => page.value?.items ?? [])
const secretCount = computed(() => detail.value?.configuredSecrets?.filter((item) => item.configured).length ?? 0)
const hasReadPermission = computed(() => adminSession.hasPermission('config.manage'))

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
      <div><div class="eyebrow">配置中心 · 版本化与秘密隔离</div><h1>{{ meta?.name ?? '供应商配置中心' }}</h1><p>{{ meta?.summary ?? '集中查看短信、对象存储与实名认证配置；秘密只展示配置状态和脱敏引用。' }}</p></div>
      <div class="page-actions"><RouterLink v-if="provider" class="ghost-button provider-back" to="/system/providers">返回配置中心</RouterLink><button class="ghost-button" :disabled="refreshing || !online || forbidden" @click="load('refresh')">{{ refreshing ? '刷新中…' : '刷新' }}</button></div>
    </header>

    <StatusNotice v-if="!online && (page || detail)" tone="warning" title="当前设备离线" detail="正在显示最近一次成功读取的配置快照；离线时禁止任何写操作。" />
    <StatusNotice v-if="forbidden" tone="danger" title="无配置管理权限" :detail="error?.message ?? '当前会话不能读取供应商配置。'" :request-id="error?.requestId" />

    <section v-if="loading" class="provider-grid" aria-label="正在加载供应商配置"><div v-for="index in (provider ? 3 : 4)" :key="index" class="card skeleton skeleton-tall" /></section>
    <section v-else-if="error && !(page || detail)" class="card error-state" aria-live="polite"><div class="avatar centered-mark">!</div><h2>{{ forbidden ? '无法访问供应商配置' : '供应商配置暂时无法加载' }}</h2><p>{{ error.message }}</p><small v-if="error.requestId">请求编号：{{ error.requestId }}</small><div><button v-if="!forbidden" class="primary-button" :disabled="!online" @click="load()">重新加载</button></div></section>

    <template v-else-if="!provider && page">
      <section class="provider-grid">
        <RouterLink v-for="code in (['sms','storage','identity'] as ProviderCode[])" :key="code" class="card provider-card" :to="providerMeta[code].route">
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
        <aside class="card account-summary"><div class="section-heading"><div><h2>连接与版本门禁</h2><p>连接失败时禁止激活。</p></div></div><dl class="facts"><dt>连接状态</dt><dd><span class="status-chip" :data-status="detail.connectionStatus">{{ statusLabel(detail.connectionStatus) }}</span></dd><dt>最近测试</dt><dd>{{ formatDate(detail.lastTestAt) }}</dd><dt>乐观锁版本</dt><dd>v{{ detail.version }}</dd></dl><StatusNotice tone="warning" title="写操作将在下一施工步接通" detail="本页已使用冻结读取类型；创建版本、连接测试、审批激活和回滚正在按状态机逐项接入，未接通前不展示伪按钮。" /></aside>
      </section>
    </template>
  </article>
</template>

<style src="../provider-config.css"></style>
