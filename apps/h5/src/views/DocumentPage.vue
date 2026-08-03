<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import type { H5Page } from '../catalog';
import {
  publicDocumentApi,
  PublicDocumentApiError,
  type PublicDocumentPage,
  type PublicDocumentResult,
} from '../services/publicDocuments';

const props = defineProps<{ page: H5Page }>();
const route = useRoute();
const result = ref<PublicDocumentResult>();
const loading = ref(false);
const refreshing = ref(false);
const state = ref<'CONTENT' | 'NOT_FOUND' | 'FORBIDDEN' | 'ERROR' | 'OFFLINE'>('CONTENT');
const error = ref<{ message: string; requestId?: string }>();
const resultKey = ref('');
let request: AbortController | undefined;

const isAgreement = computed(() => props.page.ID === 'H5-010');
const identifier = computed(() => String(isAgreement.value ? route.params.code ?? '' : route.params.id ?? ''));
const blocks = computed(() => [...(result.value?.page.content ?? [])].sort((a, b) => a.sortOrder - b.sortOrder));
const sections = computed(() => blocks.value.filter((block) => block.heading || block.body || block.media?.length || block.action));
const fetchedAt = computed(() => formatDate(result.value?.timestamp));

function formatDate(value?: string) {
  if (!value) return undefined;
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) return undefined;
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'long', timeStyle: 'short' }).format(date);
}

function safeHttps(value?: string) {
  if (!value) return undefined;
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && !url.username && !url.password ? url.href : undefined;
  } catch {
    return undefined;
  }
}

function blockLink(block: NonNullable<PublicDocumentPage['content']>[number]) {
  if (!block.action || !['H5_URL', 'DOWNLOAD'].includes(block.action.targetType)) return undefined;
  return safeHttps(block.action.url);
}

function safeImages(block: NonNullable<PublicDocumentPage['content']>[number]) {
  return (block.media ?? []).filter((media) => ['IMAGE', 'QR_CODE'].includes(media.mediaType) && safeHttps(media.url));
}

function sectionAnchor(block: NonNullable<PublicDocumentPage['content']>[number]) {
  return `document-section-${sections.value.findIndex((section) => section.blockId === block.blockId)}`;
}

function applySeo(page: PublicDocumentPage) {
  document.title = page.seoMetadata?.title || page.title || props.page.页面;
  const description = page.seoMetadata?.description || page.description;
  if (description) setMeta('description', description);
  if (page.seoMetadata?.robots) setMeta('robots', page.seoMetadata.robots);
  const image = safeHttps(page.seoMetadata?.ogImageUrl);
  if (image) setPropertyMeta('og:image', image);
  const canonical = safeHttps(page.seoMetadata?.canonicalUrl);
  if (canonical) setCanonical(canonical);
}

function setMeta(name: string, content: string) {
  let element = document.querySelector<HTMLMetaElement>(`meta[name="${name}"]`);
  if (!element) {
    element = document.createElement('meta');
    element.name = name;
    document.head.appendChild(element);
  }
  element.content = content;
}

function setPropertyMeta(property: string, content: string) {
  let element = document.querySelector<HTMLMetaElement>(`meta[property="${property}"]`);
  if (!element) {
    element = document.createElement('meta');
    element.setAttribute('property', property);
    document.head.appendChild(element);
  }
  element.content = content;
}

function setCanonical(href: string) {
  let element = document.querySelector<HTMLLinkElement>('link[rel="canonical"]');
  if (!element) {
    element = document.createElement('link');
    element.rel = 'canonical';
    document.head.appendChild(element);
  }
  element.href = href;
}

function mapError(caught: unknown) {
  if (!(caught instanceof PublicDocumentApiError)) {
    state.value = 'ERROR';
    error.value = { message: '服务暂时不可用，请稍后重试。' };
    return;
  }
  if (caught.status === 404) {
    state.value = 'NOT_FOUND';
    error.value = { message: isAgreement.value ? '该协议不存在、已删除或不再公开。' : '该帮助文章不存在、已删除或不再公开。' };
  } else if (caught.status === 403) {
    state.value = 'FORBIDDEN';
    error.value = { message: '当前内容暂不可访问。' };
  } else if (caught.status === 0) {
    state.value = 'OFFLINE';
    error.value = { message: result.value ? '网络不可用，当前展示的是本次访问中已加载的内容。' : '网络不可用，请恢复网络后重试。' };
  } else {
    state.value = 'ERROR';
    error.value = { message: '服务暂时不可用，请稍后重试。', requestId: caught.requestId === 'missing' ? undefined : caught.requestId };
  }
}

async function load() {
  const key = `${props.page.ID}:${identifier.value}`;
  if (result.value && resultKey.value !== key) result.value = undefined;
  request?.abort();
  request = new AbortController();
  const currentRequest = request;
  loading.value = !result.value;
  refreshing.value = Boolean(result.value);
  error.value = undefined;
  state.value = 'CONTENT';
  try {
    for (let attempt = 0; attempt < 2; attempt += 1) {
      try {
        result.value = isAgreement.value
          ? await publicDocumentApi.getAgreement(identifier.value, currentRequest.signal)
          : await publicDocumentApi.getHelpArticle(identifier.value, currentRequest.signal);
        resultKey.value = key;
        applySeo(result.value.page);
        state.value = 'CONTENT';
        return;
      } catch (caught) {
        if (caught instanceof PublicDocumentApiError && caught.code === 'REQUEST_ABORTED') return;
        if (caught instanceof PublicDocumentApiError && caught.status === 409 && attempt === 0) continue;
        mapError(caught);
        return;
      }
    }
  } finally {
    if (request === currentRequest) {
      loading.value = false;
      refreshing.value = false;
    }
  }
}

function reloadWhenOnline() {
  if (state.value === 'OFFLINE') void load();
}

watch(() => `${props.page.ID}:${identifier.value}`, () => void load(), { immediate: true });
onMounted(() => window.addEventListener('online', reloadWhenOnline));
onBeforeUnmount(() => {
  request?.abort();
  window.removeEventListener('online', reloadWhenOnline);
});
</script>

<template>
  <main class="document-page" :aria-busy="loading || refreshing">
    <section v-if="loading" class="document-shell document-loading" aria-label="正在加载公开文档">
      <div class="document-skeleton document-skeleton-kicker" />
      <div class="document-skeleton document-skeleton-title" />
      <div class="document-skeleton document-skeleton-line" />
      <div class="document-skeleton document-skeleton-body" />
    </section>

    <section v-else-if="!result && state !== 'CONTENT'" class="document-shell document-state" :data-state="state">
      <span class="document-state-mark" aria-hidden="true">{{ state === 'OFFLINE' ? '○' : '!' }}</span>
      <h1>{{ state === 'NOT_FOUND' ? '内容未找到' : state === 'FORBIDDEN' ? '内容暂不可访问' : state === 'OFFLINE' ? '网络连接失败' : '加载失败' }}</h1>
      <p>{{ error?.message }}</p>
      <small v-if="error?.requestId">请求编号：{{ error.requestId }}</small>
      <div class="document-state-actions">
        <button v-if="state !== 'NOT_FOUND' && state !== 'FORBIDDEN'" type="button" @click="load">重新加载</button>
        <RouterLink class="document-secondary-link" to="/">返回首页</RouterLink>
      </div>
    </section>

    <article v-else-if="result" class="document-shell">
      <div v-if="state === 'OFFLINE'" class="document-notice" role="status">{{ error?.message }}</div>
      <div v-else-if="refreshing" class="document-notice" role="status">正在刷新最新内容...</div>

      <header class="document-header">
        <span class="document-kicker">{{ isAgreement ? '协议与规则' : '帮助中心' }}</span>
        <h1>{{ result.page.title || page.页面 }}</h1>
        <p v-if="result.page.description">{{ result.page.description }}</p>
        <dl class="document-meta">
          <div><dt>版本</dt><dd>V{{ result.page.version }}</dd></div>
          <div v-if="fetchedAt"><dt>获取时间</dt><dd>{{ fetchedAt }}</dd></div>
        </dl>
      </header>

      <nav v-if="sections.some((section) => section.heading)" class="document-toc" aria-label="本文目录">
        <strong>本文目录</strong>
        <a v-for="section in sections.filter((item) => item.heading)" :key="section.blockId" :href="`#${sectionAnchor(section)}`">{{ section.heading }}</a>
      </nav>

      <section class="document-content" aria-label="正文">
        <section
          v-for="(block, index) in sections"
          :id="`document-section-${index}`"
          :key="block.blockId"
          class="document-section"
          :data-block-type="block.blockType"
        >
          <h2 v-if="block.heading">{{ block.heading }}</h2>
          <p v-if="block.body" class="document-body">{{ block.body }}</p>
          <div v-if="safeImages(block).length" class="document-media">
            <img
              v-for="media in safeImages(block)"
              :key="media.id"
              :src="safeHttps(media.url)"
              :alt="media.altText || block.heading || ''"
            />
          </div>
          <a v-if="blockLink(block)" class="document-primary-link" :href="blockLink(block)" target="_blank" rel="noopener noreferrer">
            {{ block.action?.targetType === 'DOWNLOAD' ? '下载合伙云 App' : '查看相关内容' }}
          </a>
        </section>
        <p v-if="sections.length === 0" class="document-empty">正文暂未发布。</p>
      </section>

      <footer class="document-footer">
        <span>内容编号：{{ result.page.code }}</span>
        <button type="button" class="document-refresh" :disabled="refreshing" @click="load">{{ refreshing ? '刷新中...' : '刷新内容' }}</button>
      </footer>
    </article>
  </main>
</template>
