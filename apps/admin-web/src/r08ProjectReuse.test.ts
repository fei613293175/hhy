// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter } from 'vue-router';
import { adminContentsApi, adminSession } from './services';
import AdminContentDetailPage from './views/AdminContentDetailPage.vue';
import AdminContentListPage from './views/AdminContentListPage.vue';

function authorize() {
  adminSession.apply({ accessToken: 'admin-token', adminUserId: '8', displayName: '项目运营', permissionCodes: ['content.read'], mfaRequired: 'NONE' });
}

describe('R08 project reuse of the unified content administration pages', () => {
  afterEach(() => { adminSession.clear(); vi.restoreAllMocks(); });

  it('queries projects through the existing content list rather than a parallel page', async () => {
    authorize();
    const list = vi.spyOn(adminContentsApi, 'list').mockResolvedValue({ items: [{ id: 'project_8', title: '社区服务项目', contentType: 'PROJECT', status: 'ONLINE', version: 4 }] } as never);
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/contents', component: AdminContentListPage }, { path: '/contents/:id', component: AdminContentDetailPage }] });
    await router.push('/contents'); await router.isReady();
    const wrapper = mount(AdminContentListPage, { global: { plugins: [router] } }); await flushPromises();

    await wrapper.get('select').setValue('PROJECT');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ contentType: 'PROJECT' }), expect.any(Object));
    expect(wrapper.text()).toContain('项目');
    expect(wrapper.text()).not.toContain('PROJECT');
    wrapper.unmount();
  });

  it('uses the existing versioned detail operations for a project', async () => {
    authorize();
    vi.spyOn(adminContentsApi, 'get').mockResolvedValue({ id: 'project_8', title: '社区服务项目', contentType: 'PROJECT', status: 'ONLINE', reviewStatus: 'APPROVED', version: 4 } as never);
    const command = vi.spyOn(adminContentsApi, 'command').mockResolvedValue({ status: 'ACCEPTED' } as never);
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/contents/:id', component: AdminContentDetailPage }] });
    await router.push('/contents/project_8'); await router.isReady();
    const wrapper = mount(AdminContentDetailPage, { global: { plugins: [router] } }); await flushPromises();

    await wrapper.get('.primary-button').trigger('click');
    await flushPromises();

    expect(command).toHaveBeenCalledWith('adminContentPostContentsByIdOffline', 'project_8', expect.objectContaining({ expectedVersion: 4 }));
    expect(wrapper.text()).toContain('项目 · 已上架');
    wrapper.unmount();
  });
});
