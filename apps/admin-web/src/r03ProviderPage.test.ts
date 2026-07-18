// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ProviderConfigPage from './views/ProviderConfigPage.vue';
import { adminProviderConfigsApi, adminSession } from './services';

const global = {
  stubs: {
    RouterLink: { props: ['to'], template: '<a><slot /></a>' },
  },
};

function authorize() {
  adminSession.apply({
    accessToken: 'admin-token',
    adminUserId: '7',
    displayName: '配置管理员',
    permissionCodes: ['config.manage'],
    mfaRequired: 'NONE',
  });
}

describe('R03 provider configuration pages', () => {
  afterEach(() => {
    adminSession.clear();
    vi.restoreAllMocks();
  });

  it('renders the provider center from the frozen list resource', async () => {
    authorize();
    vi.spyOn(adminProviderConfigsApi, 'list').mockResolvedValue({
      items: [
        { provider: 'sms', environment: 'STAGING', activeVersion: 'sms-v1', connectionStatus: 'ACTIVE', version: 3 },
        { provider: 'storage', environment: 'STAGING', draftVersion: 'storage-v2', connectionStatus: 'VALIDATED', version: 2 },
      ],
      page: { page: 1, pageSize: 20, total: '2', hasMore: 'false' },
    });

    const wrapper = mount(ProviderConfigPage, { global });
    await flushPromises();

    expect(wrapper.text()).toContain('供应商配置中心');
    expect(wrapper.text()).toContain('阿里云短信');
    expect(wrapper.text()).toContain('sms-v1');
    expect(wrapper.text()).toContain('storage-v2');
    expect(wrapper.text()).toContain('激活受严格门禁保护');
    wrapper.unmount();
  });

  it('shows only masked SecretRef metadata on a provider detail page', async () => {
    authorize();
    vi.spyOn(adminProviderConfigsApi, 'get').mockResolvedValue({
      provider: 'identity',
      environment: 'STAGING',
      activeVersion: 'identity-v1',
      configuredSecrets: [{
        key: 'identity.provider.appcode',
        configured: true,
        secretRefMasked: 'vault://***/ap****',
      }],
      connectionStatus: 'CONNECTION_TESTED',
      lastTestAt: '2026-07-18T13:50:00Z',
      version: 4,
    });

    const wrapper = mount(ProviderConfigPage, { props: { provider: 'identity' }, global });
    await flushPromises();

    expect(wrapper.text()).toContain('实名认证');
    expect(wrapper.text()).toContain('identity.provider.appcode');
    expect(wrapper.text()).toContain('vault://***/ap****');
    expect(wrapper.text()).toContain('连接测试通过');
    expect(wrapper.text()).not.toContain('actual-appcode');
    wrapper.unmount();
  });

  it('does not call the API when config.manage is absent', async () => {
    adminSession.apply({
      accessToken: 'admin-token',
      adminUserId: '8',
      displayName: '只读管理员',
      permissionCodes: [],
      mfaRequired: 'NONE',
    });
    const list = vi.spyOn(adminProviderConfigsApi, 'list');

    const wrapper = mount(ProviderConfigPage, { global });
    await flushPromises();

    expect(list).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('无配置管理权限');
    wrapper.unmount();
  });
});
