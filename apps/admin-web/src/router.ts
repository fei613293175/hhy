import { createRouter, createWebHistory } from 'vue-router'; import { adminPages } from './catalog'; import CatalogPage from './views/CatalogPage.vue';
export const router=createRouter({history:createWebHistory(),routes:[
 ...adminPages.map(page=>({path:page.路由,name:page.ID,component:CatalogPage,props:{page}})),
 {path:'/:pathMatch(.*)*',redirect:'/dashboard'}
]});
