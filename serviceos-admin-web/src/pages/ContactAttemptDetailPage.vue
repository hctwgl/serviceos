<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getContactAttempt, type ContactAttempt } from '../api/appointments'

const route = useRoute()
const contactAttemptId = computed(() => String(route.params.id ?? ''))
const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<ContactAttempt | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    detail.value = await getContactAttempt(contactAttemptId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载联系详情失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

watch(contactAttemptId, () => {
  if (contactAttemptId.value) void load()
})
onMounted(() => {
  if (contactAttemptId.value) void load()
})
</script>

<template>
  <section class="detail">
    <header class="top">
      <div>
        <h2>联系详情</h2>
        <p class="meta">{{ contactAttemptId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <template v-else-if="detail">
      <article class="card">
        <dl>
          <div><dt>channel</dt><dd>{{ detail.channel }}</dd></div>
          <div><dt>resultCode</dt><dd>{{ detail.resultCode }}</dd></div>
          <div><dt>contactedPartyRef</dt><dd>{{ detail.contactedPartyRef }}</dd></div>
          <div><dt>startedAt</dt><dd>{{ detail.startedAt }}</dd></div>
          <div><dt>endedAt</dt><dd>{{ detail.endedAt }}</dd></div>
          <div><dt>nextContactAt</dt><dd>{{ detail.nextContactAt || '—' }}</dd></div>
          <div><dt>recordingRef</dt><dd>{{ detail.recordingRef || '—' }}</dd></div>
          <div><dt>actorId</dt><dd>{{ detail.actorId }}</dd></div>
          <div><dt>createdAt</dt><dd>{{ detail.createdAt }}</dd></div>
        </dl>
        <p class="links">
          <RouterLink
            :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: detail.workOrderId } }"
          >
            工单工作区
          </RouterLink>
          <RouterLink :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.taskId } }">
            任务详情
          </RouterLink>
        </p>
      </article>
      <pre class="dump">{{
        JSON.stringify(
          {
            note: detail.note ?? null,
            projectId: detail.projectId,
            workOrderId: detail.workOrderId,
            taskId: detail.taskId,
          },
          null,
          2,
        )
      }}</pre>
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
  flex-wrap: wrap;
}
.dump {
  background: #f0f4f8;
  border-radius: 8px;
  padding: 0.75rem;
  overflow: auto;
  font-size: 0.85rem;
}
.error {
  color: #9b1c1c;
}
button {
  border: 1px solid #243b53;
  background: #243b53;
  color: #fff;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
