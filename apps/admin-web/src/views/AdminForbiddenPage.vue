<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <article class="page">
    <header class="page-heading"><div><div class="eyebrow">访问受限</div><h1>当前账号无权访问此功能</h1><p>该入口已被权限系统拦截。后台不会展示目标资源内容，也不会将您错误地带到其他功能页面。</p></div></header>
    <section class="card error-state"><div class="avatar centered-mark">!</div><h2>需要管理员分配对应权限</h2><p>请联系超级管理员核对角色权限，或返回您已有权限的功能。</p><div class="button-row forbidden-actions"><button class="ghost-button" type="button" @click="router.back()">返回上一页</button><RouterLink class="primary-button table-link" to="/me/security">进入账号安全中心</RouterLink></div></section>
  </article>
</template>
