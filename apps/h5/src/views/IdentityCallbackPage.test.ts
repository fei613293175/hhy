// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { H5Page } from '../catalog';
import { IdentityCallbackApiError, identityCallbackApi } from '../services/identityCallback';
import IdentityCallbackPage from './IdentityCallbackPage.vue';

vi.mock('../services/identityCallback', async (importOriginal) => {
  const original = await importOriginal<typeof import('../services/identityCallback')>();
  return { ...original, identityCallbackApi: { consume: vi.fn() } };
});

const api = vi.mocked(identityCallbackApi);
const page: H5Page = {
  ID: 'H5-012', 页面: '活体回跳', 路由: '/identity/callback', vueRoute: '/identity/callback',
  访问: '一次性state', 功能摘要: '通知App轮询状态', 计划版本: 'R05',
};

async function render(state = 'ff738a6c-71a1-4647-ab50-5d6f3cb5f544') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/identity/callback', component: IdentityCallbackPage, props: { page } }],
  });
  await router.push(`/identity/callback?state=${state}`);
  await router.isReady();
  const wrapper = mount(IdentityCallbackPage, { props: { page }, global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

describe('IdentityCallbackPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.consume.mockResolvedValue({ status: 'VERIFIED' });
  });

  it('clears state from the address and renders only commercial success copy', async () => {
    const { wrapper, router } = await render();

    expect(router.currentRoute.value.fullPath).toBe('/identity/callback');
    expect(wrapper.text()).toContain('实名认证已完成');
    expect(wrapper.text()).not.toContain('ff738a6c');
    expect(wrapper.text()).not.toContain('请求编号');
    expect(wrapper.text()).not.toContain('provider');
  });

  it('rejects malformed state locally without calling the service', async () => {
    const { wrapper } = await render('invalid');
    expect(wrapper.text()).toContain('认证页面已失效');
    expect(api.consume).not.toHaveBeenCalled();
  });

  it('maps an expired one-time state to a clear business message', async () => {
    api.consume.mockRejectedValue(new IdentityCallbackApiError(
      422, 'COMMON-422-BUSINESS_RULE', '认证页面已失效',
    ));
    const { wrapper } = await render();
    expect(wrapper.text()).toContain('认证页面已失效');
    expect(wrapper.text()).not.toContain('COMMON-422');
  });

  it.each([
    ['PROVIDER_PROCESSING', '认证信息已提交'],
    ['MANUAL_REVIEW', '资料已提交审核'],
    ['REJECTED', '本次认证未完成'],
  ])('maps %s to its commercial recovery state', async (status, copy) => {
    api.consume.mockResolvedValue({ status });
    const { wrapper } = await render();
    expect(wrapper.text()).toContain(copy);
    expect(wrapper.text()).not.toContain(status);
  });

  it.each([
    [new IdentityCallbackApiError(0, 'NETWORK_ERROR', 'offline'), '网络连接失败'],
    [new IdentityCallbackApiError(503, 'COMMON-503-UNAVAILABLE', 'unavailable'), '暂时无法确认认证状态'],
  ])('shows a commercial message when callback consumption fails', async (failure, copy) => {
    api.consume.mockRejectedValue(failure);
    const { wrapper } = await render();
    expect(wrapper.text()).toContain(copy);
    expect(wrapper.text()).not.toContain(failure.code);
  });
});
