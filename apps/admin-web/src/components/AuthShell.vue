<script setup lang="ts">
defineProps<{ eyebrow: string; title: string; description: string }>()
</script>

<template>
  <main class="auth-shell">
    <section class="auth-story" aria-label="平台安全说明">
      <div class="auth-brand"><span class="brand-mark">合</span><span>合伙云 Pro</span></div>
      <div class="auth-story-copy">
        <p class="eyebrow">运营控制台 · 安全域</p>
        <h1>重要业务，始终在可审计的边界内运行。</h1>
        <p>管理员会话与用户会话完全隔离。登录、二次验证及高风险操作均纳入操作审计。</p>
      </div>
      <ul class="trust-list">
        <li><span>01</span>独立管理员权限域</li>
        <li><span>02</span>MFA 会话升级</li>
        <li><span>03</span>全链路操作审计</li>
      </ul>
    </section>
    <section class="auth-workspace">
      <div class="auth-card">
        <p class="eyebrow">{{ eyebrow }}</p>
        <h2>{{ title }}</h2>
        <p class="auth-description">{{ description }}</p>
        <slot />
      </div>
      <p class="auth-footnote">仅限授权人员使用 · 所有访问均会记录</p>
    </section>
  </main>
</template>
