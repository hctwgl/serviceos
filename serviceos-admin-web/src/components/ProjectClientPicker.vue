<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Select } from 'ant-design-vue'
import { listProjectClients, type ProjectClientDirectoryItem } from '../api/projectCatalog'
import { getProjectReferenceOptions, type ProjectClientOption } from '../api/projectReferenceOptions'
import { safeAccessDeniedMessage } from '../api/client'

defineProps<{
  modelValue: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const loading = ref(false)
const error = ref<string | null>(null)
const directory = ref<ProjectClientDirectoryItem[]>([])
const usage = ref<ProjectClientOption[]>([])

const options = computed(() => {
  const usageMap = new Map(usage.value.map((item) => [item.clientId, item.projectCount]))
  const fromDirectory = directory.value.map((item) => ({
    value: item.clientCode,
    label: `${item.displayName}（${item.clientCode}）${
      usageMap.has(item.clientCode) ? ` · ${usageMap.get(item.clientCode)} 个项目` : ''
    }`,
  }))
  const known = new Set(fromDirectory.map((item) => item.value))
  const fromUsageOnly = usage.value
    .filter((item) => !known.has(item.clientId))
    .map((item) => ({
      value: item.clientId,
      label: `${item.displayName}（${item.clientId}）· ${item.projectCount} 个项目`,
    }))
  return [...fromDirectory, ...fromUsageOnly]
})

async function load() {
  loading.value = true
  error.value = null
  try {
    const [clients, refs] = await Promise.all([
      listProjectClients(),
      getProjectReferenceOptions(),
    ])
    directory.value = clients.items
    usage.value = refs.clients
  } catch (err) {
    directory.value = []
    usage.value = []
    error.value = safeAccessDeniedMessage(err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="picker" data-testid="project-client-picker">
    <Select
      :value="modelValue || undefined"
      show-search
      allow-clear
      style="width: 100%"
      :disabled="disabled"
      :loading="loading"
      placeholder="选择所属车企"
      aria-label="project clientId"
      :options="options"
      :filter-option="(input, option) => String(option?.label ?? '').includes(input)"
      @update:value="(value) => emit('update:modelValue', String(value ?? ''))"
    />
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="!loading && options.length === 0" class="hint">
      当前无可选车企。请先登记车企主数据，或创建项目时自动登记编码。
    </p>
    <p v-else class="hint">选项来自车企主数据目录，并标注授权范围内的项目使用数。</p>
  </div>
</template>

<style scoped>
.hint {
  margin: 6px 0 0;
  color: var(--sos-color-text-tertiary, #6b7280);
  font-size: 12px;
}
.error {
  margin: 6px 0 0;
  color: var(--sos-color-status-critical-fg, #cf1322);
  font-size: 12px;
}
</style>
