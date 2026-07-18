<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { H5Page } from '../catalog';
import {
  InviteApiError,
  inviteRegistrationApi,
  type ChallengeResource,
  type PublicPageBlock,
} from '../services/inviteRegistration';

defineProps<{ page: H5Page }>();

type LoadState = 'loading' | 'ready' | 'invalid' | 'unavailable' | 'offline';

const route = useRoute();
const router = useRouter();
const state = ref<LoadState>('loading');
const pageTitle = ref('加入合伙云');
const pageDescription = ref('接受好友邀请，完成安全注册后下载合伙云 App。');
const agreements = ref<PublicPageBlock[]>([]);
const accepted = reactive<Record<string, boolean>>({});
const form = reactive({ phone: '', password: '', passwordConfirmation: '', smsCode: '', proof: '' });
const challenge = ref<ChallengeResource>();
const challengeBusy = ref(false);
const smsBusy = ref(false);
const registerBusy = ref(false);
const notice = ref('');
const cooldown = ref(0);
const clientNonce = createClientNonce();
let loadController: AbortController | undefined;
let cooldownTimer: number | undefined;

const inviteCode = computed(() => {
  const value = route.params.code;
  return typeof value === 'string' ? value : '';
});
const allAgreementsAccepted = computed(() => agreements.value.length > 0
  && agreements.value.every((item) => accepted[item.blockId]));
const safeChallengeImage = computed(() => {
  const raw = challenge.value?.imageBase64?.trim();
  if (!raw) return undefined;
  if (/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=\s]+$/i.test(raw)) return raw;
  if (/^[A-Za-z0-9+/=\s]+$/.test(raw)) return `data:image/png;base64,${raw}`;
  return undefined;
});
const canSendSms = computed(() => state.value === 'ready'
  && /^1[3-9]\d{9}$/.test(form.phone)
  && Boolean(challenge.value?.challengeId)
  && form.proof.trim().length > 0
  && cooldown.value === 0
  && !smsBusy.value);
const canRegister = computed(() => canSubmitForm() && !registerBusy.value);

function createClientNonce(): string {
  if (typeof globalThis.crypto?.randomUUID !== 'function') return 'unsupported-secure-random';
  return globalThis.crypto.randomUUID();
}

function agreementName(code?: string): string {
  const names: Record<string, string> = {
    USER_SERVICE: '《用户服务协议》',
    PRIVACY_POLICY: '《隐私政策》',
    COMMUNITY_RULES: '《社区规范》',
  };
  return code ? (names[code] ?? `《${code}》`) : '《平台协议》';
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
    const blocks = item?.content?.filter((block) => block.blockType === 'RICH_TEXT') ?? [];
    if (!item || blocks.length === 0) {
      state.value = 'unavailable';
      return;
    }
    pageTitle.value = item.title?.trim() || pageTitle.value;
    pageDescription.value = item.description?.trim() || pageDescription.value;
    agreements.value = blocks;
    Object.keys(accepted).forEach((key) => delete accepted[key]);
    blocks.forEach((block) => { accepted[block.blockId] = false; });
    state.value = 'ready';
  } catch (caught) {
    if (caught instanceof InviteApiError && caught.code === 'REQUEST_ABORTED') return;
    if (caught instanceof InviteApiError && caught.status === 404) state.value = 'invalid';
    else if (caught instanceof InviteApiError && caught.code === 'NETWORK_ERROR') state.value = 'offline';
    else state.value = 'unavailable';
  }
}

async function requestChallenge(): Promise<void> {
  if (challengeBusy.value) return;
  challengeBusy.value = true;
  notice.value = '';
  try {
    challenge.value = await inviteRegistrationApi.createChallenge({
      scene: 'REGISTER',
      clientNonce,
    });
    form.proof = '';
  } catch (caught) {
    notice.value = messageFor(caught, '安全验证获取失败，请手动重试');
  } finally {
    challengeBusy.value = false;
  }
}

async function sendSms(): Promise<void> {
  if (!canSendSms.value || !challenge.value) return;
  smsBusy.value = true;
  notice.value = '';
  try {
    await inviteRegistrationApi.sendSms({
      phone: form.phone,
      scene: 'REGISTER',
      challengeId: challenge.value.challengeId,
      challengeProof: form.proof.trim(),
    });
    startCooldown(60);
    notice.value = '验证码已发送，请查看短信';
  } catch (caught) {
    if (caught instanceof InviteApiError && caught.status === 429) {
      startCooldown(caught.retryAfterSeconds ?? 60);
    }
    notice.value = messageFor(caught, '验证码发送失败，请手动重试');
  } finally {
    smsBusy.value = false;
  }
}

async function submitRegistration(): Promise<void> {
  notice.value = '';
  const validation = validateForm();
  if (validation) {
    notice.value = validation;
    return;
  }
  registerBusy.value = true;
  try {
    await inviteRegistrationApi.register({
      phone: form.phone,
      smsCode: form.smsCode.trim(),
      password: form.password,
      inviteCode: inviteCode.value,
      agreementVersions: agreements.value.map((item) => item.blockId),
    });
    scrubSensitiveFields();
    await router.replace({ path: '/', query: { registered: '1', invite_code: inviteCode.value } });
  } catch (caught) {
    if (caught instanceof InviteApiError && caught.status === 429) {
      startCooldown(caught.retryAfterSeconds ?? 60);
    }
    notice.value = messageFor(caught, '注册失败，请核对信息后手动重试');
  } finally {
    registerBusy.value = false;
  }
}

function canSubmitForm(): boolean {
  return state.value === 'ready'
    && /^1[3-9]\d{9}$/.test(form.phone)
    && /^\d{4,10}$/.test(form.smsCode.trim())
    && form.password.length >= 8
    && form.password.length <= 72
    && form.password === form.passwordConfirmation
    && allAgreementsAccepted.value;
}

function validateForm(): string | undefined {
  if (!/^1[3-9]\d{9}$/.test(form.phone)) return '请输入正确的中国大陆手机号';
  if (!/^\d{4,10}$/.test(form.smsCode.trim())) return '请输入正确的短信验证码';
  if (form.password.length < 8 || form.password.length > 72
      || !/[A-Za-z]/.test(form.password) || !/\d/.test(form.password)) {
    return '密码需为 8–72 位，并同时包含字母和数字';
  }
  if (form.password !== form.passwordConfirmation) return '两次输入的密码不一致';
  if (!allAgreementsAccepted.value) return '请阅读并同意全部协议';
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

function startCooldown(seconds: number): void {
  if (cooldownTimer !== undefined) globalThis.clearInterval(cooldownTimer);
  cooldown.value = Math.max(1, Math.ceil(seconds));
  cooldownTimer = globalThis.setInterval(() => {
    cooldown.value = Math.max(0, cooldown.value - 1);
    if (cooldown.value === 0 && cooldownTimer !== undefined) {
      globalThis.clearInterval(cooldownTimer);
      cooldownTimer = undefined;
    }
  }, 1000);
}

function scrubSensitiveFields(): void {
  form.password = '';
  form.passwordConfirmation = '';
  form.smsCode = '';
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
  if (cooldownTimer !== undefined) globalThis.clearInterval(cooldownTimer);
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

        <div class="security-panel">
          <div class="field-title">安全验证</div>
          <button class="secondary-button" type="button" :disabled="challengeBusy" @click="requestChallenge">
            {{ challengeBusy ? '正在获取…' : challenge ? '换一个验证图' : '获取安全验证' }}
          </button>
          <img v-if="safeChallengeImage" class="challenge-image" :src="safeChallengeImage" alt="安全验证图片">
          <label v-if="challenge">
            <span>验证答案</span>
            <input v-model.trim="form.proof" autocomplete="off" maxlength="2000" placeholder="请输入图片中的内容">
          </label>
        </div>

        <label>
          <span>短信验证码</span>
          <div class="inline-field">
            <input v-model.trim="form.smsCode" inputmode="numeric" autocomplete="one-time-code" maxlength="10" placeholder="请输入验证码">
            <button class="secondary-button" type="button" :disabled="!canSendSms" @click="sendSms">
              {{ cooldown > 0 ? `${cooldown} 秒` : smsBusy ? '发送中…' : '发送验证码' }}
            </button>
          </div>
        </label>

        <fieldset class="agreements">
          <legend>注册即需同意</legend>
          <label v-for="agreement in agreements" :key="agreement.blockId" class="check-row">
            <input v-model="accepted[agreement.blockId]" type="checkbox">
            <span>{{ agreementName(agreement.heading) }}</span>
          </label>
        </fieldset>

        <p v-if="notice" class="form-notice" role="alert">{{ notice }}</p>
        <button class="primary-button" type="submit" :disabled="!canRegister">
          {{ registerBusy ? '正在注册…' : '注册并下载 App' }}
        </button>
        <p class="privacy-note">密码、验证码与安全验证内容仅用于本次注册，不会保存在网页中。</p>
      </form>
    </section>
  </main>
</template>
