<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { PaymentResource } from '../services/payment'
import { paymentApi, PaymentApiError } from '../services/payment'
const route = useRoute(); const router = useRouter(); const orderNo = String(route.query.orderNo || '')
const state = ref<'querying'|'success'|'failed'|'unknown'|'offline'|'error'>('querying'); const payment = ref<PaymentResource>(); const message = ref('')
let controller: AbortController | undefined; let timer: number | undefined
function label() { return ({ querying: '正在确认支付结果', success: '支付成功', failed: '支付失败', unknown: '暂时无法确认结果', offline: '网络连接失败', error: '结果查询失败' } as const)[state.value] }
async function load(wait = 2) { controller?.abort(); controller = new AbortController(); state.value = 'querying'; message.value = ''
  try { const data = (await paymentApi.status(orderNo, wait, controller.signal)).data; payment.value = data.items[0]; const status = payment.value?.status; state.value = status === 'SUCCESS' || status === 'PAID' ? 'success' : status === 'FAILED' ? 'failed' : 'unknown' }
  catch (error) { if (error instanceof DOMException) return; state.value = error instanceof PaymentApiError && error.status === 0 ? 'offline' : 'error'; message.value = error instanceof Error ? error.message : '暂时无法确认支付结果' }
}
function back() { void router.replace({ path: '/' }) }
onMounted(() => { void router.replace({ path: route.path, query: orderNo ? { orderNo } : {} }); if (orderNo) void load(); else { state.value = 'error'; message.value = '缺少订单号' } })
onBeforeUnmount(() => { controller?.abort(); if (timer) clearTimeout(timer) })
</script>
<template><main class="payment-page"><section class="payment-shell payment-result"><div class="payment-state"><div class="payment-state-mark" :data-state="state">{{ state === 'success' ? '✓' : state === 'querying' ? '…' : '!' }}</div><h1>{{ label() }}</h1><p v-if="message">{{ message }}</p><p v-else-if="payment">订单 {{ payment.orderNo }} · {{ (payment.amountCent / 100).toFixed(2) }} {{ payment.currency }}</p><p v-else>不会根据回跳参数直接判断结果，请稍候。</p><div class="payment-actions"><button class="primary-button" :disabled="state === 'querying'" @click="load(2)">{{ state === 'success' ? '返回合伙云' : '继续查询' }}</button><button class="ghost-button" @click="back">返回首页</button></div></div></section></main></template>
