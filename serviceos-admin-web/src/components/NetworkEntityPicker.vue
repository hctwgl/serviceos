<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Select } from 'ant-design-vue'
import { listServiceNetworks, type ServiceNetwork } from '../api/networks'
import { statusLabel } from '../product/statusLabels'
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
const networks = ref<ServiceNetwork[]>([])

const options = computed(() =>
  networks.value
    .filter((item) => item.status === 'ACTIVE')
    .map((item) => ({
      value: item.id,
      label: `${item.networkName}（${item.networkCode}）· ${statusLabel(item.status)}`,
    })),
)

async function load() {
  loading.value = true
  error.value = null
  try {
    const page = await listServiceNetworks()
    networks.value = page.items
  } catch (err) {
    networks.value = []
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
  <div class="picker" data-testid="network-entity-picker">
    <Select
      :value="modelValue"
      mode="multiple"
      show-search
      allow-clear
      style="width: 100%"
      :disabled="disabled"
      :loading="loading"
      placeholder="搜索并选择合作网点"
      aria-label="合作网点"
      :options="options"
      :filter-option="(input, option) => String(option?.label ?? '').includes(input)"
      @update:value="(value) => emit('update:modelValue', (value as string[]) ?? [])"
    />
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else class="hint">已选 {{ modelValue.length }} 个网点 · 来自服务网点目录</p>
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
