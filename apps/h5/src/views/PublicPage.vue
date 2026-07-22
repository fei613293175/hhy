<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import type { H5Page } from '../catalog';
import { publicShareApi, PublicShareApiError, type PublicSharePage } from '../services/publicShare';

const props = defineProps<{ page: H5Page }>();
const route = useRoute();
const sharePageIds = new Set(['H5-004', 'H5-005', 'H5-006', 'H5-007']);
const isShare = computed(() => sharePageIds.has(props.page.ID));
const data = ref<PublicSharePage>();
const loading = ref(false);
const error = ref<{ title: string; detail: string; requestId?: string }>();
let request: AbortController | undefined;

const blocks = computed(() => [...(data.value?.content ?? [])].sort((a, b) => a.sortOrder - b.sortOrder));
const hero = computed(() => blocks.value.find((block) => block.blockType === 'HERO'));
const heroMedia = computed(() => hero.value?.media?.[0]);
const downloadUrl = computed(() => data.value?.download?.downloadUrl);

function stateFor(caught: unknown) {
  if (caught instanceof PublicShareApiError) {
    if (caught.status === 404) return { title: '内容已下架或链接失效', detail: '该内容可能已被发布者下架、删除或不再公开。', requestId: caught.requestId };
    if (caught.status === 403) return { title: '内容暂不可访问', detail: '当前分享内容不满足公开展示条件。', requestId: caught.requestId };
    if (caught.status === 0) return { title: '网络连接失败', detail: '请检查网络连接后重新加载。' };
    return { title: '内容暂时无法加载', detail: caught.message, requestId: caught.requestId };
  }
  return { title: '内容暂时无法加载', detail: '请稍后重试。' };
}

function applySeo(page: PublicSharePage) {
  document.title = page.seoMetadata?.title || page.title || '合伙云 Pro';
  const description = page.seoMetadata?.description || page.description;
  if (!description) return;
  let element = document.querySelector<HTMLMetaElement>('meta[name="description"]');
  if (!element) {
    element = document.createElement('meta');
    element.name = 'description';
    document.head.appendChild(element);
  }
  element.content = description;
}

async function load() {
  const id = String(route.params.id ?? '');
  request?.abort();
  request = new AbortController();
  loading.value = true;
  error.value = undefined;
  try {
    data.value = await publicShareApi.get(id, request.signal);
    applySeo(data.value);
  } catch (caught) {
    if (caught instanceof PublicShareApiError && caught.code === 'REQUEST_ABORTED') return;
    error.value = stateFor(caught);
  } finally {
    loading.value = false;
  }
}

onMounted(() => { if (isShare.value) void load(); });
onBeforeUnmount(() => request?.abort());
</script>

<template>
  <main v-if="!isShare" class="hero">
    <div class="eyebrow">{{ page.ID }} · {{ page.计划版本 }}</div>
    <h1>{{ page.页面 }}</h1>
    <p>{{ page.功能摘要 }}</p>
    <div class="card"><strong>访问模型：{{ page.访问 }}</strong><p>本页面的 API、一次性令牌、回跳 state、SEO/分享元数据和错误状态已在 V1.2.2 契约与动作矩阵中冻结。当前为可编译页面骨架。</p><button>下载合伙云 App</button></div>
  </main>

  <main v-else class="share-page" :aria-busy="loading">
    <section v-if="loading" class="share-shell share-loading" aria-label="正在加载分享内容">
      <div class="share-skeleton share-skeleton-hero" />
      <div class="share-skeleton share-skeleton-title" />
      <div class="share-skeleton share-skeleton-copy" />
      <div class="share-skeleton share-skeleton-card" />
    </section>

    <section v-else-if="error" class="share-shell share-state-card">
      <div class="share-state-icon">!</div>
      <h1>{{ error.title }}</h1>
      <p>{{ error.detail }}</p>
      <small v-if="error.requestId && error.requestId !== 'missing'">请求编号 {{ error.requestId }}</small>
      <button type="button" @click="load">重新加载</button>
      <RouterLink class="share-secondary-link" to="/">浏览其他公开内容</RouterLink>
    </section>

    <article v-else-if="data" class="share-shell">
      <header class="share-hero" :class="{ 'has-media': heroMedia }">
        <img v-if="heroMedia" :src="heroMedia.url" :alt="heroMedia.altText || data.title || '项目图片'" />
        <div class="share-hero-copy">
          <span class="share-badge">合伙云公开内容</span>
          <h1>{{ data.title || hero?.heading }}</h1>
          <p>{{ data.description || hero?.body }}</p>
        </div>
      </header>

      <section
        v-for="block in blocks.filter((item) => item.blockId !== hero?.blockId)"
        :key="block.blockId"
        class="share-content-card"
        :data-block-type="block.blockType"
      >
        <h2 v-if="block.heading">{{ block.heading }}</h2>
        <p v-if="block.body" class="share-body">{{ block.body }}</p>
        <div v-if="block.media?.length" class="share-media-grid">
          <img v-for="media in block.media" :key="media.id" :src="media.url" :alt="media.altText || block.heading || '内容图片'" />
        </div>
      </section>

      <aside class="share-trust-card">
        <strong>公开内容安全提示</strong>
        <p>本页仅展示审核通过的公开信息，不展示完整联系方式。请通过合伙云 App 使用受保护的联系和沟通能力。</p>
      </aside>

      <section class="share-cta-card">
        <div><span class="share-badge">合伙云 Pro</span><h2>在 App 内继续查看</h2><p>登录后可按页面权限使用收藏、分享、联系和私聊能力。</p></div>
        <a v-if="downloadUrl" class="share-primary-link" :href="downloadUrl" rel="noopener">下载 Android App</a>
        <RouterLink v-else class="share-primary-link" to="/">查看 App 下载方式</RouterLink>
      </section>
    </article>
  </main>
</template>
