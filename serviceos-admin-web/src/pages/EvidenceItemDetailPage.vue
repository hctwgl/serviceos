<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getEvidenceItem, type EvidenceItem } from '../api/formsEvidence'

const route = useRoute()
const evidenceItemId = computed(() => String(route.params.id ?? ''))
const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<EvidenceItem | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    detail.value = await getEvidenceItem(evidenceItemId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载资料项详情失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

watch(evidenceItemId, () => {
  if (evidenceItemId.value) void load()
})
onMounted(() => {
  if (evidenceItemId.value) void load()
})
</script>

<template>
  <section class="detail">
    <header class="top">
      <div>
        <h2>资料项详情</h2>
        <p class="meta">{{ evidenceItemId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <template v-else-if="detail">
      <article class="card">
        <dl>
          <div><dt>status</dt><dd>{{ detail.status }}</dd></div>
          <div><dt>itemOrdinal</dt><dd>{{ detail.itemOrdinal }}</dd></div>
          <div><dt>evidenceSlotId</dt><dd>{{ detail.evidenceSlotId }}</dd></div>
          <div><dt>revisionCount</dt><dd>{{ detail.revisions?.length ?? 0 }}</dd></div>
          <div><dt>createdBy</dt><dd>{{ detail.createdBy || '—' }}</dd></div>
          <div><dt>createdAt</dt><dd>{{ detail.createdAt || '—' }}</dd></div>
        </dl>
        <p class="links">
          <RouterLink :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.taskId } }">
            任务详情
          </RouterLink>
        </p>
      </article>
      <pre class="dump">{{ JSON.stringify({ revisions: detail.revisions ?? [] }, null, 2) }}</pre>
    </template>
  </section>
</template>

<style scoped>
.detail {
  display: grid;
  gap: 1rem;
}
.top {
  display: flex;
  justify-content: space-between;
}
.meta {
  margin: 0.25rem 0 0;
  color: #627d98;
  font-family: ui-monospace, monospace;
  font-size: 0.85rem;
}
.card {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.15rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
}
dl {
  margin: 0;
  display: grid;
  gap: 0.45rem;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}
dt {
  font-size: 0.78rem;
  color: #627d98;
}
dd {
  margin: 0.1rem 0 0;
  word-break: break-all;
}
.links {
  display: flex;
  gap: 0.75rem;
  margin-top: 0.75rem;
}
.dump {
  background: #f0f4f8;
  border-radius: 8px;
  padding: 0.75rem;
  overflow: auto;
  max-height: 320px;
  font-size: 0.8rem;
}
.error {
  color: #9b1c1c;
}
button {
  border: 1px solid #bcccdc;
  background: #f0f4f8;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
</style>
