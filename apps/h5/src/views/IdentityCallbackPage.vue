<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { H5Page } from '../catalog';
import {
  IdentityCallbackApiError,
  identityCallbackApi,
} from '../services/identityCallback';

defineProps<{ page: H5Page }>();

type ViewState = 'loading' | 'processing' | 'review' | 'verified' | 'unfinished' | 'expired' | 'offline' | 'unavailable';

const route = useRoute();
const router = useRouter();
const viewState = ref<ViewState>('loading');
let controller: AbortController | undefined;
let consumedState: string | undefined;

const content = computed(() => ({
  loading: ['正在确认认证状态', '请稍候，本页会自动完成。'],
  processing: ['认证信息已提交', '请返回合伙云查看最新结果。'],
  review: ['资料已提交审核', '审核完成后可在合伙云中查看结果。'],
  verified: ['实名认证已完成', '现在可以返回合伙云继续使用。'],
  unfinished: ['本次认证未完成', '请返回合伙云重新进行认证。'],
  expired: ['认证页面已失效', '请返回合伙云重新开始。'],
  offline: ['网络连接失败', '请检查网络后返回合伙云查看结果。'],
  unavailable: ['暂时无法确认认证状态', '请返回合伙云稍后查看。'],
}[viewState.value]));

const summary = computed(() => ({
  loading: ['处理中', '返回合伙云查看'],
  processing: ['处理中', '返回合伙云查看'],
  review: ['处理中', '返回合伙云查看'],
  verified: ['已完成', '返回合伙云查看'],
  unfinished: ['需要处理', '返回合伙云查看'],
  expired: ['需要处理', '返回合伙云重新发起'],
  offline: ['需要处理', '恢复网络后重试'],
  unavailable: ['需要处理', '稍后返回合伙云查看'],
}[viewState.value]));

const symbol = computed(() => {
  if (viewState.value === 'verified') return '✓';
  if (viewState.value === 'unfinished' || viewState.value === 'expired') return '!';
  if (viewState.value === 'offline') return '⌁';
  return '◷';
});

function validState(value: unknown): value is string {
  return typeof value === 'string'
    && /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$/.test(value);
}

function present(status: string): ViewState {
  if (status === 'VERIFIED') return 'verified';
  if (status === 'MANUAL_REVIEW') return 'review';
  if (status === 'REJECTED' || status === 'EXPIRED') return 'unfinished';
  return 'processing';
}

async function load(retry = false): Promise<void> {
  const callbackState = retry ? consumedState : route.query.state;
  if (!retry) {
    if (validState(callbackState)) consumedState = callbackState;
    await router.replace({ path: route.path, query: {}, hash: '' });
  }
  if (!validState(callbackState)) {
    viewState.value = 'expired';
    return;
  }
  controller = new AbortController();
  try {
    const result = await identityCallbackApi.consume(callbackState, controller.signal);
    viewState.value = present(result.status);
  } catch (caught) {
    if (caught instanceof IdentityCallbackApiError && caught.code === 'REQUEST_ABORTED') return;
    if (caught instanceof IdentityCallbackApiError && caught.status === 422) viewState.value = 'expired';
    else if (caught instanceof IdentityCallbackApiError && caught.code === 'NETWORK_ERROR') viewState.value = 'offline';
    else viewState.value = 'unavailable';
  }
}

function returnToApp(): void {
  window.location.assign('hhy://identity/result');
}

function primaryAction(): void {
  if (viewState.value === 'offline' && consumedState) {
    viewState.value = 'loading';
    void load(true);
    return;
  }
  returnToApp();
}

onMounted(load);
onBeforeUnmount(() => controller?.abort());
</script>

<template>
  <main class="identity-callback-page">
    <header class="identity-callback-header">
      <div class="callback-brand"><span class="brand-mark" aria-hidden="true">合</span><strong>合伙云 Pro</strong></div>
      <h1>实名认证</h1>
      <p>安全确认认证结果，并返回合伙云继续使用</p>
    </header>
    <section class="identity-callback-card" aria-live="polite">
      <div v-if="viewState === 'loading'" class="callback-spinner" aria-hidden="true" />
      <div v-else class="callback-symbol" :class="`is-${viewState}`" aria-hidden="true">{{ symbol }}</div>
      <h1>{{ content[0] }}</h1>
      <p class="callback-message">{{ content[1] }}</p>
      <dl class="callback-summary">
        <div><dt>当前状态</dt><dd>{{ summary[0] }}</dd></div>
        <div><dt>下一步</dt><dd>{{ summary[1] }}</dd></div>
      </dl>
      <button v-if="viewState !== 'loading'" class="primary-button" type="button" @click="primaryAction">
        {{ viewState === 'offline' ? '重新连接' : '返回合伙云' }}
      </button>
      <p class="callback-trust">无需在此页面填写或提交任何身份信息</p>
    </section>
  </main>
</template>
