// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter } from 'vue-router';
import { adminContentsApi, adminSession } from './services';
import AdminContentDetailPage from './views/AdminContentDetailPage.vue';

describe('R10 group reuse of unified content administration', () => {
  afterEach(() => { adminSession.clear(); vi.restoreAllMocks(); });

  it('shows typed group facts, real media and masked secrets without exposing the join link', async () => {
    adminSession.apply({ accessToken: 'admin-token', adminUserId: '10', displayName: '群聊运营', permissionCodes: ['content.read'], mfaRequired: 'NONE' });
    vi.spyOn(adminContentsApi, 'get').mockResolvedValue({
      id: 'group_10', title: '产品经理交流群', contentType: 'GROUP_CHAT', status: 'ONLINE', reviewStatus: 'APPROVED', version: 5,
      attributes: { platform: 'WECHAT', sizeRange: '100-200', joinRequirement: '产品从业者', groupNo: '998877', groupLink: 'https://group.example.com/join' },
      media: [{ id: '91', mediaType: 'IMAGE', url: 'https://cdn.example.com/group.png', sortOrder: 0 }],
      publisher: { userId: '88', nickname: '群主张先生', verified: true },
      contactsMasked: [{ channel: 'JOIN_PASSWORD', maskedValue: '口令***', available: true, accessPolicy: 'EXPLICIT', accessed: false }, { channel: 'WECHAT', maskedValue: 'wx***88', available: true, accessPolicy: 'EXPLICIT', accessed: false }],
      statistics: { viewCount: 18, favoriteCount: 3, shareCount: 2, contactAccessCount: 1 },
    } as never);
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/contents/:id', component: AdminContentDetailPage }] });
    await router.push('/contents/group_10'); await router.isReady();
    const wrapper = mount(AdminContentDetailPage, { global: { plugins: [router] } }); await flushPromises();

    expect(wrapper.text()).toContain('群聊运营信息');
    expect(wrapper.text()).toContain('微信群');
    expect(wrapper.text()).toContain('998877');
    expect(wrapper.text()).toContain('口令***');
    expect(wrapper.text()).toContain('群主张先生 · 已认证');
    expect(wrapper.find('img[src="https://cdn.example.com/group.png"]').exists()).toBe(true);
    expect(wrapper.text()).not.toContain('https://group.example.com/join');
    wrapper.unmount();
  });
});
