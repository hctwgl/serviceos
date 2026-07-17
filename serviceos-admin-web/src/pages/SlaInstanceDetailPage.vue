<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getSlaInstance, type SlaInstanceDetail } from '../api/slaDetail'
import QueueTable from './QueueTable.vue'

const route = useRoute()
const slaInstanceId = computed(() => String(route.params.id ?? ''))
const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<SlaInstanceDetail | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    detail.value = await getSlaInstance(slaInstanceId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载 SLA 详情失败'
    detail.value = null
  } finally {
    loading.value = false
  }
}

const milestoneRows = computed(() =>
  (detail.value?.milestones ?? []).map((item, index) => ({ index, ...item })),
)
const segmentRows = computed(() =>
  (detail.value?.segments ?? []).map((item, index) => ({ index, ...item })),
)

watch(slaInstanceId, () => {
  if (slaInstanceId.value) void load()
})
onMounted(() => {
  if (slaInstanceId.value) void load()
})
</script>

<template>
  <section class="detail">
    <header class="top">
      <div>
        <h2>SLA 实例</h2>
        <p class="meta">{{ slaInstanceId }}</p>
      </div>
      <button type="button" :disabled="loading" @click="load">刷新</button>
    </header>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">加载中…</p>
    <template v-else-if="detail">
      <article class="card">
        <dl>
          <div><dt>status</dt><dd>{{ detail.instance.status }}</dd></div>
          <div><dt>slaRef</dt><dd>{{ detail.instance.slaRef }}</dd></div>
          <div>
            <dt>workOrderId</dt>
            <dd>
              <RouterLink
                :to="{
                  name: 'ADMIN.WORKORDER.WORKSPACE',
                  params: { id: detail.instance.workOrderId },
                }"
              >
                {{ detail.instance.workOrderId }}
              </RouterLink>
            </dd>
          </div>
          <div>
            <dt>taskId</dt>
            <dd>
              <RouterLink
                :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.instance.taskId } }"
              >
                {{ detail.instance.taskId }}
              </RouterLink>
            </dd>
          </div>
          <div><dt>deadlineAt</dt><dd>{{ detail.instance.deadlineAt }}</dd></div>
          <div><dt>remainingSeconds</dt><dd>{{ detail.instance.remainingSeconds }}</dd></div>
          <div><dt>overdueSeconds</dt><dd>{{ detail.instance.overdueSeconds }}</dd></div>
          <div><dt>asOf</dt><dd>{{ detail.asOf }}</dd></div>
        </dl>
        <p class="links">
          <RouterLink :to="{ name: 'ADMIN.WORKORDER.WORKSPACE', params: { id: detail.instance.workOrderId } }">
            工单工作区
          </RouterLink>
          <RouterLink :to="{ name: 'ADMIN.TASK.DETAIL', params: { id: detail.instance.taskId } }">
            任务详情
          </RouterLink>
        </p>
      </article>
      <pre class="dump">{{ JSON.stringify({ milestones: detail.milestones, segments: detail.segments }, null, 2) }}</pre>
      <QueueTable
        title="Milestones（索引）"
        :columns="['index']"
        :rows="milestoneRows"
        :loading="false"
        :error="null"
        :next-cursor="null"
        @refresh="load"
        @next="() => undefined"
      />
      <QueueTable
        title="Segments（索引）"
        :columns="['index']"
        :rows="segmentRows"
        :loading="false"
        :error="null"
        :next-cursor="null"
        @refresh="load"
        @next="() => undefined"
      />
    </template>
  </section>
</template>

<style scoped>
.detail { display: grid; gap: 1rem; }
.top { display: flex; justify-content: space-between; }
.meta { margin: .25rem 0 0; color: #627d98; font-family: ui-monospace, monospace; font-size: .85rem; }
.card { background: #fff; border-radius: 12px; padding: 1rem 1.15rem; box-shadow: 0 1px 3px rgb(16 42 67 / 8%); }
dl { margin: 0; display: grid; gap: .45rem; grid-template-columns: repeat(auto-fit,minmax(180px,1fr)); }
dt { font-size: .78rem; color: #627d98; }
dd { margin: .1rem 0 0; word-break: break-all; }
.links { display: flex; gap: .75rem; margin-top: .75rem; }
.dump { background: #f0f4f8; border-radius: 8px; padding: .75rem; overflow: auto; max-height: 320px; font-size: .8rem; }
.error { color: #9b1c1c; }
button { border: 1px solid #bcccdc; background: #f0f4f8; border-radius: 6px; padding: .4rem .75rem; cursor: pointer; }
</style>
