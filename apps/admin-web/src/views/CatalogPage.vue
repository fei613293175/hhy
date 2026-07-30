<script setup lang="ts">
import type { AdminPage } from '../catalog'

defineProps<{ page: AdminPage }>()
</script>

<template>
  <article class="page">
    <header class="page-heading"><div><div class="eyebrow">功能建设中</div><h1>{{ page.页面 }}</h1><p>{{ page.功能摘要 }}</p></div></header>
    <section class="card empty-state"><div class="avatar centered-mark">建</div><h2>该功能尚未开放</h2><p>页面业务接口、权限、审计和完整操作链路尚未完成，因此不会提供不可用的占位按钮。</p><RouterLink class="primary-button table-link" to="/me/security">返回可用功能</RouterLink></section>
  </article>
</template>
