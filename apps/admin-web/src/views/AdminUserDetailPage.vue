<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusNotice from '../components/StatusNotice.vue'
import {
  adminSession,
  adminUsersApi,
  isApiRequestError,
  type AdminUserCommandOutcome,
  type AdminUserResource,
} from '../services'

type UserAction = 'restrict' | 'removeRestriction' | 'freeze' | 'unfreeze' | 'forceLogout'

const route = useRoute()
const router = useRouter()
const user = ref<AdminUserResource>()
const loading = ref(true)
const forbidden = ref(false)
const missing = ref(false)
const online = ref(navigator.onLine)
const error = ref<{ message: string; requestId?: string }>()
const notice = ref<{ tone: 'success' | 'warning' | 'danger'; title: string; detail: string; requestId?: string }>()
const modal = ref<UserAction>()
const submitting = ref(false)
const writeForbidden = ref(false)
const stale = ref(false)
const form = reactive({ restrictionType: '', reason: '', expiresAt: '' })
const activeTab = ref('概览')
const tabs = ['概览', '资料', '实名', '内容', '订单', '奖励', '邀请关系', '设备会话', '风险', '操作日志']
let activeRequest: AbortController | undefined

const userId = computed(() => String(route.params.id ?? ''))
const canRestrict = computed(() => adminSession.hasPermission('user.restrict'))
const canFreeze = computed(() => adminSession.hasPermission('user.freeze'))
const canSecure = computed(() => adminSession.hasPermission('user.security'))
const writable = computed(() => online.value && !submitting.value && !stale.value && !writeForbidden.value)
const statusLabels: Record<string, string> = { ACTIVE:'正常', RESTRICTED:'受限', FROZEN:'已冻结', CANCEL_PENDING:'注销处理中', CANCELLED:'已注销' }
const identityStatusLabels: Record<string, string> = { NOT_STARTED:'未实名', SESSION_CREATED:'待活体检测', LIVENESS_PENDING:'活体检测中', PROVIDER_PROCESSING:'核验中', MANUAL_REVIEW:'待人工复核', VERIFIED:'已实名', REJECTED:'未通过', EXPIRED:'已过期' }
const membershipStatusLabels: Record<string, string> = { ACTIVE:'有效会员', PENDING:'待生效', EXPIRED:'已过期', CANCELLED:'已取消' }
function label(value?: string) { return value ? (statusLabels[value] ?? '其他状态') : '暂无' }
function identityLabel(value?: string) { return value ? (identityStatusLabels[value] ?? '状态待确认') : '未实名' }
function membershipLabel(value?: string) { return value ? (membershipStatusLabels[value] ?? '状态待确认') : '非会员' }
function formatDate(value?: string) { return value ? new Intl.DateTimeFormat('zh-CN',{dateStyle:'long',timeStyle:'short'}).format(new Date(value)) : '暂无' }

async function load() {
  if (!online.value) { loading.value=false; error.value={message:'当前设备离线，请恢复网络后重试'}; return }
  activeRequest?.abort()
  const request=new AbortController(); activeRequest=request; loading.value=true; error.value=undefined; missing.value=false
  try { user.value=await adminUsersApi.getUser(userId.value,{signal:request.signal}); forbidden.value=false; stale.value=false }
  catch(caught) {
    if(isApiRequestError(caught)) {
      if(caught.code==='REQUEST_ABORTED') return
      if(caught.status===401) { adminSession.clear(); await router.replace({path:'/auth/login',query:{redirect:route.fullPath}}); return }
      forbidden.value=caught.status===403; missing.value=caught.status===404
      error.value={message:caught.message,requestId:caught.requestId}
    } else error.value={message:'用户详情加载失败，请稍后重试'}
  } finally { if(activeRequest===request) activeRequest=undefined; loading.value=false }
}
function openAction(action: UserAction) {
  notice.value=undefined
  form.restrictionType=''
  form.reason=''
  form.expiresAt=''
  modal.value=action
}
function closeAction() { if(!submitting.value) modal.value=undefined }
function applyOutcome(outcome: AdminUserCommandOutcome) {
  if ('id' in outcome) user.value=outcome
}
async function submitAction() {
  if(!modal.value||!user.value||!writable.value) return
  const action=modal.value
  const reason=form.reason.trim()
  const type=form.restrictionType.trim()
  if(action!=='removeRestriction'&&!reason) { notice.value={tone:'danger',title:'请填写操作原因',detail:'原因将进入安全审计，不能为空。'}; return }
  if((action==='restrict'||action==='removeRestriction')&&!type) { notice.value={tone:'danger',title:'请填写限制类型',detail:'限制类型不能为空。'}; return }
  submitting.value=true; notice.value=undefined
  try {
    let outcome: AdminUserCommandOutcome
    if(action==='restrict') outcome=await adminUsersApi.restrict(userId.value,{restrictionType:type,reason,expiresAt:form.expiresAt?new Date(form.expiresAt).toISOString():undefined,expectedVersion:user.value.version})
    else if(action==='removeRestriction') outcome=await adminUsersApi.removeRestriction(userId.value,type)
    else if(action==='freeze') outcome=await adminUsersApi.freeze(userId.value,{reason,expectedVersion:user.value.version})
    else if(action==='unfreeze') outcome=await adminUsersApi.unfreeze(userId.value,{reason,expectedVersion:user.value.version})
    else outcome=await adminUsersApi.forceLogout(userId.value,{reason,expectedVersion:user.value.version})
    applyOutcome(outcome)
    const businessNo='businessNo' in outcome?outcome.businessNo:undefined
    const pending=businessNo!==undefined&&outcome.status==='PENDING_APPROVAL'
    notice.value=pending
      ? {tone:'warning',title:'冻结申请已提交',detail:`审批单 ${businessNo||'已创建'}，需要另一名管理员复核后才会冻结。`}
      : {tone:'success',title:'操作已完成',detail:'服务端状态、数据版本和审计记录已同步更新。'}
    modal.value=undefined
    if(!('id' in outcome)) await load()
  } catch(caught) {
    if(isApiRequestError(caught)) {
      if(caught.status===401) { adminSession.clear(); await router.replace({path:'/auth/login',query:{redirect:route.fullPath}}); return }
      if(caught.status===403) { writeForbidden.value=true; modal.value=undefined }
      if(caught.status===409) { stale.value=true; notice.value={tone:'warning',title:'数据已经变化',detail:'已阻止覆盖服务端新版本，请刷新详情后重新确认。',requestId:caught.requestId} }
      else notice.value={tone:'danger',title:caught.status===422?'当前状态不允许该操作':'操作未完成',detail:caught.message,requestId:caught.requestId}
    } else notice.value={tone:'danger',title:'操作未完成',detail:'网络异常或服务暂时不可用；未自动重试写操作，请稍后人工重试。'}
  } finally { submitting.value=false }
}
const actionTitle = computed(() => ({restrict:'新增或修改限制',removeRestriction:'解除限制',freeze:'冻结账号',unfreeze:'解冻账号',forceLogout:'强制下线'}[modal.value??'restrict']))
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
    <section v-else-if="error&&!user" class="card error-state"><div class="avatar centered-mark">!</div><h2>{{missing?'用户不存在':forbidden?'无权查看该用户':'详情暂时无法加载'}}</h2><p>{{error.message}}</p><div><button v-if="!forbidden&&!missing" class="primary-button" :disabled="!online" @click="load">重新加载</button></div></section>
    <template v-else-if="user&&!forbidden">
      <StatusNotice v-if="notice" :tone="notice.tone" :title="notice.title" :detail="notice.detail" :request-id="notice.requestId"/>
      <StatusNotice v-if="stale" tone="warning" title="当前详情需要刷新" detail="检测到版本冲突，所有写操作已暂停。刷新并核对最新状态后才能继续。"/>
      <section class="security-hero"><div class="card user-profile-hero"><div class="summary-top"><span class="avatar user-avatar">{{(user.nickname||user.id).slice(0,1)}}</span><div><strong>{{user.nickname||`用户 ${user.id}`}}</strong><small>{{user.phoneMasked||'未绑定手机号'}}</small></div></div><div class="button-row"><span class="status-chip" :data-status="user.status">{{label(user.status)}}</span><span class="tag tag-success">服务端已脱敏</span></div></div><div class="card account-summary"><strong>数据版本</strong><span class="metric-value">v{{user.version}}</span><small>创建于 {{formatDate(user.createdAt)}}</small></div></section>
      <section class="card"><div class="section-heading"><div><h2>账号管控</h2><p>操作使用当前数据版本 v{{user.version}}，写入后生成审计记录；冻结必须由另一名管理员复核。</p></div><span v-if="writeForbidden" class="tag tag-warning">写权限已收回</span></div><div class="button-row"><button v-if="canRestrict&&['ACTIVE','RESTRICTED','FROZEN'].includes(user.status)" class="secondary-button" :disabled="!writable" @click="openAction('restrict')">新增或修改限制</button><button v-if="canRestrict&&user.status==='RESTRICTED'" class="ghost-button" :disabled="!writable" @click="openAction('removeRestriction')">解除限制</button><button v-if="canFreeze&&['ACTIVE','RESTRICTED'].includes(user.status)" class="danger-button" :disabled="!writable" @click="openAction('freeze')">申请冻结</button><button v-if="canFreeze&&user.status==='FROZEN'" class="secondary-button" :disabled="!writable" @click="openAction('unfreeze')">解冻账号</button><button v-if="canSecure&&['ACTIVE','RESTRICTED','FROZEN'].includes(user.status)" class="ghost-button" :disabled="!writable" @click="openAction('forceLogout')">强制下线</button><span v-if="!canRestrict&&!canFreeze&&!canSecure" class="field-help">当前会话仅有读取权限。</span></div></section>
      <nav class="detail-tabs" aria-label="用户详情分区"><button v-for="tab in tabs" :key="tab" :class="{active:activeTab===tab}" @click="activeTab=tab">{{tab}}</button></nav>
      <section class="card detail-panel">
        <div v-if="activeTab==='概览'" class="facts-grid"><dl class="facts"><dt>用户编号</dt><dd>{{user.id}}</dd><dt>账号状态</dt><dd>{{label(user.status)}}</dd><dt>实名状态</dt><dd>{{identityLabel(user.identityStatus)}}</dd><dt>会员状态</dt><dd>{{membershipLabel(user.membershipStatus)}}</dd></dl><dl class="facts"><dt>手机号</dt><dd>{{user.phoneMasked||'暂无'}}</dd><dt>创建时间</dt><dd>{{formatDate(user.createdAt)}}</dd><dt>数据版本</dt><dd>v{{user.version}}</dd></dl></div>
        <dl v-else-if="activeTab==='资料'" class="facts"><dt>昵称</dt><dd>{{user.nickname||'暂无'}}</dd><dt>头像</dt><dd>{{user.avatarUrl?'已设置':'暂无'}}</dd><dt>个人简介</dt><dd>{{user.bio||'暂无'}}</dd></dl>
        <dl v-else-if="activeTab==='实名'" class="facts"><dt>实名状态</dt><dd>{{identityLabel(user.identityStatus)}}</dd><dt>信息范围</dt><dd>当前页面不展示身份证件原文</dd></dl>
        <dl v-else-if="activeTab==='奖励'" class="facts"><dt>会员状态</dt><dd>{{membershipLabel(user.membershipStatus)}}</dd><dt>敏感金额</dt><dd>当前读取契约未返回金额字段</dd></dl>
        <div v-else class="empty-state compact-empty"><div class="avatar centered-mark">0</div><h2>暂无已产生的数据</h2><p>当前用户在“{{activeTab}}”分区没有可展示的记录。</p></div>
      </section>
    </template>
    <div v-if="modal&&user" class="modal-backdrop" @click.self="closeAction"><section class="modal" role="dialog" aria-modal="true" aria-labelledby="user-action-title"><header class="modal-header"><div><h2 id="user-action-title">{{actionTitle}}</h2><p>目标：{{user.nickname||`用户 ${user.id}`}}；当前状态：{{label(user.status)}}；服务端版本：v{{user.version}}。</p></div><button class="icon-button" aria-label="关闭" :disabled="submitting" @click="closeAction">×</button></header><form class="form-stack" @submit.prevent="submitAction"><label v-if="modal==='restrict'||modal==='removeRestriction'" class="field"><span class="field-label">限制类型</span><input v-model="form.restrictionType" class="input" maxlength="128" required placeholder="例如 LOGIN"/><span class="field-help">必须使用服务端支持的限制类型；解除时应与原类型完全一致。</span></label><label v-if="modal!=='removeRestriction'" class="field"><span class="field-label">操作原因</span><textarea v-model="form.reason" class="input" maxlength="2000" required placeholder="请说明操作依据和影响范围"/><span class="field-help">原因会写入安全审计，请勿填写无关个人敏感信息。</span></label><label v-if="modal==='restrict'" class="field"><span class="field-label">过期时间（可选）</span><input v-model="form.expiresAt" class="input" type="datetime-local"/></label><StatusNotice :tone="modal==='freeze'||modal==='forceLogout'?'warning':'danger'" :title="modal==='freeze'?'双人审批确认':modal==='forceLogout'?'会话影响确认':'状态变更确认'" :detail="modal==='freeze'?'提交后仅创建冻结审批，必须由另一名管理员复核；复核通过会撤销该用户全部会话。':modal==='forceLogout'?'执行后该用户全部活跃会话立即失效，需要重新登录。':'执行后将修改用户状态与数据版本，请核对目标、类型和原因。'"/><div class="modal-footer"><button type="button" class="ghost-button" :disabled="submitting" @click="closeAction">取消</button><button :class="modal==='freeze'||modal==='forceLogout'?'danger-button':'primary-button'" :disabled="!writable">{{submitting?'提交中…':`确认${actionTitle}`}}</button></div></form></section></div>
  </article>
</template>
