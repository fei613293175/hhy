// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { H5Page } from '../catalog';
import { router as appRouter } from '../router';
import { PublicDocumentApiError, publicDocumentApi } from '../services/publicDocuments';
import DocumentPage from './DocumentPage.vue';

vi.mock('../services/publicDocuments', async (importOriginal) => {
  const original = await importOriginal<typeof import('../services/publicDocuments')>();
  return {
    ...original,
    publicDocumentApi: { getAgreement: vi.fn(), getHelpArticle: vi.fn() },
  };
});

const api = vi.mocked(publicDocumentApi);
const agreementPage: H5Page = {
  ID: 'H5-010', 页面: '协议与规则', 路由: '/agreement/{code}', vueRoute: '/agreement/:code',
  访问: '公开', 功能摘要: '版本化协议', 计划版本: 'R15',
};
const helpPage: H5Page = {
  ID: 'H5-011', 页面: '帮助文章', 路由: '/help/{id}', vueRoute: '/help/:id',
  访问: '公开', 功能摘要: '帮助内容', 计划版本: 'R15',
};

function result(code = 'USER_SERVICE') {
  return {
    requestId: 'req-r15-document',
    timestamp: '2026-08-03T08:30:00Z',
    page: {
      code,
      title: code === 'USER_SERVICE' ? '用户服务协议' : '如何创建项目',
      description: '请在使用服务前仔细阅读',
      version: 6,
      seoMetadata: { title: '合伙云协议', description: '合伙云公开协议' },
      content: [
        { blockId: 'scope', blockType: 'RICH_TEXT' as const, heading: '一、适用范围', body: '本协议适用于合伙云 Pro 服务。', sortOrder: 1 },
        { blockId: 'safe', blockType: 'CTA' as const, heading: '相关说明', sortOrder: 2, action: { targetType: 'H5_URL' as const, url: 'https://www.example.com/help', requiresLogin: false } },
        { blockId: 'unsafe', blockType: 'CTA' as const, heading: '危险链接', sortOrder: 3, action: { targetType: 'H5_URL' as const, url: 'javascript:alert(1)', requiresLogin: false } },
      ],
    },
  };
}

async function render(page = agreementPage, path = '/agreement/USER_SERVICE') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/agreement/:code', component: DocumentPage, props: { page: agreementPage } },
      { path: '/help/:id', component: DocumentPage, props: { page: helpPage } },
      { path: '/', component: { template: '<main>首页</main>' } },
    ],
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(DocumentPage, { props: { page }, global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe('DocumentPage H5-010 and H5-011', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.getAgreement.mockResolvedValue(result());
    api.getHelpArticle.mockResolvedValue(result('HELP01'));
  });

  it('routes both generated R15 paths to the document product surface', () => {
    const agreement = appRouter.resolve('/agreement/USER_SERVICE');
    const help = appRouter.resolve('/help/article_15');

    expect(agreement.name).toBe('H5-010');
    expect(help.name).toBe('H5-011');
    expect(agreement.matched[0]?.components?.default).toBe(DocumentPage);
    expect(help.matched[0]?.components?.default).toBe(DocumentPage);
  });

  it('renders agreement metadata, table of contents, body, SEO and safe related actions', async () => {
    const wrapper = await render();

    expect(api.getAgreement).toHaveBeenCalledWith('USER_SERVICE', expect.any(AbortSignal));
    expect(wrapper.text()).toContain('用户服务协议');
    expect(wrapper.text()).toContain('本文目录');
    expect(wrapper.text()).toContain('本协议适用于合伙云 Pro 服务');
    expect(wrapper.text()).toContain('V6');
    expect(document.title).toBe('合伙云协议');
    expect(wrapper.find('a[href="https://www.example.com/help"]').exists()).toBe(true);
    expect(wrapper.find('a[href^="javascript:"]').exists()).toBe(false);
  });

  it('uses the public help API for H5-011', async () => {
    const wrapper = await render(helpPage, '/help/article_15');

    expect(api.getHelpArticle).toHaveBeenCalledWith('article_15', expect.any(AbortSignal));
    expect(wrapper.text()).toContain('帮助中心');
    expect(wrapper.text()).toContain('如何创建项目');
  });

  it('shows the explicit not-found state without retrying a terminal resource', async () => {
    api.getHelpArticle.mockRejectedValue(new PublicDocumentApiError(404, 'COMMON-404-NOT_FOUND', 'raw detail', 'req-404'));

    const wrapper = await render(helpPage, '/help/missing');

    expect(wrapper.text()).toContain('内容未找到');
    expect(wrapper.text()).toContain('该帮助文章不存在、已删除或不再公开');
    expect(wrapper.text()).not.toContain('raw detail');
    expect(wrapper.text()).not.toContain('重新加载');
  });

  it('keeps already loaded content visible and marks it stale when refresh goes offline', async () => {
    const wrapper = await render();
    api.getAgreement.mockRejectedValueOnce(new PublicDocumentApiError(0, 'NETWORK_ERROR', 'network'));

    await wrapper.get('.document-refresh').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('用户服务协议');
    expect(wrapper.text()).toContain('网络不可用，当前展示的是本次访问中已加载的内容');
  });

  it('shows a stable request id for recoverable server failures', async () => {
    api.getAgreement.mockRejectedValue(new PublicDocumentApiError(500, 'COMMON-500-INTERNAL', 'database detail', 'req-r15-500'));

    const wrapper = await render();

    expect(wrapper.text()).toContain('加载失败');
    expect(wrapper.text()).toContain('请求编号：req-r15-500');
    expect(wrapper.text()).not.toContain('database detail');
    expect(wrapper.text()).toContain('重新加载');
  });

  it('reloads once after a version conflict and then renders the latest document', async () => {
    api.getAgreement
      .mockRejectedValueOnce(new PublicDocumentApiError(409, 'COMMON-409-VERSION_CONFLICT', 'conflict'))
      .mockResolvedValueOnce(result());

    const wrapper = await render();

    expect(api.getAgreement).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('用户服务协议');
  });
});
