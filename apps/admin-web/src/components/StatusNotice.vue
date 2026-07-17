<script setup lang="ts">
withDefaults(defineProps<{ tone?: 'info' | 'success' | 'warning' | 'danger'; title: string; detail?: string; requestId?: string }>(), { tone: 'info' })
</script>

<template>
  <div class="status-notice" :class="`status-${tone}`" :role="tone === 'danger' ? 'alert' : 'status'" :aria-live="tone === 'danger' ? 'assertive' : 'polite'">
    <span class="status-dot" aria-hidden="true" />
    <div><strong>{{ title }}</strong><p v-if="detail">{{ detail }}</p><small v-if="requestId">请求标识：{{ requestId }}</small></div>
  </div>
</template>
