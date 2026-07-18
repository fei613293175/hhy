import { createRouter, createWebHistory } from 'vue-router';
import { h5Pages } from './catalog';
import InviteRegistrationPage from './views/InviteRegistrationPage.vue';
import PublicPage from './views/PublicPage.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    ...h5Pages.map((page) => ({
      path: page.vueRoute,
      name: page.ID,
      component: page.ID === 'H5-013' ? InviteRegistrationPage : PublicPage,
      props: { page },
    })),
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
});
