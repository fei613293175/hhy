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

async function load(): Promise<void> {
  const callbackState = route.query.state;
  await router.replace({ path: route.path, query: {}, hash: '' });
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

onMounted(load);
onBeforeUnmount(() => controller?.abort());
</script>

<template>
  <main class="identity-callback-page">
    <section class="identity-callback-card" aria-live="polite">
      <div class="brand-mark" aria-hidden="true">合</div>
      <p class="brand-name">合伙云 Pro</p>
      <div v-if="viewState === 'loading'" class="callback-spinner" aria-hidden="true" />
      <div v-else class="callback-symbol" :class="`is-${viewState}`" aria-hidden="true">
        {{ viewState === 'verified' ? '✓' : viewState === 'unfinished' || viewState === 'expired' ? '!' : '→' }}
      </div>
      <h1>{{ content[0] }}</h1>
      <p class="callback-message">{{ content[1] }}</p>
      <button v-if="viewState !== 'loading'" class="primary-button" type="button" @click="returnToApp">
        返回合伙云
      </button>
      <p class="callback-trust">认证信息由合伙云安全处理</p>
    </section>
  </main>
</template>
