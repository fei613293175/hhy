<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { H5Page } from '../catalog';
import {
  InviteApiError,
  inviteRegistrationApi,
  type ChallengeResource,
} from '../services/inviteRegistration';

defineProps<{ page: H5Page }>();

type LoadState = 'loading' | 'ready' | 'invalid' | 'unavailable' | 'offline';

const route = useRoute();
const router = useRouter();
const state = ref<LoadState>('loading');
const pageTitle = ref('加入合伙云');
const pageDescription = ref('接受好友邀请，完成安全注册后下载合伙云 App。');
const form = reactive({ phone: '', password: '', passwordConfirmation: '', proof: '' });
const challenge = ref<ChallengeResource>();
const challengeBusy = ref(false);
const registerBusy = ref(false);
const challengeVisible = ref(false);
const notice = ref('');
let loadController: AbortController | undefined;

const inviteCode = computed(() => {
  const value = route.params.code;
  return typeof value === 'string' ? value : '';
});
const safeChallengeImage = computed(() => {
  const raw = challenge.value?.imageBase64?.trim();
  if (!raw) return undefined;
  if (/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=\s]+$/i.test(raw)) return raw;
  if (/^[A-Za-z0-9+/=\s]+$/.test(raw)) return `data:image/png;base64,${raw}`;
  return undefined;
});
const canRegister = computed(() => canSubmitForm() && !registerBusy.value && !challengeBusy.value);

function createClientNonce(): string {
  if (typeof globalThis.crypto?.randomUUID !== 'function') return 'unsupported-secure-random';
  return globalThis.crypto.randomUUID();
}

async function loadConfig(): Promise<void> {
  loadController?.abort();
  loadController = new AbortController();
  state.value = 'loading';
  notice.value = '';
  try {
    const config = await inviteRegistrationApi.getConfig(inviteCode.value, {
      signal: loadController.signal,
    });
    const item = config.items[0];
    if (!item) {
      state.value = 'unavailable';
      return;
    }
    pageTitle.value = item.title?.trim() || pageTitle.value;
    pageDescription.value = item.description?.trim() || pageDescription.value;
    state.value = 'ready';
  } catch (caught) {
    if (caught instanceof InviteApiError && caught.code === 'REQUEST_ABORTED') return;
    if (caught instanceof InviteApiError && caught.status === 404) state.value = 'invalid';
    else if (caught instanceof InviteApiError && caught.code === 'NETWORK_ERROR') state.value = 'offline';
    else state.value = 'unavailable';
  }
}

async function requestChallenge(message = ''): Promise<void> {
  if (challengeBusy.value) return;
  challengeBusy.value = true;
  challengeVisible.value = true;
  notice.value = message;
  try {
    challenge.value = await inviteRegistrationApi.createChallenge({
      scene: 'REGISTER',
      clientNonce: createClientNonce(),
    });
    form.proof = '';
  } catch (caught) {
    notice.value = messageFor(caught, '安全验证暂时无法加载，请重试');
  } finally {
    challengeBusy.value = false;
  }
}

async function submitRegistration(): Promise<void> {
  notice.value = '';
  const validation = validateForm();
  if (validation) {
    notice.value = validation;
    return;
  }
  await requestChallenge();
}

async function verifyAndRegister(): Promise<void> {
  if (!challenge.value || !form.proof.trim() || registerBusy.value) return;
  registerBusy.value = true;
  notice.value = '';
  try {
    await inviteRegistrationApi.register({
      phone: form.phone,
      password: form.password,
      inviteCode: inviteCode.value,
      challengeId: challenge.value.challengeId,
      challengeProof: form.proof.trim(),
    });
    challengeVisible.value = false;
    scrubSensitiveFields();
    await router.replace({ path: '/', query: { registered: '1', invite_code: inviteCode.value } });
  } catch (caught) {
    if (caught instanceof InviteApiError && caught.status === 429) {
      notice.value = messageFor(caught, '操作过于频繁，请稍后重试');
    } else if (caught instanceof InviteApiError
        && caught.code === 'AUTH-422-SECURITY_CHALLENGE_INVALID') {
      await requestChallenge('输入不正确，请根据新图片重新输入');
    } else {
      notice.value = registrationFailureMessage(caught);
      challengeVisible.value = false;
    }
  } finally {
    registerBusy.value = false;
  }
}

function registrationFailureMessage(caught: unknown): string {
  if (!(caught instanceof InviteApiError)) return '注册服务暂时不可用，请稍后再试';
  if (caught.code === 'NETWORK_ERROR') return '网络连接失败，请检查网络后重试';
  if (caught.status === 429) {
    const seconds = caught.retryAfterSeconds ?? 60;
    return `操作过于频繁，请在 ${seconds} 秒后重试`;
  }
  if ([400, 409, 422].includes(caught.status)) {
    return '注册信息未通过，请检查手机号、密码和邀请码';
  }
  return '注册服务暂时不可用，请稍后再试';
}

function canSubmitForm(): boolean {
  return state.value === 'ready'
    && /^1[3-9]\d{9}$/.test(form.phone)
    && form.password.length >= 8
    && form.password.length <= 72
    && form.password === form.passwordConfirmation
    && inviteCode.value.length > 0;
}

function validateForm(): string | undefined {
  if (!/^1[3-9]\d{9}$/.test(form.phone)) return '请输入正确的中国大陆手机号';
  if (form.password.length < 8 || form.password.length > 72
      || !/[A-Za-z]/.test(form.password) || !/\d/.test(form.password)) {
    return '密码需为 8–72 位，并同时包含字母和数字';
  }
  if (form.password !== form.passwordConfirmation) return '两次输入的密码不一致';
  if (!inviteCode.value) return '邀请已失效，请向邀请人获取新的邀请链接';
  return undefined;
}

function messageFor(caught: unknown, fallback: string): string {
  if (!(caught instanceof InviteApiError)) return fallback;
  if (caught.code === 'NETWORK_ERROR') return '网络连接失败，请检查网络后重试';
  if (caught.status === 409) return '请求状态已变化，请刷新页面后重试';
  if (caught.status === 429) {
    const seconds = caught.retryAfterSeconds ?? 60;
    return `操作过于频繁，请在 ${seconds} 秒后重试`;
  }
  return caught.message || fallback;
}

function scrubSensitiveFields(): void {
  form.password = '';
  form.passwordConfirmation = '';
  form.proof = '';
  challenge.value = undefined;
}

function onOnline(): void {
  if (state.value === 'offline') void loadConfig();
}

onMounted(() => {
  globalThis.addEventListener('online', onOnline);
  void loadConfig();
});
onBeforeUnmount(() => {
  loadController?.abort();
  globalThis.removeEventListener('online', onOnline);
  scrubSensitiveFields();
});
</script>

<template>
  <main class="invite-page">
    <section v-if="state === 'loading'" class="invite-state" aria-live="polite">
      <div class="loading-mark" aria-hidden="true"></div>
      <h1>正在验证邀请</h1>
      <p>请稍候，正在加载注册信息。</p>
    </section>

    <section v-else-if="state !== 'ready'" class="invite-state" aria-live="assertive">
      <div class="state-symbol" aria-hidden="true">!</div>
      <h1>{{ state === 'invalid' ? '邀请已失效' : state === 'offline' ? '网络连接失败' : '暂时无法注册' }}</h1>
      <p v-if="state === 'invalid'">请向邀请人获取新的邀请链接。</p>
      <p v-else-if="state === 'offline'">请检查网络连接，恢复后可以重新加载。</p>
      <p v-else>注册页面尚未发布，请稍后再试。</p>
      <button v-if="state !== 'invalid'" type="button" @click="loadConfig">重新加载</button>
    </section>

    <section v-else class="invite-shell">
      <div class="invite-intro">
        <div class="eyebrow">安全邀请注册</div>
        <h1>{{ pageTitle }}</h1>
        <p>{{ pageDescription }}</p>
        <div class="invite-code"><span>邀请码</span><strong>{{ inviteCode }}</strong></div>
      </div>

      <form class="registration-card" novalidate @submit.prevent="submitRegistration">
        <label>
          <span>手机号</span>
          <input v-model.trim="form.phone" inputmode="tel" autocomplete="tel" maxlength="11" placeholder="请输入手机号">
        </label>
        <label>
          <span>登录密码</span>
          <input v-model="form.password" type="password" autocomplete="new-password" maxlength="72" placeholder="8–72 位字母和数字">
        </label>
        <label>
          <span>确认密码</span>
          <input v-model="form.passwordConfirmation" type="password" autocomplete="new-password" maxlength="72" placeholder="请再次输入密码">
        </label>

        <p v-if="notice" class="form-notice" role="alert">{{ notice }}</p>
        <button class="primary-button" type="submit" :disabled="!canRegister">
          {{ registerBusy ? '正在注册…' : '注册并下载 App' }}
        </button>
      </form>
    </section>

    <div v-if="challengeVisible" class="challenge-scrim" role="presentation">
      <section class="challenge-dialog" role="dialog" aria-modal="true" aria-labelledby="challenge-title">
        <h2 id="challenge-title">完成安全验证</h2>
        <p>请输入图中字符，验证通过后将继续注册</p>
        <div v-if="challengeBusy" class="challenge-loading" aria-live="polite">请稍候…</div>
        <template v-else>
          <img v-if="safeChallengeImage" class="challenge-image" :src="safeChallengeImage" alt="图形验证码图片，请根据图片输入字符">
          <button class="challenge-refresh" type="button" :disabled="registerBusy" @click="requestChallenge()">换一张</button>
          <input v-model.trim="form.proof" class="challenge-input" autocomplete="off" maxlength="32" placeholder="请输入图中字符">
          <p v-if="notice" class="form-notice" role="alert">{{ notice }}</p>
          <div class="challenge-actions">
            <button type="button" :disabled="registerBusy" @click="challengeVisible = false">取消</button>
            <button class="primary-button" type="button" :disabled="registerBusy || !form.proof" @click="verifyAndRegister">
              {{ registerBusy ? '验证中…' : '验证并继续' }}
            </button>
          </div>
        </template>
      </section>
    </div>
  </main>
</template>

<style scoped>
.challenge-scrim { position: fixed; inset: 0; z-index: var(--hhy-z-overlay); display: grid; place-items: center; padding: var(--hhy-space-24) var(--hhy-space-16); background: var(--hhy-color-overlay-scrim); }
.challenge-dialog { box-sizing: border-box; width: min(var(--hhy-component-challenge-dialog-width), 100%); max-height: min(var(--hhy-component-challenge-dialog-max-height), calc(100vh - var(--hhy-size-primary-button-height))); overflow: auto; padding: var(--hhy-space-24) var(--hhy-space-24) var(--hhy-space-20); border-radius: var(--hhy-radius-dialog); background: var(--hhy-color-background-surface); box-shadow: var(--hhy-shadow-dialog); text-align: center; }
.challenge-dialog h2 { margin: 0; color: var(--hhy-color-text-primary); font-size: var(--hhy-type-page-title-size); line-height: var(--hhy-type-page-title-line-height); }
.challenge-dialog > p { margin: var(--hhy-space-4) 0 var(--hhy-space-12); color: var(--hhy-color-text-secondary); font-size: var(--hhy-type-secondary-body-size); line-height: var(--hhy-type-secondary-body-line-height); }
.challenge-image { display: block; width: var(--hhy-component-challenge-image-width); height: var(--hhy-component-challenge-image-height); margin: 0 auto; border: var(--hhy-border-standard) solid var(--hhy-color-border-default); border-radius: var(--hhy-radius-input); object-fit: fill; }
.challenge-refresh { min-height: var(--hhy-size-primary-button-height); margin-left: auto; border: 0; background: transparent; color: var(--hhy-color-brand-primary); }
.challenge-input { box-sizing: border-box; width: 100%; height: var(--hhy-size-single-line-input-height); padding: 0 var(--hhy-component-challenge-input-horizontal-padding); border: var(--hhy-border-standard) solid var(--hhy-color-border-default); border-radius: var(--hhy-radius-input); font-size: var(--hhy-type-body-size); }
.challenge-actions { display: grid; grid-template-columns: var(--hhy-component-challenge-cancel-button-width) 1fr; gap: var(--hhy-space-12); margin-top: var(--hhy-space-12); }
.challenge-actions button { min-height: var(--hhy-size-primary-button-height); border-radius: var(--hhy-radius-button); }
.challenge-loading { min-height: var(--hhy-component-challenge-loading-min-height); display: grid; place-items: center; color: var(--hhy-color-text-secondary); }
</style>
