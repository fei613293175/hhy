// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter } from 'vue-router';
import { adminIdentitiesApi, adminSession } from './services';
import AdminIdentityDetailPage from './views/AdminIdentityDetailPage.vue';
import AdminIdentityListPage from './views/AdminIdentityListPage.vue';

function authorize() {
  adminSession.apply({
    accessToken: 'admin-token',
    adminUserId: '7',
    displayName: '实名复核员',
    permissionCodes: ['identity.read', 'identity.review', 'identity.media.view', 'identity.freeze'],
    mfaRequired: 'NONE',
  });
}

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text));
  if (!button) throw new Error(`Missing button: ${text}`);
  return button;
}

describe('R05 administrator identity pages', () => {
  afterEach(() => {
    adminSession.clear();
    vi.restoreAllMocks();
  });

  it('renders business labels without raw provider urls or failure codes', async () => {
    authorize();
    vi.spyOn(adminIdentitiesApi, 'list').mockResolvedValue({
      items: [{
        id: '77', userId: '42', status: 'REJECTED', provider: 'ALIYUN_MARKET_FACE',
        livenessUrl: 'https://signed.example.test/secret', failureCode: 'FACE_NOT_MATCH',
        expiresAt: '2026-07-19T08:00:00Z', version: 3,
      }],
      page: { page: 1, pageSize: 20, total: '1', hasMore: 'false' },
    });
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/identity', component: AdminIdentityListPage },
        { path: '/identity/:id', component: AdminIdentityDetailPage },
        { path: '/auth/login', component: { template: '<div>登录</div>' } },
      ],
    });
    await router.push('/identity');
    await router.isReady();
    const wrapper = mount(AdminIdentityListPage, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.text()).toContain('未通过');
    expect(wrapper.text()).toContain('人脸与证件不一致');
    expect(wrapper.text()).toContain('实名认证服务');
    expect(wrapper.text()).not.toContain('ALIYUN_MARKET_FACE');
    expect(wrapper.text()).not.toContain('FACE_NOT_MATCH');
    expect(wrapper.text()).not.toContain('signed.example.test');
    expect(wrapper.text()).not.toContain('requestId');
    wrapper.unmount();
  });

  it('submits review with the current server version and no page-local dto', async () => {
    authorize();
    const resource = {
      id: '77', userId: '42', status: 'MANUAL_REVIEW', provider: 'ALIYUN_MARKET_FACE',
      expiresAt: '2026-07-19T08:00:00Z', version: 3,
    };
    vi.spyOn(adminIdentitiesApi, 'detail').mockResolvedValue(resource);
    const review = vi.spyOn(adminIdentitiesApi, 'review').mockResolvedValue({
      ...resource, status: 'COMPLETED', version: 4,
    });
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/identity/:id', component: AdminIdentityDetailPage },
        { path: '/identity', component: AdminIdentityListPage },
        { path: '/auth/login', component: { template: '<div>登录</div>' } },
      ],
    });
    await router.push('/identity/42');
    await router.isReady();
    const wrapper = mount(AdminIdentityDetailPage, { global: { plugins: [router] } });
    await flushPromises();

    await buttonByText(wrapper, '人工复核').trigger('click');
    await wrapper.get('textarea').setValue('活体与证件信息一致');
    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(review).toHaveBeenCalledWith('77', {
      decision: 'APPROVE', reason: '活体与证件信息一致', expectedVersion: 3, evidenceIds: [],
    });
    expect(wrapper.text()).toContain('复核结论已提交');
    expect(wrapper.text()).toContain('已通过');
    wrapper.unmount();
  });
});
