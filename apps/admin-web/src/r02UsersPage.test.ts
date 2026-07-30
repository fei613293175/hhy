// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter } from 'vue-router';
import AdminUserDetailPage from './views/AdminUserDetailPage.vue';
import { adminSession, adminUsersApi } from './services';

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text));
  if (!button) throw new Error(`Missing button: ${text}`);
  return button;
}

describe('R02 administrator user controls', () => {
  afterEach(() => {
    adminSession.clear();
    vi.restoreAllMocks();
  });

  it('shows permission-bound actions and completes the two-person freeze request flow', async () => {
    adminSession.apply({
      accessToken: 'admin-token',
      adminUserId: '7',
      displayName: '安全管理员',
      permissionCodes: ['user.read', 'user.restrict', 'user.freeze', 'user.security'],
      mfaRequired: 'NONE',
    });
    const user = {
      id: '42', nickname: '测试用户', phoneMasked: '138****5678',
      status: 'ACTIVE', version: 3, createdAt: '2026-07-18T00:00:00Z',
    };
    vi.spyOn(adminUsersApi, 'getUser').mockResolvedValue(user);
    const freeze = vi.spyOn(adminUsersApi, 'freeze').mockResolvedValue({
      resourceId: '42', businessNo: 'APR-17', status: 'PENDING_APPROVAL',
      version: 0, acceptedAt: '2026-07-18T08:00:00Z',
    });
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/users/:id', component: AdminUserDetailPage },
        { path: '/users', component: { template: '<div>用户列表</div>' } },
        { path: '/auth/login', component: { template: '<div>登录</div>' } },
      ],
    });
    await router.push('/users/42');
    await router.isReady();
    const wrapper = mount(AdminUserDetailPage, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.text()).toContain('新增或修改限制');
    expect(wrapper.text()).toContain('申请冻结');
    expect(wrapper.text()).toContain('强制下线');
    await buttonByText(wrapper, '申请冻结').trigger('click');
    expect(wrapper.text()).toContain('必须由另一名管理员复核');
    await wrapper.get('textarea').setValue('异常行为复核');
    await buttonByText(wrapper, '确认冻结账号').trigger('submit');
    await flushPromises();

    expect(freeze).toHaveBeenCalledWith('42', { reason: '异常行为复核', expectedVersion: 3 });
    expect(wrapper.text()).toContain('冻结申请已提交');
    expect(wrapper.text()).toContain('APR-17');
    wrapper.unmount();
  });
});
