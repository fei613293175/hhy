<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { adminPages } from './catalog'
import { adminSession } from './services'

const route = useRoute()
const shellVisible = computed(() => !route.meta.authLayout)
const menuPages = computed(() => adminPages.filter((page) => page.菜单分组 !== 'hidden' && !page.路由.includes(':id')))
const groups = computed(() => Array.from(new Set(menuPages.value.map((page) => page.菜单分组))))
</script>

<template>
  <RouterView v-if="!shellVisible" />
  <div v-else class="layout">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">合</span><span class="brand-copy"><strong>合伙云 Pro</strong><small>运营控制台</small></span></div>
      <nav aria-label="主导航"><section v-for="group in groups" :key="group" class="nav-section"><h2>{{ group }}</h2><RouterLink v-for="page in menuPages.filter((item) => item.菜单分组 === group)" :key="page.ID" class="nav-link" :to="page.路由">{{ page.页面 }}</RouterLink></section></nav>
    </aside>
    <div class="main-column">
      <header class="topbar"><span class="environment-badge">开发环境</span><div class="topbar-meta"><span>权限、脱敏与审计策略已启用</span><strong>{{ adminSession.displayName || '管理员' }}</strong></div></header>
      <main><RouterView /></main>
    </div>
  </div>
</template>
