// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter } from 'vue-router';
import PublicPage from '../views/PublicPage.vue';
import { PublicShareApi, PublicShareApiError, publicShareApi } from './publicShare';

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('PublicShareApi', () => {
  afterEach(() => vi.restoreAllMocks());
  it('loads the generated public page contract without page-local DTOs', async () => {
    const fetcher = vi.fn().mockResolvedValue(response({
      success: true,
      requestId: 'req-r08-share',
      data: {
        code: 'PROJ',
        title: '智慧社区生活服务平台',
        description: '连接社区服务供需',
        content: [{ blockId: 'hero', blockType: 'HERO', heading: '智慧社区生活服务平台', body: '连接社区服务供需', sortOrder: 0 }],
        version: 3,
      },
    }));

    const result = await new PublicShareApi('https://api.orbexa.cc', fetcher).get('project_1');

    expect(result.code).toBe('PROJ');
    expect(result.content?.[0]?.blockType).toBe('HERO');
    expect(fetcher).toHaveBeenCalledWith(new URL('https://api.orbexa.cc/public-api/v1/share/contents/project_1'), expect.any(Object));
  });

  it('maps an expired share to a stable not-found state', async () => {
    const fetcher = vi.fn().mockResolvedValue(response({ requestId: 'req-404', error: { code: 'COMMON-404-NOT_FOUND', message: '不存在' } }, 404));
    await expect(new PublicShareApi('https://api.orbexa.cc', fetcher).get('project_1')).rejects.toMatchObject({ status: 404, requestId: 'req-404' });
  });

  it('rejects malformed route ids before network access', async () => {
    const fetcher = vi.fn();
    await expect(new PublicShareApi('https://api.orbexa.cc', fetcher).get('../secret')).rejects.toMatchObject({ status: 400 });
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('renders real public project blocks and the protected-action explanation', async () => {
    vi.spyOn(publicShareApi, 'get').mockResolvedValue({
      code: 'PROJ', title: '智慧社区生活服务平台', description: '连接社区服务供需', version: 3,
      content: [
        { blockId: 'hero', blockType: 'HERO', heading: '智慧社区生活服务平台', body: '连接社区服务供需', sortOrder: 0 },
        { blockId: 'description', blockType: 'RICH_TEXT', heading: '项目说明', body: '面向社区的真实服务说明', sortOrder: 1 },
      ],
    });
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/share/project/:id', component: PublicPage }] });
    await router.push('/share/project/project_1'); await router.isReady();
    const wrapper = mount(PublicPage, { props: { page: { ID: 'H5-004' } as never }, global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.text()).toContain('智慧社区生活服务平台');
    expect(wrapper.text()).toContain('面向社区的真实服务说明');
    expect(wrapper.text()).toContain('不展示完整联系方式');
    expect(wrapper.text()).not.toContain('当前为可编译页面骨架');
    wrapper.unmount();
  });

  it('renders an explicit expired state without leaking backend details', async () => {
    vi.spyOn(publicShareApi, 'get').mockRejectedValue(new PublicShareApiError(404, 'COMMON-404-NOT_FOUND', 'raw backend message', 'req-share-404'));
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/share/project/:id', component: PublicPage }] });
    await router.push('/share/project/project_1'); await router.isReady();
    const wrapper = mount(PublicPage, { props: { page: { ID: 'H5-004' } as never }, global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.text()).toContain('内容已下架或链接失效');
    expect(wrapper.text()).not.toContain('请求编号');
    expect(wrapper.text()).not.toContain('req-share-404');
    expect(wrapper.text()).not.toContain('raw backend message');
    wrapper.unmount();
  });
});
