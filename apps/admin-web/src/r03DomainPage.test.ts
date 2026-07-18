// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import DomainConfigPage from './views/DomainConfigPage.vue';
import { ApiRequestError, adminDomainsApi, adminSession } from './services';

function authorize(permissions = ['config.manage', 'domain.read', 'domain.write', 'domain.verify']) {
  adminSession.apply({
    accessToken: 'admin-token',
    adminUserId: '9',
    displayName: '域名管理员',
    permissionCodes: permissions,
    mfaRequired: 'NONE',
  });
}

function resource(overrides: Record<string, unknown> = {}) {
  return {
    code: 'api',
    environment: 'PRODUCTION',
    hostname: 'api.orbexa.cc',
    dnsStatus: 'PASSED',
    httpsStatus: 'PASSED',
    certificateStatus: 'VALID',
    lastVerifiedAt: '2026-07-18T15:40:00Z',
    version: 5,
    ...overrides,
  };
}

function page(items = [resource()]) {
  return { items, page: { page: 1, pageSize: 20, total: String(items.length), hasMore: 'false' } };
}

function prepare(items = [resource()], actions = [resource({ dnsStatus: 'PENDING_USER_DNS' })]) {
  vi.spyOn(adminDomainsApi, 'list').mockResolvedValue(page(items));
  vi.spyOn(adminDomainsApi, 'dnsActions').mockResolvedValue(page(actions));
}

describe('R03 domain and environment page', () => {
  afterEach(() => {
    adminSession.clear();
    vi.restoreAllMocks();
  });

  it('renders frozen domain and DNS action resources without claiming false health', async () => {
    authorize();
    prepare();

    const wrapper = mount(DomainConfigPage);
    await flushPromises();

    expect(wrapper.text()).toContain('orbexa.cc 域名编排');
    expect(wrapper.text()).toContain('api.orbexa.cc');
    expect(wrapper.text()).toContain('DNS 用户待办');
    expect(wrapper.text()).toContain('等待业务健康确认');
    expect(wrapper.text()).toContain('DNS 成功不等于服务可用');
    wrapper.unmount();
  });

  it('does not call domain APIs without read permission', async () => {
    authorize([]);
    const list = vi.spyOn(adminDomainsApi, 'list');
    const actions = vi.spyOn(adminDomainsApi, 'dnsActions');

    const wrapper = mount(DomainConfigPage);
    await flushPromises();

    expect(list).not.toHaveBeenCalled();
    expect(actions).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('无法访问域名配置');
    wrapper.unmount();
  });

  it('updates a domain with the current optimistic lock version', async () => {
    authorize();
    prepare();
    const update = vi.spyOn(adminDomainsApi, 'update').mockResolvedValue(resource({ version: 6, dnsStatus: 'PENDING_DNS', httpsStatus: 'NOT_RUN', lastVerifiedAt: undefined }));

    const wrapper = mount(DomainConfigPage);
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '修改计划')!.trigger('click');
    await wrapper.find('select').setValue('EXTERNAL');
    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(update).toHaveBeenCalledWith('api', {
      hostname: 'api.orbexa.cc', certificateMode: 'EXTERNAL', expectedVersion: 5,
    });
    expect(wrapper.text()).toContain('域名计划已更新');
    expect(wrapper.text()).toContain('v6');
    wrapper.unmount();
  });

  it('rejects an external hostname before issuing a write', async () => {
    authorize();
    prepare();
    const update = vi.spyOn(adminDomainsApi, 'update');

    const wrapper = mount(DomainConfigPage);
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '修改计划')!.trigger('click');
    await wrapper.find('input').setValue('api.attacker.example');
    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(update).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('受控二级域名');
    wrapper.unmount();
  });

  it('executes layered verification from the domain action', async () => {
    authorize();
    prepare();
    const verify = vi.spyOn(adminDomainsApi, 'verify').mockResolvedValue(resource({ version: 6 }));

    const wrapper = mount(DomainConfigPage);
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '分层验证')!.trigger('click');
    await flushPromises();

    expect(verify).toHaveBeenCalledWith('api', { force: false });
    expect(wrapper.text()).toContain('分层验证已执行');
    wrapper.unmount();
  });

  it('freezes the affected domain after a version conflict', async () => {
    authorize();
    prepare();
    vi.spyOn(adminDomainsApi, 'verify').mockRejectedValue(new ApiRequestError({
      status: 409,
      code: 'COMMON-409-VERSION_CONFLICT',
      message: '版本冲突',
      requestId: 'req-domain-409',
    }));

    const wrapper = mount(DomainConfigPage);
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '分层验证')!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('域名版本已变化');
    expect(wrapper.text()).toContain('部分域名需要刷新');
    expect(wrapper.findAll('button').find((button) => button.text() === '分层验证')!.attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });
});
