<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusNotice from '../components/StatusNotice.vue'
import { adminContentsApi, isApiRequestError, type AdminContentResource } from '../services'

const route = useRoute(); const router = useRouter(); const item = ref<AdminContentResource>(); const loading = ref(true); const working = ref(false); const error = ref<string>(); const state = ref<'error'|'not-found'|'forbidden'|'conflict'|'offline'>(); let request: AbortController | undefined
const contentTypeLabels: Record<string, string> = { PROJECT: '项目', APP: '应用', GROUP_CHAT: '群聊', TEAM_LEADER: '团队长' }
const statusLabels: Record<string, string> = { DRAFT: '草稿', PENDING_REVIEW: '待审核', ONLINE: '已上架', OFFLINE: '已下架', BANNED: '已封禁', REJECTED: '未通过' }
const reviewLabels: Record<string, string> = { APPROVED: '审核通过', PENDING: '待审核', REJECTED: '审核未通过' }
const categoryLabels: Record<string, string> = { DIGITAL_SERVICE: '数字服务', ENTERPRISE_APP: '企业应用', BUSINESS_COMMUNITY: '商业社群', TEAM_SERVICE: '团队服务' }
const regionLabels: Record<string, string> = { 'CN-31': '上海', 'CN-33': '浙江', 'CN-44': '广东' }
function contentTypeLabel(value?: string) { return value ? (contentTypeLabels[value] ?? '其他内容') : '暂无' }
function statusLabel(value?: string) { return value ? (statusLabels[value] ?? '状态确认中') : '暂无' }
function reviewLabel(value?: string) { return value ? (reviewLabels[value] ?? '审核状态确认中') : '未标注审核状态' }
function categoryLabel(value?: string) { return value ? (categoryLabels[value] ?? '其他分类') : '暂无' }
function regionLabel(value?: string) { return value ? (regionLabels[value] ?? '其他地区') : '暂无' }
function formatDate(value?: string) { return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '暂无' }
function friendly(caught: unknown) { if (isApiRequestError(caught)) { if (caught.status === 403) state.value = 'forbidden'; if (caught.status === 404) state.value = 'not-found'; if (caught.status === 409) state.value = 'conflict'; return caught.message } return '内容详情暂时无法加载，请稍后重试' }
async function load() { request?.abort(); request = new AbortController(); loading.value = true; error.value = undefined; state.value = undefined; if (!navigator.onLine) { state.value = 'offline'; loading.value = false; return } try { item.value = await adminContentsApi.get(String(route.params.id), { signal: request.signal }) } catch (caught) { error.value = friendly(caught) } finally { loading.value = false } }
async function command(operation: 'adminContentPostContentsByIdOnline'|'adminContentPostContentsByIdOffline'|'adminContentPostContentsByIdBan'|'adminContentPostContentsByIdRecommend'|'adminContentPostContentsByIdOfficialMark') { if (!item.value || working.value) return; working.value = true; error.value = undefined; try { await adminContentsApi.command(operation, item.value.id, ({ expectedVersion: item.value.version, ...(operation.endsWith('Ban') ? { reason: '后台运营操作' } : operation.endsWith('Recommend') ? { enabled: true } : operation.endsWith('OfficialMark') ? { enabled: true } : {}) } as never)); await load() } catch (caught) { error.value = friendly(caught) } finally { working.value = false } }
onMounted(load); onBeforeUnmount(() => request?.abort())
</script>
<template>
  <article class="page users-page"><header class="page-heading"><div><div class="eyebrow">内容运营 · 详情</div><h1>{{ item?.title || '内容详情' }}</h1><p>查看内容版本、媒体、链接和运营状态。</p></div><div class="page-actions"><button class="ghost-button" @click="router.back()">返回</button><button class="ghost-button" :disabled="loading" @click="load">刷新</button></div></header>
    <StatusNotice v-if="state === 'offline'" tone="warning" title="当前设备离线" detail="详情以服务端数据为准，恢复网络后重试。" /><StatusNotice v-if="state === 'forbidden'" tone="danger" title="无权查看此内容" detail="当前会话没有内容读取权限。" /><StatusNotice v-if="state === 'not-found'" tone="warning" title="内容不存在" detail="该内容可能已删除或下架。" /><StatusNotice v-if="state === 'conflict'" tone="warning" title="数据已变化" detail="请刷新后再执行运营操作。" />
    <section v-if="loading" class="card skeleton-stack"><div v-for="n in 5" :key="n" class="skeleton user-row-skeleton" /></section><section v-else-if="error && !item" class="card error-state"><h2>无法加载内容详情</h2><p>{{ error }}</p><button class="primary-button" @click="load">重新加载</button></section><section v-else-if="item" class="card detail-card"><div class="section-heading"><div><h2>{{ item.title }}</h2><p>{{ contentTypeLabel(item.contentType) }} · {{ statusLabel(item.status) }} · 数据版本 v{{ item.version }}</p></div><span class="status-chip" :data-status="item.reviewStatus">{{ reviewLabel(item.reviewStatus) }}</span></div><dl class="detail-grid"><div><dt>内容编号</dt><dd>{{ item.id }}</dd></div><div><dt>分类</dt><dd>{{ categoryLabel(item.categoryCode) }}</dd></div><div><dt>地区</dt><dd>{{ regionLabel(item.regionCode) }}</dd></div><div><dt>更新时间</dt><dd>{{ formatDate(item.updatedAt) }}</dd></div></dl><p class="detail-copy">{{ item.summary || item.description || '暂无内容说明' }}</p><div class="page-actions"><button class="primary-button" :disabled="working" @click="command(item.status === 'ONLINE' ? 'adminContentPostContentsByIdOffline' : 'adminContentPostContentsByIdOnline')">{{ item.status === 'ONLINE' ? '下线' : '上线' }}</button><button class="secondary-button" :disabled="working" @click="command('adminContentPostContentsByIdRecommend')">推荐</button><button class="secondary-button" :disabled="working" @click="command('adminContentPostContentsByIdOfficialMark')">标记官方</button><button class="danger-button" :disabled="working" @click="command('adminContentPostContentsByIdBan')">封禁</button></div><p v-if="error" class="field-error">{{ error }}</p></section>
  </article>
</template>
