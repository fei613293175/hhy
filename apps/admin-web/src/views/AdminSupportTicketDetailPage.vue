<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusNotice from '../components/StatusNotice.vue'
import { adminR15Api, adminSession, isApiRequestError, type AdminSupportTicket } from '../services'

const route = useRoute(); const router = useRouter(); const ticket = ref<AdminSupportTicket>(); const loading = ref(true); const refreshing = ref(false); const submitting = ref(false)
const offline = ref(!navigator.onLine); const stale = ref(false); const forbidden = ref(false); const notFound = ref(false); const error = ref<string>(); const notice = ref<{ tone: 'success' | 'warning' | 'danger'; title: string; detail?: string }>()
const assign = reactive({ assigneeId: '', reason: '' }); const reply = reactive({ content: '', attachments: '' }); const close = reactive({ reason: '' }); let request: AbortController | undefined
const id = computed(() => typeof route.params.id === 'string' ? route.params.id : '')
const canAssign = computed(() => adminSession.hasPermission('support.assign')); const canReply = computed(() => adminSession.hasPermission('support.reply')); const canClose = computed(() => adminSession.hasPermission('support.close'))
const writable = computed(() => !offline.value && !stale.value && !submitting.value && Boolean(ticket.value)); const isClosed = computed(() => ['CLOSED', 'RESOLVED'].includes(ticket.value?.status ?? ''))
function statusLabel(input?: string) { return ({ OPEN: '待受理', ASSIGNED: '已分配', IN_PROGRESS: '处理中', WAITING_USER: '等待用户', RESOLVED: '已解决', CLOSED: '已关闭' } as Record<string, string>)[input ?? ''] ?? '待确认' }
function format(input?: string) { return input ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(input)) : '暂无记录' }
function message(caught: unknown, fallback: string) {
  if (!isApiRequestError(caught)) return fallback
  if (caught.status === 0) return '网络连接不可用，当前快照已标记为可能过期'
  if (caught.status === 403) return '当前账号没有执行此操作的权限'
  if (caught.status === 404) return '工单不存在或已不可访问'
  if (caught.status === 409) return '工单已被其他客服更新，请重新加载最新版本'
  if (caught.status === 422) return caught.message || '当前工单状态不允许执行此操作'
  if (caught.status === 429) return caught.retryAfter ? `操作过于频繁，请在 ${caught.retryAfter} 秒后重试` : '操作过于频繁，请稍后重试'
  return caught.requestId !== 'missing' ? `${fallback}（请求 ${caught.requestId}）` : fallback
}
async function unauthorized(caught: unknown) { if (!isApiRequestError(caught) || caught.status !== 401) return false; adminSession.clear(); await router.replace({ path: '/auth/login', query: { redirect: route.fullPath } }); return true }
async function load(mode: 'initial' | 'refresh' = 'initial') {
  request?.abort(); if (!navigator.onLine) { offline.value = true; loading.value = false; if (!ticket.value) error.value = '当前设备离线，无法读取工单'; return }
  const current = new AbortController(); request = current; error.value = undefined; forbidden.value = false; notFound.value = false; mode === 'initial' ? loading.value = true : refreshing.value = true
  try { const loaded = await adminR15Api.supportTicket(id.value, { signal: current.signal }); ticket.value = loaded; assign.assigneeId = loaded.assignee ?? ''; stale.value = false; offline.value = false }
  catch (caught) { if (isApiRequestError(caught) && caught.code === 'REQUEST_ABORTED') return; if (await unauthorized(caught)) return; forbidden.value = isApiRequestError(caught) && caught.status === 403; notFound.value = isApiRequestError(caught) && caught.status === 404; if (ticket.value && isApiRequestError(caught) && caught.status === 0) stale.value = true; error.value = message(caught, '工单详情暂时无法加载') }
  finally { if (request === current) request = undefined; loading.value = false; refreshing.value = false }
}
async function run(action: 'assign' | 'reply' | 'close') {
  if (!ticket.value || !writable.value) return
  if (action === 'assign' && (!assign.assigneeId.trim() || !window.confirm(`确认将工单分配给 ${assign.assigneeId.trim()}？`))) return
  if (action === 'reply' && !reply.content.trim()) return
  if (action === 'close' && !window.confirm('确认关闭此工单？用户将看到工单已结束。')) return
  submitting.value = true; notice.value = undefined
  try {
    if (action === 'assign') await adminR15Api.assignTicket(ticket.value.id, { assigneeId: assign.assigneeId.trim(), reason: assign.reason.trim() || undefined, expectedVersion: ticket.value.version })
    if (action === 'reply') await adminR15Api.replyTicket(ticket.value.id, { content: reply.content.trim(), attachments: reply.attachments.split(/[;,\n]/).map((item) => item.trim()).filter(Boolean) })
    if (action === 'close') await adminR15Api.closeTicket(ticket.value.id, { reason: close.reason.trim() || undefined, expectedVersion: ticket.value.version })
    notice.value = { tone: 'success', title: action === 'assign' ? '分配客服成功' : action === 'reply' ? '客服回复成功' : '关闭工单成功', detail: '工单已按服务端最新状态刷新。' }
    if (action === 'reply') { reply.content = ''; reply.attachments = '' }
    await load('refresh')
  } catch (caught) { if (await unauthorized(caught)) return; stale.value = isApiRequestError(caught) && caught.status === 409; notice.value = { tone: stale.value ? 'warning' : 'danger', title: stale.value ? '数据冲突，未覆盖新版本' : '操作失败', detail: message(caught, '操作未完成，请稍后重试') } }
  finally { submitting.value = false }
}
async function back() { const from = typeof route.query.from === 'string' && route.query.from.startsWith('/support/tickets') ? route.query.from : '/support/tickets'; await router.push(from) }
function online() { offline.value = false; void load('refresh') } function offlineNow() { offline.value = true }
onMounted(() => { window.addEventListener('online', online); window.addEventListener('offline', offlineNow); void load() }); onBeforeUnmount(() => { request?.abort(); window.removeEventListener('online', online); window.removeEventListener('offline', offlineNow) })
</script>

<template><div class="r15-page"><header class="r15-heading"><div><button type="button" class="back-link" @click="back">返回工单列表</button><p class="eyebrow">R15 客户服务</p><h1>{{ ticket?.ticketNo || '工单详情' }}</h1><p>{{ ticket?.subject || '查看工单上下文并处理用户诉求。' }}</p></div><button type="button" :disabled="refreshing" @click="load('refresh')">{{ refreshing ? '刷新中' : '刷新' }}</button></header>
<StatusNotice v-if="offline" tone="warning" title="当前离线" detail="详情快照只读，所有写操作已禁用。" /><StatusNotice v-if="stale" tone="warning" title="工单数据已过期" detail="本地输入已保留，请加载服务端最新版本后继续。" /><StatusNotice v-if="notice" :tone="notice.tone" :title="notice.title" :detail="notice.detail" />
<div v-if="loading" class="r15-skeleton"><span v-for="n in 5" :key="n" /></div><StatusNotice v-else-if="forbidden" tone="danger" title="无访问权限" detail="未展示任何工单敏感内容。" /><section v-else-if="notFound" class="r15-empty"><h2>工单不存在或已不可访问</h2><button type="button" @click="back">返回工单列表</button></section><StatusNotice v-else-if="error && !ticket" tone="danger" title="工单加载失败" :detail="error" />
<div v-else-if="ticket" class="ticket-detail"><section class="ticket-summary"><div class="r15-panel-title"><div><p class="eyebrow">工单摘要</p><h2>{{ ticket.subject || '未填写主题' }}</h2></div><span class="status-chip">{{ statusLabel(ticket.status) }}</span></div><dl><div><dt>工单编号</dt><dd>{{ ticket.ticketNo }}</dd></div><div><dt>分类</dt><dd>{{ ticket.category || '未分类' }}</dd></div><div><dt>当前客服</dt><dd>{{ ticket.assignee || '待分配' }}</dd></div><div><dt>服务端版本</dt><dd>v{{ ticket.version }}</dd></div><div><dt>创建时间</dt><dd>{{ format(ticket.createdAt) }}</dd></div><div><dt>最近消息</dt><dd>{{ format(ticket.lastMessageAt) }}</dd></div></dl></section>
<section class="ticket-timeline"><h2>会话与附件</h2><p>当前冻结契约仅返回工单摘要和最近消息时间，不生成虚假会话、附件或内部备注。提交回复后将以服务端工单状态为准。</p></section>
<aside class="ticket-actions"><section v-if="canAssign"><h2>分配客服</h2><label>客服标识<input v-model="assign.assigneeId" maxlength="64" :disabled="!writable" /></label><label>分配原因<textarea v-model="assign.reason" maxlength="500" :disabled="!writable" /></label><button type="button" :disabled="!writable || !assign.assigneeId.trim()" @click="run('assign')">确认分配</button></section><p v-else class="permission-hint">当前角色无工单分配权限。</p>
<section v-if="canReply && !isClosed"><h2>客服回复</h2><label>回复内容<textarea v-model="reply.content" maxlength="2000" :disabled="!writable" required /></label><label>附件媒体 ID<input v-model="reply.attachments" placeholder="多个 ID 用逗号分隔" :disabled="!writable" /></label><button type="button" :disabled="!writable || !reply.content.trim()" @click="run('reply')">发送回复</button></section><p v-else-if="!canReply" class="permission-hint">当前角色无客服回复权限。</p>
<section v-if="canClose && !isClosed"><h2>关闭工单</h2><label>关闭原因<textarea v-model="close.reason" maxlength="500" :disabled="!writable" /></label><button type="button" class="danger-button" :disabled="!writable" @click="run('close')">关闭工单</button></section><p v-else-if="!canClose" class="permission-hint">当前角色无关闭工单权限。</p><button v-if="stale" type="button" @click="load('refresh')">加载最新版本</button></aside></div></div></template>
<style scoped src="../r15-operations.css"></style>
