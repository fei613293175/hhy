<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import StatusNotice from '../components/StatusNotice.vue'
import { adminMembershipApi, adminSession, isApiRequestError, type AdminMembershipSku, type AdminUserMembershipPage } from '../services'

const result = ref<AdminUserMembershipPage>()
const skus = ref<AdminMembershipSku[]>([])
const loading = ref(true)
const error = ref('')
const forbidden = ref(false)
const grantOpen = ref(false)
const grant = reactive({ userId: '', skuId: '', durationDays: 30, reason: '' })
const items = computed(() => result.value?.items ?? [])
function message(caught: unknown) {
  if (isApiRequestError(caught)) {
    if (caught.status === 401) adminSession.clear()
    if (caught.status === 403) forbidden.value = true
    return caught.message
  }
  return '用户会员暂时无法加载，请稍后重试'
}
async function load() {
  loading.value = true; error.value = ''
  try {
    result.value = await adminMembershipApi.users({ keyword: '' })
    if (!skus.value.length) skus.value = (await adminMembershipApi.skus()).items
  } catch (caught) { error.value = message(caught) } finally { loading.value = false }
}
async function submitGrant() {
  if (!grant.userId.trim() || !grant.reason.trim()) { error.value = '用户编号和赠送原因不能为空'; return }
  try {
    await adminMembershipApi.grant({
      userId: grant.userId.trim(), skuId: grant.skuId || undefined,
      durationDays: grant.durationDays, reason: grant.reason.trim(),
    }, 'r18-admin-grant-' + Date.now().toString(36).padEnd(16, '0'))
    grantOpen.value = false; grant.userId = ''; grant.reason = ''; await load()
  } catch (caught) { error.value = message(caught) }
}
onMounted(() => { void load() })
</script>

<template>
  <article class="page membership-page">
    <header class="page-heading">
      <div><div class="eyebrow">会员运营</div><h1>用户会员</h1><p>按真实会员状态、权益快照和人工赠送合同查看用户会员。</p></div>
      <button class="primary-button" @click="grantOpen = true">人工赠送</button>
    </header>
    <StatusNotice v-if="forbidden" tone="danger" title="没有会员读取权限" detail="需要 membership.read 权限。" />
    <StatusNotice v-if="error" tone="danger" title="操作未完成" :detail="error" />
    <section class="order-metrics" aria-label="用户会员统计">
      <div><span>当前结果</span><strong>{{ items.length }}</strong><small>用户会员记录</small></div>
      <div><span>有效会员</span><strong>{{ items.filter((item) => item.status === 'ACTIVE').length }}</strong><small>真实状态</small></div>
      <div><span>待生效</span><strong>{{ items.filter((item) => item.status === 'PENDING').length }}</strong><small>等待履约</small></div>
      <div><span>历史快照</span><strong>只读</strong><small>不可被 SKU 修改</small></div>
    </section>
    <section class="card table-card">
      <div v-if="loading" class="loading-state">正在加载用户会员…</div>
      <div v-else-if="!items.length" class="empty-state">暂无用户会员记录</div>
      <table v-else class="data-table"><thead><tr><th>用户</th><th>Pro 套餐</th><th>状态</th><th>有效期</th><th>权益</th><th>版本</th></tr></thead>
        <tbody><tr v-for="item in items" :key="item.id"><td><strong>{{ item.id || '未知用户' }}</strong></td><td>{{ item.name || item.skuId || 'Pro' }}</td><td><span class="status-chip" :data-status="item.status">{{ item.status }}</span></td><td>{{ item.startsAt || '暂无' }}<small>{{ item.expiresAt || '暂无' }}</small></td><td>{{ (item.benefits ?? []).length }} 项快照</td><td>v{{ item.version }}</td></tr></tbody>
      </table>
    </section>
    <div v-if="grantOpen" class="dialog-backdrop" @click.self="grantOpen = false">
      <section class="dialog-card"><header><h2>人工赠送 Pro</h2><button class="icon-button" aria-label="关闭" @click="grantOpen = false">×</button></header>
        <label class="field"><span class="field-label">用户编号</span><input v-model="grant.userId" class="input" maxlength="64" /></label>
        <label class="field"><span class="field-label">套餐</span><select v-model="grant.skuId" class="input"><option value="">按期限赠送</option><option v-for="sku in skus" :key="sku.id" :value="sku.skuId">{{ sku.name || sku.skuId }}</option></select></label>
        <label class="field"><span class="field-label">赠送天数</span><input v-model.number="grant.durationDays" class="input" type="number" min="1" max="3660" /></label>
        <label class="field"><span class="field-label">原因</span><textarea v-model="grant.reason" class="input" maxlength="500"></textarea></label>
        <footer><button class="ghost-button" @click="grantOpen = false">取消</button><button class="primary-button" @click="submitGrant">确认赠送</button></footer>
      </section>
    </div>
  </article>
</template>
