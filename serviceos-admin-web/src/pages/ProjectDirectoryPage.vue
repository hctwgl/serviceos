<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import QueueTable from './QueueTable.vue'
import { listAuthorizedProjects, type ProjectPage } from '../api/projects'

const loading = ref(false)
const error = ref<string | null>(null)
const page = ref<ProjectPage | null>(null)
const cursor = ref<string | undefined>()
const status = ref('ACTIVE')
const clientId = ref('')

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listAuthorizedProjects({
      cursor: next,
      limit: '20',
      status: status.value || undefined,
      clientId: clientId.value || undefined,
    })
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载项目目录失败'
  } finally {
    loading.value = false
  }
}

const rows = computed(() =>
  (page.value?.items ?? []).map((item) => ({
    id: item.id,
    code: item.code,
    name: item.name,
    clientId: item.clientId,
    status: item.status,
    startsOn: item.startsOn,
    endsOn: item.endsOn,
  })),
)

onMounted(() => load())
</script>

<template>
  <section>
    <form class="filters" @submit.prevent="load()">
      <label>
        status
        <select v-model="status">
          <option value="">全部</option>
          <option value="DRAFT">DRAFT</option>
          <option value="ACTIVE">ACTIVE</option>
          <option value="SUSPENDED">SUSPENDED</option>
          <option value="CLOSED">CLOSED</option>
        </select>
      </label>
      <label>
        clientId
        <input v-model="clientId" placeholder="可选" />
      </label>
      <button type="submit" :disabled="loading">查询</button>
    </form>

    <QueueTable
      title="授权项目目录"
      :columns="['id', 'code', 'name', 'clientId', 'status', 'startsOn', 'endsOn']"
      :rows="rows"
      :loading="loading"
      :error="error"
      :as-of="page?.asOf"
      :next-cursor="cursor ?? null"
      @refresh="load()"
      @next="load(cursor)"
    />
  </section>
</template>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-bottom: 1rem;
  align-items: end;
}
label {
  display: grid;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: #486581;
}
input,
select,
button {
  border: 1px solid #bcccdc;
  border-radius: 6px;
  padding: 0.4rem 0.65rem;
}
button {
  background: #243b53;
  color: #fff;
  border-color: #243b53;
  cursor: pointer;
}
</style>
