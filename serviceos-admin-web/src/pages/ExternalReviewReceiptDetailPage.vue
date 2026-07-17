<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  getExternalReviewReceipt,
  type ExternalReviewReceipt,
} from '../api/formsEvidence'

const route = useRoute()
const receiptId = computed(() => String(route.params.id ?? ''))
const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<ExternalReviewReceipt | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    detail.value = await getExternalReviewReceipt(receiptId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载外部审核回执详情失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

watch(receiptId, () => {
  if (receiptId.value) void load()
})
onMounted(() => {
  if (receiptId.value) void load()
})
</script>

<template>
  <section class="detail">
    <header class="top">
      <div>
        <h2>外部审核回执</h2>
        <p class="meta">{{ receiptId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <template v-else-if="detail">
      <article class="card">
        <dl>
          <div><dt>result</dt><dd>{{ detail.result }}</dd></div>
          <div><dt>externalKey</dt><dd>{{ detail.externalKey }}</dd></div>
          <div><dt>callbackBatchRef</dt><dd>{{ detail.callbackBatchRef }}</dd></div>
          <div><dt>mappingVersionId</dt><dd>{{ detail.mappingVersionId }}</dd></div>
          <div><dt>receivedBy</dt><dd>{{ detail.receivedBy }}</dd></div>
          <div><dt>receivedAt</dt><dd>{{ detail.receivedAt }}</dd></div>
          <div><dt>inboundEnvelopeId</dt><dd>{{ detail.inboundEnvelopeId }}</dd></div>
          <div><dt>canonicalMessageId</dt><dd>{{ detail.canonicalMessageId }}</dd></div>
          <div><dt>coordinationTaskId</dt><dd>{{ detail.coordinationTaskId || '—' }}</dd></div>
          <div><dt>payloadRef</dt><dd>{{ detail.payloadRef || '—' }}</dd></div>
        </dl>
        <p class="links">
          <RouterLink
            :to="{ name: 'ADMIN.REVIEW.DETAIL', params: { id: detail.reviewCaseId } }"
          >
            审核案例
          </RouterLink>
          <RouterLink
            v-if="detail.coordinationTaskId"
            :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.coordinationTaskId } }"
          >
            协调任务
          </RouterLink>
          <RouterLink
            :to="{
              name: 'ADMIN.INTEGRATION.CANONICAL.DETAIL',
              params: { id: detail.canonicalMessageId },
            }"
          >
            打开 Canonical {{ detail.canonicalMessageId }}
          </RouterLink>
        </p>
      </article>
      <pre class="dump">{{
        JSON.stringify(
          {
            reviewDecisionId: detail.reviewDecisionId,
            reasonCodes: detail.reasonCodes ?? [],
            affectedTargets: detail.affectedTargets ?? [],
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
