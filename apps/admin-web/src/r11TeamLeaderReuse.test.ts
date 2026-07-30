// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createMemoryHistory, createRouter } from 'vue-router';
import { adminContentsApi, adminSession } from './services';
import AdminContentDetailPage from './views/AdminContentDetailPage.vue';

describe('R11 team leader reuse of unified content administration', () => {
  afterEach(() => { adminSession.clear(); vi.restoreAllMocks(); });

  it('shows frozen team facts, real media and masked contacts without inventing admin actions', async () => {
    adminSession.apply({ accessToken: 'admin-token', adminUserId: '11', displayName: '内容运营', permissionCodes: ['content.read'], mfaRequired: 'NONE' });
    vi.spyOn(adminContentsApi, 'get').mockResolvedValue({
      id: 'team_11', title: '星火增长团队', contentType: 'TEAM_LEADER', status: 'ONLINE', reviewStatus: 'APPROVED', version: 7,
      attributes: { teamName: '星火增长团队', nickname: '星火队长', personalIntro: '专注品牌增长。', teamIntro: '提供内容策划与渠道协作。', sizeRange: '11-30', skills: '内容增长', cooperationTypes: '品牌合作', cooperationRequirement: '需求目标清晰', pastCases: ['新品冷启动', '区域增长计划'], acceptPrivateChat: true },
      media: [{ id: '111', mediaType: 'IMAGE', url: 'https://cdn.example.com/team.jpg', sortOrder: 0 }, { id: '112', mediaType: 'IMAGE', url: 'http://unsafe.example.com/team.jpg', sortOrder: 1 }],
      publisher: { userId: '101', nickname: '星火队长', verified: true },
      contactsMasked: [{ channel: 'WECHAT', maskedValue: 'xh***88', available: true, accessPolicy: 'EXPLICIT', accessed: false }, { channel: 'PHONE', maskedValue: '****6132', available: true, accessPolicy: 'EXPLICIT', accessed: false }],
      statistics: { viewCount: 86, favoriteCount: 12, shareCount: 5, contactAccessCount: 3 },
    } as never);
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/contents/:id', component: AdminContentDetailPage }] });
    await router.push('/contents/team_11'); await router.isReady();
    const wrapper = mount(AdminContentDetailPage, { global: { plugins: [router] } }); await flushPromises();

    expect(wrapper.text()).toContain('团队长运营信息');
    expect(wrapper.text()).toContain('星火增长团队');
    expect(wrapper.text()).toContain('星火队长 · 已认证');
    expect(wrapper.text()).toContain('品牌合作');
    expect(wrapper.text()).toContain('新品冷启动');
    expect(wrapper.text()).toContain('xh***88');
    expect(wrapper.text()).toContain('浏览 86 · 收藏 12 · 分享 5 · 联系 3');
    expect(wrapper.find('img[src="https://cdn.example.com/team.jpg"]').exists()).toBe(true);
    expect(wrapper.find('img[src="http://unsafe.example.com/team.jpg"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('审核通过团队长');
    wrapper.unmount();
  });
});
