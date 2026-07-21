<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Select } from 'ant-design-vue'
import { listRegionCatalog, type RegionCatalogItem } from '../api/projectCatalog'
import { getProjectReferenceOptions, type ProjectRegionOption } from '../api/projectReferenceOptions'
import { safeAccessDeniedMessage } from '../api/client'

defineProps<{
  modelValue: string[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

const loading = ref(false)
const error = ref<string | null>(null)
const catalog = ref<RegionCatalogItem[]>([])
const usage = ref<ProjectRegionOption[]>([])

const options = computed(() => {
  const usageMap = new Map(usage.value.map((item) => [item.regionCode, item.projectCount]))
  const fromCatalog = catalog.value.map((item) => ({
    value: item.regionCode,
    label: `${item.regionName}（${item.regionCode}）${
      usageMap.has(item.regionCode) ? ` · ${usageMap.get(item.regionCode)} 个项目` : ''
    }`,
  }))
  const known = new Set(fromCatalog.map((item) => item.value))
  const fromUsageOnly = usage.value
    .filter((item) => !known.has(item.regionCode))
    .map((item) => ({
      value: item.regionCode,
      label: `${item.regionName}（${item.regionCode}）· ${item.projectCount} 个项目`,
    }))
  return [...fromCatalog, ...fromUsageOnly]
})

async function load() {
  loading.value = true
  error.value = null
  try {
    const [regions, refs] = await Promise.all([
      listRegionCatalog({ parentCode: '*', limit: 200 }),
      getProjectReferenceOptions(),
    ])
    catalog.value = regions.items
    usage.value = refs.regions
  } catch (err) {
    catalog.value = []
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
  <div class="picker" data-testid="project-region-picker">
    <Select
      :value="modelValue"
      mode="tags"
      show-search
      allow-clear
      style="width: 100%"
      :disabled="disabled"
      :loading="loading"
      placeholder="搜索并选择服务区域"
      aria-label="服务区域编码"
      :options="options"
      :filter-option="(input, option) => String(option?.label ?? '').includes(input)"
      @update:value="(value) => emit('update:modelValue', (value as string[]) ?? [])"
    />
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else class="hint">
      选项来自行政区名称目录；可按名称/编码搜索。未收录编码仍可通过项目范围修订录入。
    </p>
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
