// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { H5Page } from '../catalog';
import { InviteApiError, inviteRegistrationApi } from '../services/inviteRegistration';
import InviteRegistrationPage from './InviteRegistrationPage.vue';

vi.mock('../services/inviteRegistration', async (importOriginal) => {
  const original = await importOriginal<typeof import('../services/inviteRegistration')>();
  return {
    ...original,
    inviteRegistrationApi: {
      getConfig: vi.fn(),
      createChallenge: vi.fn(),
      sendSms: vi.fn(),
      register: vi.fn(),
    },
  };
});

const api = vi.mocked(inviteRegistrationApi);
const page: H5Page = {
  ID: 'H5-013',
  页面: 'H5邀请注册页',
  路由: '/invite/{code}/register',
  vueRoute: '/invite/:code/register',
  访问: '公开',
  功能摘要: '邀请注册',
  计划版本: 'R02',
};

function config() {
  return {
    items: [{
      code: 'INVITE-R02',
      title: '加入测试团队',
      description: '安全注册后下载 App',
      content: [{
        blockId: '91',
        blockType: 'RICH_TEXT' as const,
        heading: 'USER_SERVICE',
        body: '2026-07-18T09:00:00Z',
        sortOrder: 0,
      }],
      version: 7,
    }],
    page: { page: 1, pageSize: 20, total: '1', hasMore: 'false' },
  };
}

async function render() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/invite/:code/register', component: InviteRegistrationPage, props: { page } },
      { path: '/', component: { template: '<main>下载页</main>' } },
    ],
  });
  await router.push('/invite/INVITE-R02/register');
  await router.isReady();
  const wrapper = mount(InviteRegistrationPage, {
    props: { page },
    global: { plugins: [router] },
  });
  await flushPromises();
  return { wrapper, router };
}

async function fillFormAndOpenChallenge(wrapper: Awaited<ReturnType<typeof render>>['wrapper']) {
  await wrapper.get('input[autocomplete="tel"]').setValue('13800000000');
  const passwords = wrapper.findAll('input[autocomplete="new-password"]');
  await passwords[0]!.setValue('StrongPass9');
  await passwords[1]!.setValue('StrongPass9');
  await wrapper.get('form').trigger('submit');
  await flushPromises();
}

describe('InviteRegistrationPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.getConfig.mockResolvedValue(config());
    api.createChallenge.mockResolvedValue({
      challengeId: 'challenge-1',
      challengeType: 'IMAGE',
      expiresAt: '2026-07-18T09:30:00Z',
      imageBase64: 'aW1hZ2U=',
    });
    api.sendSms.mockResolvedValue({
      status: 'SMS_ACCEPTED',
      acceptedAt: '2026-07-18T09:00:00Z',
    });
    api.register.mockResolvedValue({
      status: 'REGISTERED',
      acceptedAt: '2026-07-18T09:01:00Z',
    });
  });

  it('opens the challenge from register and continues registration without SMS', async () => {
    const { wrapper, router } = await render();
    expect(wrapper.text()).toContain('加入测试团队');
    expect(wrapper.text()).toContain('INVITE-R02');

    await fillFormAndOpenChallenge(wrapper);
    await wrapper.get('input[autocomplete="off"]').setValue('proof-r02');
    await wrapper.findAll('button').find((button) => button.text().includes('验证并继续'))!.trigger('click');
    await flushPromises();

    expect(api.createChallenge).toHaveBeenCalledWith({
      scene: 'REGISTER', clientNonce: expect.any(String),
    });
    expect(api.sendSms).not.toHaveBeenCalled();
    expect(api.register).toHaveBeenCalledWith({
      phone: '13800000000', password: 'StrongPass9', inviteCode: 'INVITE-R02',
      challengeId: 'challenge-1', challengeProof: 'proof-r02',
    });
    expect(router.currentRoute.value.fullPath).toBe('/?registered=1&invite_code=INVITE-R02');
  });

  it('refreshes only for the dedicated security challenge error', async () => {
    api.register.mockRejectedValueOnce(new InviteApiError(
      422, 'AUTH-422-SECURITY_CHALLENGE_INVALID', '安全验证失败',
    ));
    const { wrapper } = await render();
    await fillFormAndOpenChallenge(wrapper);
    await wrapper.get('input[autocomplete="off"]').setValue('0000');
    await wrapper.findAll('button').find((button) => button.text().includes('验证并继续'))!.trigger('click');
    await flushPromises();

    expect(api.createChallenge).toHaveBeenCalledTimes(2);
    expect(wrapper.find('.challenge-dialog').exists()).toBe(true);
    expect(wrapper.text()).toContain('输入不正确，请根据新图片重新输入');
  });

  it('does not misreport a registration business rule as a challenge failure', async () => {
    api.register.mockRejectedValueOnce(new InviteApiError(
      422, 'COMMON-422-BUSINESS_RULE', '邀请码无效',
    ));
    const { wrapper } = await render();
    await fillFormAndOpenChallenge(wrapper);
    await wrapper.get('input[autocomplete="off"]').setValue('1458');
    await wrapper.findAll('button').find((button) => button.text().includes('验证并继续'))!.trigger('click');
    await flushPromises();

    expect(api.createChallenge).toHaveBeenCalledTimes(1);
    expect(wrapper.find('.challenge-dialog').exists()).toBe(false);
    expect(wrapper.text()).toContain('注册信息未通过，请检查手机号、密码和邀请码');
    expect(wrapper.text()).not.toContain('输入不正确');
  });

  it('does not label a server-side registration failure as a network outage', async () => {
    api.register.mockRejectedValueOnce(new InviteApiError(
      500, 'COMMON-500-INTERNAL', '内部错误',
    ));
    const { wrapper } = await render();
    await fillFormAndOpenChallenge(wrapper);
    await wrapper.get('input[autocomplete="off"]').setValue('8937');
    await wrapper.findAll('button').find((button) => button.text().includes('验证并继续'))!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('注册服务暂时不可用，请稍后再试');
    expect(wrapper.text()).not.toContain('网络连接失败');
  });

  it('does not expose a manual challenge button or registration SMS field', async () => {
    const { wrapper } = await render();
    expect(wrapper.text()).not.toContain('获取安全验证');
    expect(wrapper.text()).not.toContain('发送验证码');
    expect(wrapper.find('input[autocomplete="one-time-code"]').exists()).toBe(false);
    expect(wrapper.find('.challenge-dialog').exists()).toBe(false);
  });

  it('renders an unrecoverable state for an invalid invite', async () => {
    api.getConfig.mockRejectedValue(new InviteApiError(
      404, 'COMMON-404-NOT_FOUND', '邀请码不存在或已经失效',
    ));

    const { wrapper } = await render();

    expect(wrapper.text()).toContain('邀请已失效');
    expect(wrapper.text()).not.toContain('重新加载');
  });

  it('allows a manual reload after an offline failure', async () => {
    api.getConfig
      .mockRejectedValueOnce(new InviteApiError(0, 'NETWORK_ERROR', '网络连接失败'))
      .mockResolvedValueOnce(config());

    const { wrapper } = await render();
    expect(wrapper.text()).toContain('网络连接失败');

    await wrapper.get('button').trigger('click');
    await flushPromises();

    expect(api.getConfig).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('加入测试团队');
  });
});
