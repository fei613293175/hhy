<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { H5Page } from '../catalog'
import { paymentApi, PaymentApiError, type PaymentResource } from '../services/payment'

defineProps<{ page: H5Page }>()
const route = useRoute(); const router = useRouter(); const orderNo = computed(() => String(route.params.orderNo || ''))
const state = ref<'loading'|'ready'|'submitting'|'pending'|'error'|'offline'>('loading')
const payment = ref<PaymentResource>(); const gateway = ref<'ALIPAY'|'WECHAT_PAY'>('ALIPAY'); const message = ref('')
let request: AbortController | undefined; let timer: number | undefined
const money = computed(() => payment.value ? `${(payment.value.amountCent / 100).toFixed(2)} ${payment.value.currency}` : '--')
function key() { return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}` }
async function load() {
  request?.abort(); request = new AbortController(); state.value = 'loading'; message.value = ''
  try { payment.value = (await paymentApi.cashier(orderNo.value, request.signal)).data; state.value = 'ready' }
  catch (error) { if (error instanceof DOMException) return; state.value = error instanceof PaymentApiError && error.status === 0 ? 'offline' : 'error'; message.value = error instanceof Error ? error.message : '收银台暂时无法打开' }
}
async function pay() {
  if (!payment.value) return; state.value = 'submitting'; message.value = ''
  try {
    const response = await paymentApi.create(orderNo.value, gateway.value, `${location.origin}/payment/result`, key(), request?.signal)
    const data = response.data as PaymentResource & { returnUrl?: string; paymentUrl?: string }
    if (data && typeof data === 'object' && 'paymentUrl' in data && data.paymentUrl) location.assign(String(data.paymentUrl))
    else await router.replace({ path: '/payment/result', query: { orderNo: orderNo.value } })
  } catch (error) { state.value = error instanceof PaymentApiError && error.status === 0 ? 'offline' : 'error'; message.value = error instanceof Error ? error.message : '支付创建失败' }
}
onMounted(load); onBeforeUnmount(() => { request?.abort(); if (timer) clearTimeout(timer) })
</script>
<template>
  <main class="payment-page"><section class="payment-shell">
    <header class="payment-header"><span class="payment-brand">合伙云 Pro</span><span>安全收银台</span></header>
    <div v-if="state === 'loading'" class="payment-state"><div class="payment-spinner"/><h1>正在读取订单</h1><p>请稍候，金额以服务端报价为准。</p></div>
    <div v-else-if="state === 'error' || state === 'offline'" class="payment-state"><div class="payment-state-mark">!</div><h1>{{ state === 'offline' ? '网络连接失败' : '收银台暂时不可用' }}</h1><p>{{ message }}</p><button class="primary-button" @click="load">重新加载</button></div>
    <form v-else class="payment-form" @submit.prevent="pay"><div class="payment-title"><span>订单支付</span><strong>{{ money }}</strong></div><dl class="payment-facts"><div><dt>订单号</dt><dd>{{ orderNo }}</dd></div><div><dt>支付状态</dt><dd>{{ payment?.status || '待支付' }}</dd></div><div><dt>支付网关</dt><dd>彩虹易支付</dd></div></dl><fieldset :disabled="state === 'submitting' || state === 'pending'"><legend>选择支付方式</legend><label class="gateway-option"><input v-model="gateway" type="radio" value="ALIPAY"><span>支付宝</span></label><label class="gateway-option"><input v-model="gateway" type="radio" value="WECHAT_PAY"><span>微信支付</span></label><button class="primary-button" type="submit">{{ state === 'submitting' ? '正在创建支付…' : '确认支付' }}</button></fieldset><p class="payment-notice">提交后将跳转到支付渠道；最终结果只以服务端查询为准。</p></form>
  </section></main>
</template>
