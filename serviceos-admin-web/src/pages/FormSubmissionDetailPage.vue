<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getFormSubmission, type FormSubmission } from '../api/formsEvidence'

const route = useRoute()
const submissionId = computed(() => String(route.params.id ?? ''))
const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<FormSubmission | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    detail.value = await getFormSubmission(submissionId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载表单提交详情失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

watch(submissionId, () => {
  if (submissionId.value) void load()
})
onMounted(() => {
  if (submissionId.value) void load()
})
</script>

<template>
  <section class="detail">
    <header class="top">
      <div>
        <h2>表单提交详情</h2>
        <p class="meta">{{ submissionId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <template v-else-if="detail">
      <article class="card">
        <dl>
          <div><dt>formKey</dt><dd>{{ detail.formKey }}</dd></div>
          <div><dt>validationStatus</dt><dd>{{ detail.validationStatus }}</dd></div>
          <div><dt>submissionVersion</dt><dd>{{ detail.submissionVersion }}</dd></div>
          <div><dt>formVersionId</dt><dd>{{ detail.formVersionId }}</dd></div>
          <div><dt>contentDigest</dt><dd>{{ detail.contentDigest }}</dd></div>
          <div><dt>prefillVersion</dt><dd>{{ detail.prefillVersion || '—' }}</dd></div>
          <div><dt>submittedBy</dt><dd>{{ detail.submittedBy }}</dd></div>
          <div><dt>submittedAt</dt><dd>{{ detail.submittedAt }}</dd></div>
          <div><dt>errorCount</dt><dd>{{ detail.errors?.length ?? 0 }}</dd></div>
          <div><dt>warningCount</dt><dd>{{ detail.warnings?.length ?? 0 }}</dd></div>
        </dl>
        <p class="links">
          <RouterLink :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.taskId } }">
            任务详情
          </RouterLink>
        </p>
      </article>
      <pre class="dump">{{
        JSON.stringify(
          {
            values: detail.values,
            errors: detail.errors,
            warnings: detail.warnings,
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
