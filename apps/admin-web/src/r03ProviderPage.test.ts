// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ProviderConfigPage from './views/ProviderConfigPage.vue';
import { ApiRequestError, adminProviderConfigsApi, adminSession } from './services';

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

function detail(overrides: Record<string, unknown> = {}) {
  return {
    provider: 'sms',
    environment: 'STAGING',
    activeVersion: 'sms-v1',
    draftVersion: 'sms-v2',
    configuredSecrets: [],
    connectionStatus: 'CONNECTION_TESTED',
    lastTestAt: '2026-07-18T13:50:00Z',
    version: 4,
    ...overrides,
  };
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

  it('creates a provider version with public values and SecretRef-only secrets', async () => {
    authorize();
    vi.spyOn(adminProviderConfigsApi, 'get').mockResolvedValue(detail());
    const create = vi.spyOn(adminProviderConfigsApi, 'createVersion').mockResolvedValue(detail({ draftVersion: 'sms-v3', version: 5 }));

    const wrapper = mount(ProviderConfigPage, { props: { provider: 'sms' }, global });
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '创建新版本')!.trigger('click');
    const textareas = wrapper.findAll('textarea');
    await textareas[0].setValue('{"region":"cn-hangzhou"}');
    await textareas[1].setValue('{"sms.accessKeySecret":"vault://staging/sms/access-key"}');
    await textareas[2].setValue('轮换短信供应商凭据');
    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(create).toHaveBeenCalledWith('sms', {
      environment: 'STAGING',
      values: { region: 'cn-hangzhou' },
      secretRefs: { 'sms.accessKeySecret': 'vault://staging/sms/access-key' },
      remark: '轮换短信供应商凭据',
    });
    expect(wrapper.text()).toContain('创建配置版本已提交');
    wrapper.unmount();
  });

  it('rejects suspected plaintext secrets before issuing a write request', async () => {
    authorize();
    vi.spyOn(adminProviderConfigsApi, 'get').mockResolvedValue(detail());
    const create = vi.spyOn(adminProviderConfigsApi, 'createVersion');

    const wrapper = mount(ProviderConfigPage, { props: { provider: 'sms' }, global });
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '创建新版本')!.trigger('click');
    const textareas = wrapper.findAll('textarea');
    await textareas[0].setValue('{"password":"plaintext"}');
    await textareas[1].setValue('{}');
    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(create).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('疑似秘密字段');
    wrapper.unmount();
  });

  it('requires a tested draft before activation is available', async () => {
    authorize();
    vi.spyOn(adminProviderConfigsApi, 'get').mockResolvedValue(detail({ connectionStatus: 'VALIDATED' }));

    const wrapper = mount(ProviderConfigPage, { props: { provider: 'sms' }, global });
    await flushPromises();
    const activate = wrapper.findAll('button').find((button) => button.text() === '审批并激活')!;

    expect(activate.attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('submits approval-bound activation and rollback with the current lock version', async () => {
    authorize();
    vi.spyOn(adminProviderConfigsApi, 'get').mockResolvedValue(detail());
    const activate = vi.spyOn(adminProviderConfigsApi, 'activate').mockResolvedValue(detail({
      activeVersion: 'sms-v2', draftVersion: undefined, connectionStatus: 'ACTIVE', version: 5,
    }));
    const rollback = vi.spyOn(adminProviderConfigsApi, 'rollback').mockResolvedValue(detail({
      activeVersion: 'sms-v1', draftVersion: undefined, connectionStatus: 'ROLLED_BACK', version: 6,
    }));

    const wrapper = mount(ProviderConfigPage, { props: { provider: 'sms' }, global });
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '审批并激活')!.trigger('click');
    let inputs = wrapper.findAll('input');
    await inputs[1].setValue('approval-activate-01');
    await wrapper.find('form').trigger('submit');
    await flushPromises();
    expect(activate).toHaveBeenCalledWith('sms', {
      versionId: 'sms-v2', approvalId: 'approval-activate-01', expectedVersion: 4,
    });

    await wrapper.findAll('button').find((button) => button.text() === '回滚版本')!.trigger('click');
    inputs = wrapper.findAll('input');
    await inputs[0].setValue('sms-v1');
    await inputs[1].setValue('approval-rollback-01');
    await wrapper.find('textarea').setValue('新配置导致发送失败，恢复上一稳定版本');
    await wrapper.find('form').trigger('submit');
    await flushPromises();
    expect(rollback).toHaveBeenCalledWith('sms', {
      targetVersionId: 'sms-v1', approvalId: 'approval-rollback-01',
      reason: '新配置导致发送失败，恢复上一稳定版本', expectedVersion: 5,
    });
    wrapper.unmount();
  });

  it('freezes all writes after an optimistic-lock conflict', async () => {
    authorize();
    vi.spyOn(adminProviderConfigsApi, 'get').mockResolvedValue(detail());
    vi.spyOn(adminProviderConfigsApi, 'testConnection').mockRejectedValue(new ApiRequestError({
      status: 409, code: 'COMMON-409-VERSION_CONFLICT', message: '版本冲突', requestId: 'req-r03-409',
    }));

    const wrapper = mount(ProviderConfigPage, { props: { provider: 'sms' }, global });
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '连接测试')!.trigger('click');
    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('配置版本已经变化');
    expect(wrapper.text()).toContain('所有写操作已暂停');
    expect(wrapper.findAll('button').find((button) => button.text() === '创建新版本')!.attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });
});
