<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { h5Pages } from './catalog';

const route = useRoute();
const isIdentityCallback = computed(() => route.path === '/identity/callback');
</script>

<template>
  <header v-if="!isIdentityCallback" class="top">
    <RouterLink to="/" class="logo">合伙云 Pro</RouterLink>
    <nav><RouterLink v-for="p in h5Pages.slice(0,4)" :key="p.ID" :to="p.vueRoute.replace(/:\w+/g,'demo')">{{p.页面}}</RouterLink></nav>
  </header>
  <RouterView />
  <footer v-if="!isIdentityCallback">V1.2.2 · 公开内容与安全收银台部署边界分离</footer>
</template>
