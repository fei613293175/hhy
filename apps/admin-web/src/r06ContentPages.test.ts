// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter } from 'vue-router';
import { adminContentsApi, adminSession } from './services';
import AdminContentDetailPage from './views/AdminContentDetailPage.vue';
import AdminContentDictionariesPage from './views/AdminContentDictionariesPage.vue';
import AdminContentListPage from './views/AdminContentListPage.vue';

function authorize() {
  adminSession.apply({ accessToken: 'admin-token', adminUserId: '26', displayName: '内容运营管理员', permissionCodes: ['content.read'], mfaRequired: 'NONE' });
}

describe('R06 administrator content pages', () => {
  afterEach(() => { adminSession.clear(); vi.restoreAllMocks(); });

  it('renders list enums and timestamps as business labels', async () => {
    authorize();
    vi.spyOn(adminContentsApi, 'list').mockResolvedValue({ items: [{ id: 'content-project-001', title: '星桥协作平台', contentType: 'PROJECT', status: 'PENDING_REVIEW', version: 12, updatedAt: '2026-07-21T05:10:00Z' }] } as never);
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/contents', component: AdminContentListPage }, { path: '/contents/:id', component: AdminContentDetailPage }] });
    await router.push('/contents'); await router.isReady();
    const wrapper = mount(AdminContentListPage, { global: { plugins: [router] } }); await flushPromises();
    expect(wrapper.text()).toContain('项目'); expect(wrapper.text()).toContain('待审核'); expect(wrapper.text()).toContain('内容编号 content-project-001');
    expect(wrapper.text()).not.toContain('PROJECT'); expect(wrapper.text()).not.toContain('PENDING_REVIEW'); expect(wrapper.text()).not.toContain('T05:10:00Z');
    wrapper.unmount();
  });

  it('renders detail codes as safe commercial copy', async () => {
    authorize();
    vi.spyOn(adminContentsApi, 'get').mockResolvedValue({ id: 'content-project-001', title: '星桥协作平台', contentType: 'PROJECT', status: 'ONLINE', reviewStatus: 'APPROVED', version: 12, categoryCode: 'DIGITAL_SERVICE', regionCode: 'CN-31', summary: '项目协作服务。', updatedAt: '2026-07-21T05:10:00Z' } as never);
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/contents/:id', component: AdminContentDetailPage }] });
    await router.push('/contents/content-project-001'); await router.isReady();
    const wrapper = mount(AdminContentDetailPage, { global: { plugins: [router] } }); await flushPromises();
    expect(wrapper.text()).toContain('项目 · 已上架'); expect(wrapper.text()).toContain('审核通过'); expect(wrapper.text()).toContain('数字服务'); expect(wrapper.text()).toContain('上海');
    for (const raw of ['PROJECT', 'ONLINE', 'APPROVED', 'DIGITAL_SERVICE', 'CN-31', 'T05:10:00Z']) expect(wrapper.text()).not.toContain(raw);
    wrapper.unmount();
  });

  it('renders dictionary status and timestamp without raw transport values', async () => {
    authorize();
    vi.spyOn(adminContentsApi, 'dictionaries').mockResolvedValue({ items: [{ id: 'CONTENT_CATEGORY', title: '内容分类', status: 'ACTIVE', version: 6, updatedAt: '2026-07-21T03:20:00Z' }] } as never);
    const wrapper = mount(AdminContentDictionariesPage); await flushPromises();
    expect(wrapper.text()).toContain('启用'); expect(wrapper.text()).not.toContain('ACTIVE'); expect(wrapper.text()).not.toContain('T03:20:00Z');
    wrapper.unmount();
  });
});
