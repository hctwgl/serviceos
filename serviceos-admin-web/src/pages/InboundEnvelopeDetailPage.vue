<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  getCanonicalMessage,
  getInboundEnvelope,
  type CanonicalMessage,
  type InboundEnvelope,
} from '../api/inbound'

const route = useRoute()
const envelopeId = computed(() => String(route.params.id ?? ''))
const loading = ref(false)
const error = ref<string | null>(null)
const envelope = ref<InboundEnvelope | null>(null)
const canonical = ref<CanonicalMessage | null>(null)
const canonicalError = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  canonicalError.value = null
  canonical.value = null
  try {
    envelope.value = await getInboundEnvelope(envelopeId.value)
    if (envelope.value.canonicalMessageId) {
      try {
        canonical.value = await getCanonicalMessage(envelope.value.canonicalMessageId)
      } catch (err) {
        canonicalError.value = err instanceof Error ? err.message : '加载 Canonical 失败'
      }
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载入站 Envelope 失败'
    envelope.value = null
  } finally {
    loading.value = false
  }
}

const workOrderId = computed(() => {
  if (envelope.value?.resultType === 'WORK_ORDER' && envelope.value.resultId) {
    return envelope.value.resultId
  }
  return null
})

watch(envelopeId, () => {
  if (envelopeId.value) void load()
})
onMounted(() => {
  if (envelopeId.value) void load()
})
</script>

<template>
  <section class="detail">
    <header class="top">
      <div>
        <h2>入站 Envelope</h2>
        <p class="meta">{{ envelopeId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <template v-else-if="envelope">
      <article class="card">
        <dl>
          <div><dt>messageType</dt><dd>{{ envelope.messageType }}</dd></div>
          <div><dt>processingStatus</dt><dd>{{ envelope.processingStatus }}</dd></div>
          <div><dt>signatureStatus</dt><dd>{{ envelope.signatureStatus }}</dd></div>
          <div><dt>externalMessageId</dt><dd>{{ envelope.externalMessageId }}</dd></div>
          <div><dt>resultCode</dt><dd>{{ envelope.resultCode ?? '—' }}</dd></div>
          <div><dt>resultType</dt><dd>{{ envelope.resultType ?? '—' }}</dd></div>
          <div><dt>resultId</dt><dd>{{ envelope.resultId ?? '—' }}</dd></div>
          <div><dt>receivedAt</dt><dd>{{ envelope.receivedAt }}</dd></div>
          <div><dt>completedAt</dt><dd>{{ envelope.completedAt ?? '—' }}</dd></div>
          <div><dt>correlationId</dt><dd>{{ envelope.correlationId }}</dd></div>
          <div><dt>rawPayloadDigest</dt><dd>{{ envelope.rawPayloadDigest }}</dd></div>
          <div>
            <dt>canonicalPayloadDigest</dt>
            <dd>{{ envelope.canonicalPayloadDigest ?? '—' }}</dd>
          </div>
        </dl>
        <p class="links">
          <RouterLink
            v-if="workOrderId"
            :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: workOrderId } }"
          >
            工单工作区
          </RouterLink>
          <RouterLink
            v-if="envelope.canonicalMessageId"
            :to="{
              name: 'ADMIN.INTEGRATION.CANONICAL.DETAIL',
              params: { id: envelope.canonicalMessageId },
            }"
          >
            打开 Canonical {{ envelope.canonicalMessageId }}
          </RouterLink>
          <span v-if="!workOrderId && !envelope.canonicalMessageId" class="meta">
            无关联详情链接
          </span>
        </p>
      </article>

      <article class="card">
        <h3>Canonical Message</h3>
        <p v-if="!envelope.canonicalMessageId" class="meta">尚未关联 Canonical</p>
        <p v-else-if="canonicalError" class="error">{{ canonicalError }}</p>
        <template v-else-if="canonical">
          <dl>
            <div>
              <dt>canonicalMessageId</dt>
              <dd>
                <RouterLink
                  :to="{
                    name: 'ADMIN.INTEGRATION.CANONICAL.DETAIL',
                    params: { id: canonical.canonicalMessageId },
                  }"
                >
                  {{ canonical.canonicalMessageId }}
                </RouterLink>
              </dd>
            </div>
            <div><dt>businessKey</dt><dd>{{ canonical.businessKey }}</dd></div>
            <div><dt>messageType</dt><dd>{{ canonical.messageType }}</dd></div>
            <div><dt>processingStatus</dt><dd>{{ canonical.processingStatus }}</dd></div>
            <div><dt>resultCode</dt><dd>{{ canonical.resultCode ?? '—' }}</dd></div>
            <div><dt>resultType</dt><dd>{{ canonical.resultType ?? '—' }}</dd></div>
            <div><dt>resultId</dt><dd>{{ canonical.resultId ?? '—' }}</dd></div>
            <div><dt>payloadDigest</dt><dd>{{ canonical.payloadDigest }}</dd></div>
            <div><dt>createdAt</dt><dd>{{ canonical.createdAt }}</dd></div>
            <div><dt>processedAt</dt><dd>{{ canonical.processedAt ?? '—' }}</dd></div>
          </dl>
        </template>
        <p v-else class="meta">Canonical 加载中…</p>
      </article>
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
  display: grid;
  gap: 0.55rem;
}
h2,
h3 {
  margin: 0 0 0.5rem;
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
  font-family: ui-monospace, monospace;
  font-size: 0.85rem;
}
button {
  border: 1px solid #243b53;
  background: #243b53;
  color: #fff;
  border-radius: 6px;
  padding: 0.4rem 0.65rem;
  cursor: pointer;
}
.error {
  color: #9b1c1c;
}
.links {
  display: flex;
  gap: 0.75rem;
  margin: 0;
}
</style>
