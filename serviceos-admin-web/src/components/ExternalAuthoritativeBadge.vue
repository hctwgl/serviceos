<script setup lang="ts">
defineProps<{
  authorityMode: 'LOCAL' | 'EXTERNAL_AUTHORITATIVE' | string
  sourceSystem?: string | null
  sourceKey?: string | null
  sourceVersion?: number | null
  lastSyncedAt?: string | null
}>()
</script>

<template>
  <div
    v-if="authorityMode === 'EXTERNAL_AUTHORITATIVE'"
    class="badge"
    data-testid="external-authoritative-badge"
  >
    <strong>EXTERNAL_AUTHORITATIVE</strong>
    <span>来源字段只读</span>
    <dl>
      <div v-if="sourceSystem"><dt>sourceSystem</dt><dd>{{ sourceSystem }}</dd></div>
      <div v-if="sourceKey"><dt>sourceKey</dt><dd>{{ sourceKey }}</dd></div>
      <div v-if="sourceVersion != null"><dt>sourceVersion</dt><dd>{{ sourceVersion }}</dd></div>
      <div>
        <dt>同步状态</dt>
        <dd>{{ lastSyncedAt ? `lastSyncedAt=${lastSyncedAt}` : '以单元 sourceVersion/updatedAt 为准' }}</dd>
      </div>
    </dl>
  </div>
  <span v-else class="local" data-testid="local-authority-badge">LOCAL</span>
</template>

<style scoped>
.badge {
  display: grid;
  gap: 0.35rem;
  border: 1px solid #829ab1;
  background: #f0f4f8;
  border-radius: 8px;
  padding: 0.65rem 0.8rem;
  font-size: 0.85rem;
  color: #243b53;
}
.badge strong {
  color: #102a43;
}
dl {
  margin: 0;
  display: grid;
  gap: 0.2rem;
}
dl div {
  display: grid;
  grid-template-columns: 7.5rem 1fr;
  gap: 0.4rem;
}
dt {
  color: #627d98;
}
dd {
  margin: 0;
  font-family: ui-monospace, monospace;
}
.local {
  display: inline-block;
  padding: 0.15rem 0.45rem;
  border-radius: 4px;
  background: #d9e2ec;
  color: #243b53;
  font-size: 0.8rem;
}
</style>
