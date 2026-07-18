<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusNotice from '../components/StatusNotice.vue'
import { adminSession, adminUsersApi, isApiRequestError, type AdminUserResource } from '../services'

const route = useRoute()
const router = useRouter()
const user = ref<AdminUserResource>()
const loading = ref(true)
const forbidden = ref(false)
const missing = ref(false)
const online = ref(navigator.onLine)
const error = ref<{ message: string; requestId?: string }>()
const activeTab = ref('概览')
const tabs = ['概览', '资料', '实名', '内容', '订单', '奖励', '邀请关系', '设备会话', '风险', '操作日志']
let activeRequest: AbortController | undefined

const userId = computed(() => String(route.params.id ?? ''))
const statusLabels: Record<string, string> = { ACTIVE:'正常', RESTRICTED:'受限', FROZEN:'已冻结', CANCEL_PENDING:'注销处理中', CANCELLED:'已注销', VERIFIED:'已实名' }
function label(value?: string) { return value ? (statusLabels[value] ?? '其他状态') : '暂无' }
function formatDate(value?: string) { return value ? new Intl.DateTimeFormat('zh-CN',{dateStyle:'long',timeStyle:'short'}).format(new Date(value)) : '暂无' }

async function load() {
  if (!online.value) { loading.value=false; error.value={message:'当前设备离线，请恢复网络后重试'}; return }
  activeRequest?.abort()
  const request=new AbortController(); activeRequest=request; loading.value=true; error.value=undefined; missing.value=false
  try { user.value=await adminUsersApi.getUser(userId.value,{signal:request.signal}); forbidden.value=false }
  catch(caught) {
    if(isApiRequestError(caught)) {
      if(caught.code==='REQUEST_ABORTED') return
      if(caught.status===401) { adminSession.clear(); await router.replace({path:'/auth/login',query:{redirect:route.fullPath}}); return }
      forbidden.value=caught.status===403; missing.value=caught.status===404
      error.value={message:caught.message,requestId:caught.requestId}
    } else error.value={message:'用户详情加载失败，请稍后重试'}
  } finally { if(activeRequest===request) activeRequest=undefined; loading.value=false }
}
function updateOnline(){online.value=navigator.onLine;if(!online.value)activeRequest?.abort();else if(!user.value)void load()}
watch(userId,()=>void load())
onMounted(()=>{window.addEventListener('online',updateOnline);window.addEventListener('offline',updateOnline);void load()})
onBeforeUnmount(()=>{activeRequest?.abort();window.removeEventListener('online',updateOnline);window.removeEventListener('offline',updateOnline)})
</script>

<template>
  <article class="page user-detail-page">
    <header class="page-heading"><div><div class="eyebrow">用户详情 · 编号 {{userId}}</div><h1>{{user?.nickname || '用户详情'}}</h1><p>基础资料、状态与关联业务的统一只读视图。</p></div><div class="page-actions"><RouterLink class="ghost-button table-link" to="/users">返回列表</RouterLink><button class="ghost-button" :disabled="loading||!online" @click="load">刷新</button></div></header>
    <StatusNotice v-if="!online&&user" tone="warning" title="当前设备离线" detail="正在显示最近一次成功读取的详情。"/>
    <section v-if="loading" class="card skeleton-stack"><div class="skeleton skeleton-tall"/><div class="skeleton"/><div class="skeleton"/></section>
    <section v-else-if="error&&!user" class="card error-state"><div class="avatar centered-mark">!</div><h2>{{missing?'用户不存在':forbidden?'无权查看该用户':'详情暂时无法加载'}}</h2><p>{{error.message}}</p><small v-if="error.requestId">请求编号：{{error.requestId}}</small><div><button v-if="!forbidden&&!missing" class="primary-button" :disabled="!online" @click="load">重新加载</button></div></section>
    <template v-else-if="user&&!forbidden">
      <section class="security-hero"><div class="card user-profile-hero"><div class="summary-top"><span class="avatar user-avatar">{{(user.nickname||user.id).slice(0,1)}}</span><div><strong>{{user.nickname||`用户 ${user.id}`}}</strong><small>{{user.phoneMasked||'未绑定手机号'}}</small></div></div><div class="button-row"><span class="status-chip" :data-status="user.status">{{label(user.status)}}</span><span class="tag tag-success">服务端已脱敏</span></div></div><div class="card account-summary"><strong>数据版本</strong><span class="metric-value">v{{user.version}}</span><small>创建于 {{formatDate(user.createdAt)}}</small></div></section>
      <nav class="detail-tabs" aria-label="用户详情分区"><button v-for="tab in tabs" :key="tab" :class="{active:activeTab===tab}" @click="activeTab=tab">{{tab}}</button></nav>
      <section class="card detail-panel">
        <div v-if="activeTab==='概览'" class="facts-grid"><dl class="facts"><dt>用户编号</dt><dd>{{user.id}}</dd><dt>账号状态</dt><dd>{{label(user.status)}}</dd><dt>实名状态</dt><dd>{{label(user.identityStatus)}}</dd><dt>会员状态</dt><dd>{{label(user.membershipStatus)}}</dd></dl><dl class="facts"><dt>手机号</dt><dd>{{user.phoneMasked||'暂无'}}</dd><dt>创建时间</dt><dd>{{formatDate(user.createdAt)}}</dd><dt>数据版本</dt><dd>v{{user.version}}</dd></dl></div>
        <dl v-else-if="activeTab==='资料'" class="facts"><dt>昵称</dt><dd>{{user.nickname||'暂无'}}</dd><dt>头像</dt><dd>{{user.avatarUrl?'已设置':'暂无'}}</dd><dt>个人简介</dt><dd>{{user.bio||'暂无'}}</dd></dl>
        <dl v-else-if="activeTab==='实名'" class="facts"><dt>实名状态</dt><dd>{{label(user.identityStatus)}}</dd><dt>信息范围</dt><dd>当前页面不展示身份证件原文</dd></dl>
        <dl v-else-if="activeTab==='奖励'" class="facts"><dt>会员状态</dt><dd>{{label(user.membershipStatus)}}</dd><dt>敏感金额</dt><dd>当前读取契约未返回金额字段</dd></dl>
        <div v-else class="empty-state compact-empty"><div class="avatar centered-mark">0</div><h2>暂无已产生的数据</h2><p>当前用户在“{{activeTab}}”分区没有可展示的记录。</p></div>
      </section>
    </template>
  </article>
</template>
