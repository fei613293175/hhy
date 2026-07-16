<script setup lang="ts">import { computed } from 'vue'; import { adminPages } from './catalog';
const groups=computed(()=>Array.from(new Set(adminPages.map(x=>x.菜单分组))));
</script>
<template><div class="layout"><aside><div class="brand"><strong>合伙云 Pro</strong><small>V1.2.2 管理后台</small></div><nav><section v-for="g in groups" :key="g"><h2>{{g}}</h2><RouterLink v-for="p in adminPages.filter(x=>x.菜单分组===g&&!x.路由.includes(':id'))" :key="p.ID" :to="p.路由">{{p.页面}}</RouterLink></section></nav></aside><main><header><span>环境：LOCAL</span><span>权限、脱敏、审批、审计已冻结</span></header><RouterView/></main></div></template>
