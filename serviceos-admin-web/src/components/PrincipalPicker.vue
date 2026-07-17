<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  listSecurityPrincipals,
  type SecurityPrincipal,
} from '../api/securityPrincipals'

const props = defineProps<{
  modelValue: string | null
  label?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string | null]
  selected: [principal: SecurityPrincipal | null]
}>()

const query = ref('')
const loading = ref(false)
const error = ref<string | null>(null)
const results = ref<SecurityPrincipal[]>([])
const selected = ref<SecurityPrincipal | null>(null)

async function search() {
  const q = query.value.trim()
  if (!q) {
    error.value = '请输入姓名或工号搜索，不要粘贴 UUID 作为主路径'
    return
  }
  loading.value = true
  error.value = null
  try {
    const page = await listSecurityPrincipals({ query: q, limit: '20' })
    results.value = page.items
    if (page.items.length === 0) {
      error.value = '未找到匹配主体'
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '目录搜索失败'
    results.value = []
  } finally {
    loading.value = false
  }
}

function choose(item: SecurityPrincipal) {
  selected.value = item
  emit('update:modelValue', item.id)
  emit('selected', item)
  results.value = []
}

function clear() {
  selected.value = null
  query.value = ''
  results.value = []
  emit('update:modelValue', null)
  emit('selected', null)
}

watch(
  () => props.modelValue,
  (id) => {
    if (!id) {
      selected.value = null
    }
  },
)
</script>

<template>
  <div class="picker" data-testid="principal-picker">
    <label>
      {{ label ?? '选择人员' }}
      <div class="row">
        <input
          v-model="query"
          type="search"
          :disabled="disabled"
          aria-label="principal directory search"
          placeholder="姓名或工号"
          @keydown.enter.prevent="search"
        />
        <button type="button" :disabled="disabled || loading" @click="search">搜索</button>
        <button v-if="selected" type="button" :disabled="disabled" @click="clear">清除</button>
      </div>
    </label>
    <p v-if="selected" class="selected" data-testid="principal-picker-selected">
      已选：{{ selected.displayName }}
      <span v-if="selected.employeeNumber">（{{ selected.employeeNumber }}）</span>
    </p>
    <ul v-if="results.length" class="results" data-testid="principal-picker-results">
      <li v-for="item in results" :key="item.id">
        <button type="button" :disabled="disabled" @click="choose(item)">
          <strong>{{ item.displayName }}</strong>
          <span v-if="item.employeeNumber"> · {{ item.employeeNumber }}</span>
          <span class="status"> · {{ item.status }}</span>
        </button>
      </li>
    </ul>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

<style scoped>
.picker {
  display: grid;
  gap: 0.4rem;
}
label {
  display: grid;
  gap: 0.3rem;
  font-size: 0.85rem;
  color: #486581;
}
.row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}
input {
  flex: 1;
  min-width: 12rem;
  border: 1px solid #bcccdc;
  border-radius: 6px;
  padding: 0.4rem 0.65rem;
}
button {
  border: 1px solid #bcccdc;
  background: #243b53;
  color: #fff;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
}
.results {
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #fff;
}
.results button {
  width: 100%;
  text-align: left;
  background: transparent;
  color: #102a43;
  border: 0;
  border-radius: 0;
  border-bottom: 1px solid #f0f4f8;
}
.results li:last-child button {
  border-bottom: 0;
}
.selected {
  margin: 0;
  color: #054e31;
  font-size: 0.9rem;
}
.status {
  color: #627d98;
}
.error {
  margin: 0;
  color: #b42318;
  font-size: 0.85rem;
}
</style>
