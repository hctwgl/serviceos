<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import QueueTable from './QueueTable.vue'
import { createProject, listAuthorizedProjects, type ProjectPage } from '../api/projects'

const loading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const page = ref<ProjectPage | null>(null)
const cursor = ref<string | undefined>()
const status = ref('ACTIVE')
const clientId = ref('')
const activeOn = ref('')

const createCode = ref('')
const createClientId = ref('client-demo')
const createName = ref('')
const createStartsOn = ref(new Date().toISOString().slice(0, 10))
const createEndsOn = ref('')
const createRegionCodes = ref('')
const createNetworkIds = ref('')
const createdProjectId = ref('')

async function load(next?: string) {
  loading.value = true
  error.value = null
  try {
    page.value = await listAuthorizedProjects({
      cursor: next,
      limit: '20',
      status: status.value || undefined,
      clientId: clientId.value.trim() || undefined,
      activeOn: activeOn.value.trim() || undefined,
    })
    cursor.value = page.value.nextCursor ?? undefined
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载项目目录失败'
  } finally {
    loading.value = false
  }
}

function search() {
  cursor.value = undefined
  return load()
}

async function create() {
  busy.value = true
  message.value = null
  error.value = null
  try {
    if (!createCode.value.trim() || !createName.value.trim()) {
      throw new Error('需要 code 与 name')
    }
    const regionCodes = createRegionCodes.value
      .split(/[,\s]+/)
      .map((v) => v.trim())
      .filter(Boolean)
    const networkIds = createNetworkIds.value
      .split(/[,\s]+/)
      .map((v) => v.trim())
      .filter(Boolean)
    const created = await createProject({
      code: createCode.value.trim(),
      clientId: createClientId.value.trim(),
      name: createName.value.trim(),
      startsOn: createStartsOn.value,
      endsOn: createEndsOn.value.trim() || null,
      regionCodes,
      networkIds,
    })
    createdProjectId.value = created.data.id
    message.value = `已创建项目 ${created.data.code} / ${created.data.id}`
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '创建项目失败'
  } finally {
    busy.value = false
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
  <section class="page">
    <form class="filters" @submit.prevent="search">
      <label>
        status
        <select v-model="status" aria-label="project status filter">
          <option value="">（不限）</option>
          <option value="DRAFT">DRAFT</option>
          <option value="ACTIVE">ACTIVE</option>
          <option value="SUSPENDED">SUSPENDED</option>
          <option value="CLOSED">CLOSED</option>
        </select>
      </label>
      <label>
        clientId
        <input
          v-model="clientId"
          aria-label="project clientId filter"
          placeholder="可选"
        />
      </label>
      <label>
        activeOn
        <input
          v-model="activeOn"
          aria-label="project activeOn filter"
          type="date"
        />
      </label>
      <button type="submit" :disabled="loading">查询</button>
    </form>

    <article class="card">
      <h3>创建项目</h3>
      <label>code<input v-model="createCode" placeholder="PRJ_DEMO_01" /></label>
      <label>clientId<input v-model="createClientId" /></label>
      <label>name<input v-model="createName" /></label>
      <label>startsOn<input v-model="createStartsOn" type="date" /></label>
      <label>endsOn（可选）<input v-model="createEndsOn" type="date" /></label>
      <label>regionCodes（逗号分隔，可选）<input v-model="createRegionCodes" /></label>
      <label>networkIds（逗号分隔，可选）<input v-model="createNetworkIds" /></label>
      <button type="button" :disabled="busy" @click="create">createProject</button>
      <p v-if="message" class="ok">{{ message }}</p>
      <p v-if="createdProjectId" class="links">
        <RouterLink :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: createdProjectId } }">
          打开新建项目 {{ createdProjectId }}
        </RouterLink>
      </p>
    </article>

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

    <p v-if="page?.items?.length" class="links">
      打开详情：
      <RouterLink
        v-for="item in page.items"
        :key="item.id"
        :to="{ name: 'ADMIN.PROJECT.DETAIL', params: { id: item.id } }"
      >
        {{ item.code }}
      </RouterLink>
    </p>
  </section>
</template>

<style scoped>
.page { display: grid; gap: 1rem; }
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: end;
}
.card {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.15rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
  display: grid;
  gap: 0.55rem;
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
.links {
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  font-size: 0.9rem;
}
.ok { color: #054e31; margin: 0; }
</style>
