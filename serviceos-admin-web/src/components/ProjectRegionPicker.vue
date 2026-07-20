<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Select } from 'ant-design-vue'
import { getProjectReferenceOptions, type ProjectRegionOption } from '../api/projectReferenceOptions'
import { safeAccessDeniedMessage } from '../api/client'

const props = defineProps<{
  modelValue: string[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

const loading = ref(false)
const error = ref<string | null>(null)
const regions = ref<ProjectRegionOption[]>([])

const options = computed(() =>
  regions.value.map((item) => ({
    value: item.regionCode,
    label: `${item.regionCode} · ${item.projectCount} 个项目使用`,
  })),
)

async function load() {
  loading.value = true
  error.value = null
  try {
    const page = await getProjectReferenceOptions()
    regions.value = page.regions
  } catch (err) {
    regions.value = []
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
      placeholder="选择或输入区域编码"
      aria-label="服务区域编码"
      :options="options"
      :filter-option="(input, option) => String(option?.label ?? '').includes(input)"
      @update:value="(value) => emit('update:modelValue', (value as string[]) ?? [])"
    />
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else class="hint">
      选项来自已授权项目生效 REGION；完整行政区名称树仍属 UI_DATA_GAP，可继续手工输入编码。
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
