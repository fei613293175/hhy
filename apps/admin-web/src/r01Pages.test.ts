// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiRequestError, adminSecurityApi, adminSession } from './services';
import { router } from './router';
import AdminLoginPage from './views/AdminLoginPage.vue';
import AdminMfaPage from './views/AdminMfaPage.vue';
import AdminSecurityPage from './views/AdminSecurityPage.vue';
import StatusNotice from './components/StatusNotice.vue';

async function navigate(path: string) {
  await router.push(path);
  await router.isReady();
}

describe('R01 admin routes and page states', () => {
  afterEach(() => {
    adminSession.clear();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('redirects an anonymous security-page visit to login with a safe return path', async () => {
    adminSession.clear();
    await navigate('/me/security');

    expect(router.currentRoute.value.path).toBe('/auth/login');
    expect(router.currentRoute.value.query.redirect).toBe('/me/security');
  });

  it('protects every catalog-backed admin route behind the same memory session', async () => {
    adminSession.clear();
    await navigate('/dashboard');

    expect(router.currentRoute.value.path).toBe('/auth/login');
    expect(router.currentRoute.value.query.redirect).toBe('/dashboard');
  });

  it('requires an in-memory MFA ticket and allows the pending flow when present', async () => {
    adminSession.clear();
    await navigate('/auth/mfa');
    expect(router.currentRoute.value.path).toBe('/auth/login');

    adminSession.apply({
      adminUserId: '17',
      mfaRequired: 'TOTP',
      mfaTicket: 'memory-only-ticket',
    });
    await navigate('/auth/mfa?redirect=/me/security');
    expect(router.currentRoute.value.path).toBe('/auth/mfa');
  });

  it('keeps authenticated administrators out of the public login route', async () => {
    adminSession.apply({
      accessToken: 'memory-only-access',
      adminUserId: '17',
      displayName: '安全管理员',
      permissionCodes: ['admin.self.read'],
      mfaRequired: 'NONE',
    });
    await navigate('/auth/login');
    expect(router.currentRoute.value.path).toBe('/me/security');
  });

  it('renders login validation and the explicit memory-only security promise', async () => {
    adminSession.clear();
    await navigate('/auth/login');
    const wrapper = mount(AdminLoginPage, { global: { plugins: [router] } });
    const submit = wrapper.get('button[type="submit"]');

    expect(submit.attributes('disabled')).toBeDefined();
    expect(wrapper.text()).toContain('访问令牌仅保存在当前页面内存中');
    await wrapper.get('input[name="username"]').setValue('root');
    await wrapper.get('input[name="password"]').setValue('short');
    expect(wrapper.text()).toContain('密码长度须为 8–72 个字符');
    wrapper.unmount();
  });

  it('clears the password immediately after a failed login attempt', async () => {
    vi.spyOn(adminSecurityApi, 'login').mockRejectedValueOnce(new ApiRequestError({
      status: 401,
      code: 'COMMON-401-UNAUTHENTICATED',
      message: '账号或密码错误',
      requestId: 'req-login-failed',
    }));
    adminSession.clear();
    await navigate('/auth/login');
    const wrapper = mount(AdminLoginPage, { global: { plugins: [router] } });
    await wrapper.get('input[name="username"]').setValue('root');
    const passwordInput = wrapper.get('input[name="password"]');
    await passwordInput.setValue('Invalid-Test-Only-123!');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect((passwordInput.element as HTMLInputElement).value).toBe('');
    expect(wrapper.text()).toContain('账号或密码错误');
    expect(wrapper.text()).toContain('请求标识：req-login-failed');
    wrapper.unmount();
  });

  it('renders field errors and blocks login until Retry-After expires', async () => {
    vi.useFakeTimers();
    vi.spyOn(adminSecurityApi, 'login').mockRejectedValueOnce(new ApiRequestError({
      status: 429,
      code: 'COMMON-429-RATE_LIMITED',
      message: '尝试过于频繁',
      requestId: 'req-rate-limited',
      retryAfter: 2,
      retryable: true,
      details: [{ field: 'password', code: 'AUTH-LOCKED', message: '请重新确认密码' }],
    }));
    adminSession.clear();
    await navigate('/auth/login');
    const wrapper = mount(AdminLoginPage, { global: { plugins: [router] } });
    await wrapper.get('input[name="username"]').setValue('root');
    const passwordInput = wrapper.get('input[name="password"]');
    await passwordInput.setValue('Invalid-Test-Only-123!');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('请重新确认密码');
    expect(wrapper.text()).toContain('2 秒后重试');
    await passwordInput.setValue('Changed-Test-Only-456!');
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined();

    await vi.advanceTimersByTimeAsync(2_000);
    await wrapper.vm.$nextTick();
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined();
    wrapper.unmount();
  });

  it('maps the real backend restricted-account response to the terminal locked state', async () => {
    vi.spyOn(adminSecurityApi, 'login').mockRejectedValueOnce(new ApiRequestError({
      status: 422,
      code: 'COMMON-422-BUSINESS_RULE',
      message: '管理员账号已受限',
      requestId: 'req-account-restricted',
    }));
    adminSession.clear();
    await navigate('/auth/login');
    const wrapper = mount(AdminLoginPage, { global: { plugins: [router] } });
    await wrapper.get('input[name="username"]').setValue('restricted-admin');
    const passwordInput = wrapper.get('input[name="password"]');
    await passwordInput.setValue('Invalid-Test-Only-123!');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    await passwordInput.setValue('Changed-Test-Only-456!');

    expect(wrapper.text()).toContain('管理员账号已锁定');
    expect(wrapper.get('button[type="submit"]').text()).toBe('账号已锁定');
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('treats a login 403 as a terminal no-permission state and hides sensitive inputs', async () => {
    vi.spyOn(adminSecurityApi, 'login').mockRejectedValueOnce(new ApiRequestError({
      status: 403,
      code: 'COMMON-403-FORBIDDEN',
      message: '访问被拒绝',
      requestId: 'req-login-forbidden',
    }));
    adminSession.clear();
    await navigate('/auth/login');
    const wrapper = mount(AdminLoginPage, { global: { plugins: [router] } });
    await wrapper.get('input[name="username"]').setValue('root');
    await wrapper.get('input[name="password"]').setValue('Invalid-Test-Only-123!');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('没有后台登录权限');
    expect(wrapper.find('form').exists()).toBe(false);
    expect(wrapper.html()).not.toContain('Invalid-Test-Only-123!');
    wrapper.unmount();
  });

  it('renders an MFA input without exposing its ticket in the DOM', async () => {
    adminSession.apply({
      adminUserId: '17',
      mfaRequired: 'TOTP',
      mfaTicket: 'ticket-must-not-render',
    });
    await navigate('/auth/mfa');
    const wrapper = mount(AdminMfaPage, { global: { plugins: [router] } });

    expect(wrapper.get('input[name="code"]').attributes('autocomplete')).toBe('one-time-code');
    expect(wrapper.html()).not.toContain('ticket-must-not-render');
    expect(wrapper.text()).toContain('不会写入 URL、日志或浏览器持久存储');
    wrapper.unmount();
  });

  it('shows the MFA offline state and disables verification writes', async () => {
    const previousOnline = window.navigator.onLine;
    Object.defineProperty(window.navigator, 'onLine', { configurable: true, value: false });
    adminSession.apply({
      adminUserId: '17',
      mfaRequired: 'TOTP',
      mfaTicket: 'memory-only-ticket',
    });
    await navigate('/auth/mfa');
    const wrapper = mount(AdminMfaPage, { global: { plugins: [router] } });
    await wrapper.get('input[name="code"]').setValue('123456');

    expect(wrapper.text()).toContain('二次验证已禁用');
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined();
    wrapper.unmount();
    Object.defineProperty(window.navigator, 'onLine', {
      configurable: true,
      value: previousOnline,
    });
  });

  it('renders an MFA field error and blocks repeated verification while rate limited', async () => {
    vi.useFakeTimers();
    vi.spyOn(adminSecurityApi, 'verifyMfa').mockRejectedValueOnce(new ApiRequestError({
      status: 429,
      code: 'COMMON-429-RATE_LIMITED',
      message: '验证码尝试过于频繁',
      requestId: 'req-mfa-rate-limited',
      retryAfter: 30,
      details: [{ field: 'code', code: 'MFA-CODE-INVALID', message: '动态码无效' }],
    }));
    adminSession.apply({
      adminUserId: '17',
      mfaRequired: 'TOTP',
      mfaTicket: 'memory-only-ticket',
    });
    await navigate('/auth/mfa');
    const wrapper = mount(AdminMfaPage, { global: { plugins: [router] } });
    const codeInput = wrapper.get('input[name="code"]');
    await codeInput.setValue('123456');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('动态码无效');
    expect(wrapper.text()).toContain('30 秒后重试');
    await codeInput.setValue('654321');
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('treats an MFA 403 as terminal and only offers a safe return to login', async () => {
    vi.spyOn(adminSecurityApi, 'verifyMfa').mockRejectedValueOnce(new ApiRequestError({
      status: 403,
      code: 'COMMON-403-FORBIDDEN',
      message: '访问被拒绝',
      requestId: 'req-mfa-forbidden',
    }));
    adminSession.apply({
      adminUserId: '17',
      mfaRequired: 'TOTP',
      mfaTicket: 'memory-only-ticket',
    });
    await navigate('/auth/mfa');
    const wrapper = mount(AdminMfaPage, { global: { plugins: [router] } });
    await wrapper.get('input[name="code"]').setValue('123456');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('没有二次验证权限');
    expect(wrapper.find('form').exists()).toBe(false);
    expect(wrapper.get('button').text()).toContain('安全返回登录');
    wrapper.unmount();
  });

  it('renders structured request identifiers for support diagnostics', () => {
    const wrapper = mount(StatusNotice, {
      props: {
        tone: 'danger',
        title: '请求失败',
        detail: '请稍后重试',
        requestId: 'req-r01-0001',
      },
    });
    expect(wrapper.text()).toContain('请求标识：req-r01-0001');
  });

  it('labels, focuses, traps and restores focus for critical security dialogs', async () => {
    vi.spyOn(adminSecurityApi, 'getSecurity').mockResolvedValueOnce({
      adminId: '17',
      username: 'root',
      mfaEnabled: false,
      mfaMethods: [],
      activeSessionCount: 1,
      recoveryCodesRemaining: 0,
    });
    adminSession.apply({
      accessToken: 'memory-only-access',
      adminUserId: '17',
      permissionCodes: ['admin.self.read', 'admin.self.security'],
      mfaRequired: 'NONE',
    });
    await navigate('/me/security');
    const wrapper = mount(AdminSecurityPage, {
      attachTo: document.body,
      global: { plugins: [router] },
    });
    await flushPromises();
    const trigger = wrapper.findAll('button').find((button) => button.text() === '修改密码');
    expect(trigger).toBeDefined();
    trigger!.element.focus();
    expect(document.activeElement).toBe(trigger!.element);
    await trigger!.trigger('click');
    await wrapper.vm.$nextTick();

    const dialog = wrapper.get('[role="dialog"]');
    expect(dialog.attributes('aria-labelledby')).toBe('security-modal-title');
    expect(dialog.element.contains(document.activeElement)).toBe(true);
    await dialog.trigger('keydown', { key: 'Escape' });
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(document.activeElement).toBe(trigger!.element);
    wrapper.unmount();
  });

  it('closes and scrubs a critical dialog immediately when write permission is revoked', async () => {
    vi.spyOn(adminSecurityApi, 'getSecurity').mockResolvedValueOnce({
      adminId: '17',
      username: 'root',
      mfaEnabled: false,
      mfaMethods: [],
      activeSessionCount: 1,
      recoveryCodesRemaining: 0,
    });
    vi.spyOn(adminSecurityApi, 'changePassword').mockRejectedValueOnce(new ApiRequestError({
      status: 403,
      code: 'COMMON-403-FORBIDDEN',
      message: '权限已撤销',
      requestId: 'req-write-revoked',
    }));
    adminSession.apply({
      accessToken: 'memory-only-access',
      adminUserId: '17',
      permissionCodes: ['admin.self.read', 'admin.self.security'],
      mfaRequired: 'NONE',
    });
    await navigate('/me/security');
    const wrapper = mount(AdminSecurityPage, { global: { plugins: [router] } });
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '修改密码')!.trigger('click');
    const dialog = wrapper.get('[role="dialog"]');
    await dialog.get('input[autocomplete="current-password"]').setValue('Current!234');
    await dialog.get('input[autocomplete="new-password"]').setValue('Changed!567');
    await dialog.get('input[autocomplete="one-time-code"]').setValue('123456');
    await dialog.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('只读权限');
    expect(wrapper.html()).not.toContain('Current!234');
    expect(wrapper.html()).not.toContain('Changed!567');
    wrapper.unmount();
  });

  it('disables security-page logout and all writes while offline', async () => {
    const previousOnline = window.navigator.onLine;
    Object.defineProperty(window.navigator, 'onLine', { configurable: true, value: false });
    const getSecurity = vi.spyOn(adminSecurityApi, 'getSecurity');
    adminSession.apply({
      accessToken: 'memory-only-access',
      adminUserId: '17',
      permissionCodes: ['admin.self.read', 'admin.self.security'],
      mfaRequired: 'NONE',
    });
    await navigate('/me/security');
    const wrapper = mount(AdminSecurityPage, { global: { plugins: [router] } });
    await flushPromises();
    const logout = wrapper.findAll('button').find((button) => button.text() === '退出会话');

    expect(wrapper.text()).toContain('安全写操作已全部禁用');
    expect(logout?.attributes('disabled')).toBeDefined();
    expect(getSecurity).not.toHaveBeenCalled();
    wrapper.unmount();
    Object.defineProperty(window.navigator, 'onLine', {
      configurable: true,
      value: previousOnline,
    });
  });

  it('applies a Retry-After cooldown to every security POST and rejects duplicate submits', async () => {
    vi.useFakeTimers();
    vi.spyOn(adminSecurityApi, 'getSecurity').mockResolvedValueOnce({
      adminId: '17', username: 'root', mfaEnabled: false, mfaMethods: [],
      activeSessionCount: 1, recoveryCodesRemaining: 0,
    });
    const changePassword = vi.spyOn(adminSecurityApi, 'changePassword').mockRejectedValueOnce(new ApiRequestError({
      status: 429,
      code: 'COMMON-429-RATE_LIMITED',
      message: '安全操作过于频繁',
      requestId: 'req-security-rate-limit',
      retryAfter: 2,
    }));
    const logout = vi.spyOn(adminSecurityApi, 'logout');
    adminSession.apply({
      accessToken: 'memory-only-access', adminUserId: '17',
      permissionCodes: ['admin.self.read', 'admin.self.security'], mfaRequired: 'NONE',
    });
    await navigate('/me/security');
    const wrapper = mount(AdminSecurityPage, { global: { plugins: [router] } });
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '修改密码')!.trigger('click');
    const form = wrapper.get('[role="dialog"] form');
    await form.get('input[autocomplete="current-password"]').setValue('Current!234');
    await form.get('input[autocomplete="new-password"]').setValue('Changed!567');
    await form.get('input[autocomplete="one-time-code"]').setValue('123456');
    await form.trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('安全写操作冷却中');
    expect(wrapper.text()).toContain('2 秒后可重试');
    await form.trigger('submit');
    await flushPromises();
    expect(changePassword).toHaveBeenCalledTimes(1);
    await wrapper.get('button[aria-label="关闭"]').trigger('click');
    const logoutButton = wrapper.findAll('button').find((button) => button.text() === '退出会话')!;
    expect(logoutButton.attributes('disabled')).toBeDefined();
    await logoutButton.trigger('click');
    expect(logout).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(2_000);
    await wrapper.vm.$nextTick();
    expect(logoutButton.attributes('disabled')).toBeUndefined();
    wrapper.unmount();
  });

  it('closes a populated security dialog, scrubs secrets and emits zero POSTs after switching offline', async () => {
    const previousOnline = window.navigator.onLine;
    Object.defineProperty(window.navigator, 'onLine', { configurable: true, value: true });
    vi.spyOn(adminSecurityApi, 'getSecurity').mockResolvedValueOnce({
      adminId: '17', username: 'root', mfaEnabled: false, mfaMethods: [],
      activeSessionCount: 1, recoveryCodesRemaining: 0,
    });
    const changePassword = vi.spyOn(adminSecurityApi, 'changePassword');
    const enrollMfa = vi.spyOn(adminSecurityApi, 'enrollMfa');
    const logout = vi.spyOn(adminSecurityApi, 'logout');
    adminSession.apply({
      accessToken: 'memory-only-access', adminUserId: '17',
      permissionCodes: ['admin.self.read', 'admin.self.security'], mfaRequired: 'NONE',
    });
    await navigate('/me/security');
    const wrapper = mount(AdminSecurityPage, { global: { plugins: [router] } });
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '修改密码')!.trigger('click');
    const form = wrapper.get('[role="dialog"] form');
    await form.get('input[autocomplete="current-password"]').setValue('Offline-Current!234');
    await form.get('input[autocomplete="new-password"]').setValue('Offline-Changed!567');
    await form.get('input[autocomplete="one-time-code"]').setValue('654321');

    Object.defineProperty(window.navigator, 'onLine', { configurable: true, value: false });
    window.dispatchEvent(new Event('offline'));
    await wrapper.vm.$nextTick();
    await form.trigger('submit');
    await flushPromises();

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(wrapper.html()).not.toContain('Offline-Current!234');
    expect(wrapper.html()).not.toContain('Offline-Changed!567');
    expect(changePassword).not.toHaveBeenCalled();
    expect(enrollMfa).not.toHaveBeenCalled();
    expect(logout).not.toHaveBeenCalled();
    wrapper.unmount();
    Object.defineProperty(window.navigator, 'onLine', { configurable: true, value: previousOnline });
  });

  it.each([
    ['success', undefined, '状态冲突，已刷新最新状态', 'fresh-admin'],
    ['failure', new ApiRequestError({ status: 500, code: 'COMMON-500-INTERNAL', message: '刷新失败', requestId: 'req-refresh-failed' }), '状态冲突且刷新失败', '安全状态暂时不可用'],
  ])('awaits a 409 refresh and exposes its %s terminal state', async (_outcome, refreshFailure, expectedNotice, expectedState) => {
    const initial = {
      adminId: '17', username: 'root', mfaEnabled: false, mfaMethods: [],
      activeSessionCount: 1, recoveryCodesRemaining: 0,
    };
    const getSecurity = vi.spyOn(adminSecurityApi, 'getSecurity').mockResolvedValueOnce(initial);
    if (refreshFailure) getSecurity.mockRejectedValueOnce(refreshFailure);
    else getSecurity.mockResolvedValueOnce({ ...initial, username: 'fresh-admin', activeSessionCount: 2 });
    vi.spyOn(adminSecurityApi, 'changePassword').mockRejectedValueOnce(new ApiRequestError({
      status: 409,
      code: 'COMMON-409-CONFLICT',
      message: '安全状态已变化',
      requestId: 'req-security-conflict',
    }));
    adminSession.apply({
      accessToken: 'memory-only-access', adminUserId: '17',
      permissionCodes: ['admin.self.read', 'admin.self.security'], mfaRequired: 'NONE',
    });
    await navigate('/me/security');
    const wrapper = mount(AdminSecurityPage, { global: { plugins: [router] } });
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text() === '修改密码')!.trigger('click');
    const form = wrapper.get('[role="dialog"] form');
    await form.get('input[autocomplete="current-password"]').setValue('Current!234');
    await form.get('input[autocomplete="new-password"]').setValue('Changed!567');
    await form.get('input[autocomplete="one-time-code"]').setValue('123456');
    await form.trigger('submit');
    await flushPromises();

    expect(getSecurity).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain(expectedNotice);
    expect(wrapper.text()).toContain(expectedState);
    wrapper.unmount();
  });
});
