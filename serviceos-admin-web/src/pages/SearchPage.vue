<script setup lang="ts">
import PageContainer from '../patterns/PageContainer.vue'
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  searchResources,
  type ControlledSearchHit,
  type SearchResourceType,
} from '../api/search'

const ALL_TYPES: SearchResourceType[] = [
  'WORK_ORDER',
  'EXTERNAL_ORDER',
  'NETWORK',
  'TECHNICIAN',
]

const q = ref('')
const selectedTypes = ref<SearchResourceType[]>([...ALL_TYPES])
const items = ref<ControlledSearchHit[]>([])
const message = ref<string | null>(null)
const busy = ref(false)
const qDigest = ref<string | null>(null)

function toggleType(type: SearchResourceType, checked: boolean) {
  if (checked) {
    if (!selectedTypes.value.includes(type)) {
      selectedTypes.value = [...selectedTypes.value, type]
    }
    return
  }
  selectedTypes.value = selectedTypes.value.filter((value) => value !== type)
}

async function runSearch() {
  busy.value = true
  message.value = null
  items.value = []
  qDigest.value = null
  try {
    const result = await searchResources(q.value.trim(), selectedTypes.value)
    items.value = result.items
    qDigest.value = result.meta.qDigest
    if (result.meta.omittedTypes.length > 0) {
      message.value = `已省略无权限类型：${result.meta.omittedTypes.join(', ')}`
    } else if (result.items.length === 0) {
      message.value = '无匹配结果'
    }
  } catch (err) {
    message.value = err instanceof Error ? err.message : '搜索失败'
  } finally {
    busy.value = false
  }
}

function typeLabel(type: SearchResourceType): string {
  switch (type) {
    case 'WORK_ORDER':
      return '工单'
    case 'EXTERNAL_ORDER':
      return '外部单号'
    case 'NETWORK':
      return '网点'
    case 'TECHNICIAN':
      return '师傅'
  }
}
</script>

<template>
  <PageContainer title="全局搜索" description="按授权范围检索工单、任务与项目等资源。"><form class="search-form" @submit.prevent="runSearch">
      <label>
        关键词
        <input
          v-model="q"
          data-testid="search-q"
          type="search"
          maxlength="200"
          placeholder="工单号 / 网点编码 / 师傅姓名…"
          required
        />
      </label>
      <fieldset class="types">
        <legend>类型</legend>
        <label v-for="type in ALL_TYPES" :key="type" class="type">
          <input
            type="checkbox"
            :checked="selectedTypes.includes(type)"
            :data-testid="`search-type-${type}`"
            @change="toggleType(type, ($event.target as HTMLInputElement).checked)"
          />
          {{ typeLabel(type) }}
        </label>
      </fieldset>
      <button data-testid="search-submit" type="submit" :disabled="busy || !q.trim()">
        {{ busy ? '搜索中…' : '搜索' }}
      </button>
    </form>

    <p v-if="message" class="message" data-testid="search-message">{{ message }}</p>
    <p v-if="qDigest" class="digest" data-testid="search-q-digest">qDigest={{ qDigest }}</p>

    <ul class="results" data-testid="search-results">
      <li v-for="hit in items" :key="`${hit.type}:${hit.resourceRef}:${hit.matchReason}`">
        <RouterLink
          :to="hit.deepLink"
          :data-testid="`search-hit-${hit.type}`"
          class="hit-link"
        >
          <span class="type-badge">{{ typeLabel(hit.type) }}</span>
          <strong>{{ hit.primaryLabel }}</strong>
          <span v-if="hit.maskedSecondaryLabel" class="secondary">{{ hit.maskedSecondaryLabel }}</span>
          <span class="reason">{{ hit.matchReason }}</span>
        </RouterLink>
      </li>
    </ul>
  </PageContainer>
</template>

<style scoped>
.page {
  max-width: 920px;
}
.hint,
.message,
.digest {
  color: #486581;
  font-size: 0.9rem;
}
.search-form {
  display: grid;
  gap: 1rem;
  margin: 1.25rem 0;
  padding: 1rem 0;
  border-top: 1px solid #d9e2ec;
  border-bottom: 1px solid #d9e2ec;
}
label {
  display: grid;
  gap: 0.35rem;
  font-size: 0.9rem;
}
input[type='search'] {
  padding: 0.55rem 0.7rem;
  border: 1px solid #9fb3c8;
  border-radius: 6px;
  font: inherit;
}
.types {
  border: none;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem 1.25rem;
}
.type {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}
button {
  justify-self: start;
  padding: 0.55rem 1rem;
  border: none;
  border-radius: 6px;
  background: #243b53;
  color: #fff;
  cursor: pointer;
}
button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.results {
  list-style: none;
  padding: 0;
  margin: 1rem 0 0;
  display: grid;
  gap: 0.5rem;
}
.hit-link {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 0.35rem 0.75rem;
  align-items: baseline;
  padding: 0.65rem 0.1rem;
  color: inherit;
  text-decoration: none;
  border-bottom: 1px solid #e4eaf1;
}
.hit-link:hover strong {
  text-decoration: underline;
}
.type-badge {
  font-size: 0.75rem;
  color: #334e68;
  background: #d9e2ec;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
}
.secondary,
.reason {
  grid-column: 2 / -1;
  font-size: 0.85rem;
  color: #627d98;
}
.reason {
  grid-column: 3;
  grid-row: 1;
  justify-self: end;
}
</style>
