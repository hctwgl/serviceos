<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Select } from 'ant-design-vue'
import { getProjectReferenceOptions, type ProjectClientOption } from '../api/projectReferenceOptions'
import { labelClientCode } from '../presentation/enum-labels'
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
const clients = ref<ProjectClientOption[]>([])

const options = computed(() =>
  clients.value.map((item) => ({
    value: item.clientId,
    label: `${labelClientCode(item.clientId)}（${item.clientId}）· ${item.projectCount} 个项目`,
  })),
)

async function load() {
  loading.value = true
  error.value = null
  try {
    const page = await getProjectReferenceOptions()
    clients.value = page.clients
  } catch (err) {
    clients.value = []
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
    <p v-else-if="!loading && clients.length === 0" class="hint">
      当前授权范围内尚无可选车企；请先用已有项目建立车企事实，或联系管理员开通目录。
    </p>
    <p v-else class="hint">
      选项来自已授权项目中的车企聚合；完整车企主数据目录尚未单独交付。
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
