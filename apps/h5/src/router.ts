import { createRouter, createWebHistory } from 'vue-router';
import { h5Pages } from './catalog';
import InviteRegistrationPage from './views/InviteRegistrationPage.vue';
import IdentityCallbackPage from './views/IdentityCallbackPage.vue';
import PublicPage from './views/PublicPage.vue';
import DocumentPage from './views/DocumentPage.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    ...h5Pages.map((page) => ({
      path: page.vueRoute,
      name: page.ID,
      component: page.ID === 'H5-013'
        ? InviteRegistrationPage
        : page.ID === 'H5-012'
          ? IdentityCallbackPage
          : ['H5-010', 'H5-011'].includes(page.ID) ? DocumentPage : PublicPage,
      props: { page },
    })),
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
});
