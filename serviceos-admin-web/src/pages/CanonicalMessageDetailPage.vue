<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getCanonicalMessage, type CanonicalMessage } from '../api/inbound'

const route = useRoute()
const messageId = computed(() => String(route.params.id ?? ''))
const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<CanonicalMessage | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    detail.value = await getCanonicalMessage(messageId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载 Canonical Message 失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

const workOrderId = computed(() => {
  if (detail.value?.resultType === 'WORK_ORDER' && detail.value.resultId) {
    return detail.value.resultId
  }
  return null
})

watch(messageId, () => {
  if (messageId.value) void load()
})
onMounted(() => {
  if (messageId.value) void load()
})
</script>

<template>
  <section class="detail">
    <header class="top">
      <div>
        <h2>Canonical Message</h2>
        <p class="meta">{{ messageId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <template v-else-if="detail">
      <article class="card">
        <dl>
          <div><dt>messageType</dt><dd>{{ detail.messageType }}</dd></div>
          <div><dt>businessKey</dt><dd>{{ detail.businessKey }}</dd></div>
          <div><dt>processingStatus</dt><dd>{{ detail.processingStatus }}</dd></div>
          <div>
            <dt>projectId</dt>
            <dd>
              <RouterLink
                v-if="detail.projectId"
                :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: detail.projectId } }"
              >
                {{ detail.projectId }}
              </RouterLink>
              <template v-else>—</template>
            </dd>
          </div>
          <div><dt>connectorVersionId</dt><dd>{{ detail.connectorVersionId }}</dd></div>
          <div><dt>mappingVersionId</dt><dd>{{ detail.mappingVersionId }}</dd></div>
          <div><dt>resultCode</dt><dd>{{ detail.resultCode ?? '—' }}</dd></div>
          <div><dt>resultType</dt><dd>{{ detail.resultType ?? '—' }}</dd></div>
          <div>
            <dt>resultId</dt>
            <dd>
              <RouterLink
                v-if="workOrderId"
                :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: workOrderId } }"
              >
                {{ workOrderId }}
              </RouterLink>
              <template v-else>{{ detail.resultId ?? '—' }}</template>
            </dd>
          </div>
          <div><dt>payloadDigest</dt><dd>{{ detail.payloadDigest }}</dd></div>
          <div><dt>createdAt</dt><dd>{{ detail.createdAt }}</dd></div>
          <div><dt>processedAt</dt><dd>{{ detail.processedAt ?? '—' }}</dd></div>
        </dl>
        <p class="links">
          <RouterLink
            v-if="detail.projectId"
            :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: detail.projectId } }"
          >
            打开项目 {{ detail.projectId }}
          </RouterLink>
          <RouterLink
            v-if="workOrderId"
            :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: workOrderId } }"
          >
            工单工作区
          </RouterLink>
          <span v-if="!detail.projectId && !workOrderId" class="meta">无关联详情链接</span>
        </p>
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
h2 {
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
  flex-wrap: wrap;
}
</style>
