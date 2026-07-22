// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter } from 'vue-router';
import { adminContentsApi, adminSession } from './services';
import AdminContentDetailPage from './views/AdminContentDetailPage.vue';
import AdminContentListPage from './views/AdminContentListPage.vue';

function authorize() {
  adminSession.apply({ accessToken: 'admin-token', adminUserId: '9', displayName: '内容运营', permissionCodes: ['content.read'], mfaRequired: 'NONE' });
}

describe('R09 App reuse of unified content administration', () => {
  afterEach(() => { adminSession.clear(); vi.restoreAllMocks(); });

  it('filters APP through the existing content list with a business label', async () => {
    authorize();
    const list = vi.spyOn(adminContentsApi, 'list').mockResolvedValue({ items: [{ id: 'app_9', title: '协作工具', contentType: 'APP', status: 'ONLINE', version: 2 }] } as never);
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/contents', component: AdminContentListPage }, { path: '/contents/:id', component: AdminContentDetailPage }] });
    await router.push('/contents'); await router.isReady();
    const wrapper = mount(AdminContentListPage, { global: { plugins: [router] } }); await flushPromises();
    await wrapper.get('select').setValue('APP'); await wrapper.get('form').trigger('submit'); await flushPromises();

    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ contentType: 'APP' }), expect.any(Object));
    expect(wrapper.text()).toContain('应用');
    expect(wrapper.text()).not.toContain('>APP<');
    wrapper.unmount();
  });

  it('uses the existing versioned detail command for APP', async () => {
    authorize();
    vi.spyOn(adminContentsApi, 'get').mockResolvedValue({ id: 'app_9', title: '协作工具', contentType: 'APP', status: 'ONLINE', reviewStatus: 'APPROVED', version: 2 } as never);
    const command = vi.spyOn(adminContentsApi, 'command').mockResolvedValue({ status: 'ACCEPTED' } as never);
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/contents/:id', component: AdminContentDetailPage }] });
    await router.push('/contents/app_9'); await router.isReady();
    const wrapper = mount(AdminContentDetailPage, { global: { plugins: [router] } }); await flushPromises();
    await wrapper.get('.primary-button').trigger('click'); await flushPromises();

    expect(command).toHaveBeenCalledWith('adminContentPostContentsByIdOffline', 'app_9', expect.objectContaining({ expectedVersion: 2 }));
    expect(wrapper.text()).toContain('应用 · 已上架');
    wrapper.unmount();
  });
});
