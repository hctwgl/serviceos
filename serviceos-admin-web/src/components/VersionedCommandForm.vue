<script setup lang="ts">
defineProps<{
  title: string
  version: number | null
  busy?: boolean
  submitLabel?: string
  hint?: string
}>()

const emit = defineEmits<{
  submit: []
}>()
</script>

<template>
  <article class="form" data-testid="versioned-command-form">
    <h3>{{ title }}</h3>
    <p v-if="hint" class="hint">{{ hint }}</p>
    <p class="version">当前版本：{{ version ?? '—' }}（提交携带 If-Match + Idempotency-Key）</p>
    <slot />
    <button type="button" :disabled="busy || version == null" @click="emit('submit')">
      {{ submitLabel ?? '提交' }}
    </button>
  </article>
</template>

<style scoped>
.form {
  display: grid;
  gap: 0.55rem;
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.15rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
}
h3 {
  margin: 0;
}
.hint,
.version {
  margin: 0;
  color: #627d98;
  font-size: 0.85rem;
}
button {
  justify-self: start;
  border: 1px solid #bcccdc;
  background: #243b53;
  color: #fff;
  border-radius: 6px;
  padding: 0.45rem 0.9rem;
}
button:disabled {
  opacity: 0.55;
}
</style>
