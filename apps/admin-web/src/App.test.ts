// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import App from './App.vue'
import { router } from './router'
import { adminSession } from './services'

describe('admin application shell', () => {
  afterEach(() => adminSession.clear())

  it('recomputes the permission-filtered menu after login navigation', async () => {
    adminSession.clear()
    await router.push('/auth/login')
    await router.isReady()
    const wrapper = mount(App, { global: { plugins: [router] } })

    adminSession.apply({
      accessToken: 'access', adminUserId: '1', displayName: '超级管理员',
      permissionCodes: ['admin.self.read', 'user.read', 'provider.config.read'], mfaRequired: 'NONE',
    })
    await router.push('/me/security')
    await flushPromises()

    expect(wrapper.get('nav[aria-label="主导航"]').text()).toContain('用户管理')
    expect(wrapper.get('nav[aria-label="主导航"]').text()).toContain('对象存储')
    expect(wrapper.get('nav[aria-label="主导航"]').text()).not.toContain('数据看板')
  })
})
