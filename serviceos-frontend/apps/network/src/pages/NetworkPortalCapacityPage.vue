<script setup lang="ts">
import { statusLabel } from '../product/labels'
import { computed, onMounted, ref, watch } from 'vue'
import { formatDateTime, safeProblemMessage } from '../product/labels'
import {
  listNetworkPortalCapacity,
  type NetworkPortalCapacityItem,
} from '../api/networkPortal'
import SummaryStrip, { type SummaryStripItem } from '../components/SummaryStrip.vue'
import PageState from '../components/PageState.vue'

const props = defineProps<{ networkContextId: string | null }>()
const items = ref<NetworkPortalCapacityItem[]>([])
const asOf = ref<string | null>(null)
const error = ref<string | null>(null)
const loading = ref(false)

const summaryItems = computed<SummaryStripItem[]>(() => {
  const occupied = items.value.reduce((sum, row) => sum + Number(row.occupiedUnits || 0), 0)
  const max = items.value.reduce((sum, row) => sum + Number(row.maxUnits || 0), 0)
  const available = items.value.reduce((sum, row) => sum + Number(row.availableUnits || 0), 0)
  const saturated = items.value.filter((row) => Number(row.availableUnits || 0) <= 0).length
  return [
    {
      key: 'occupied',
      label: '当前在途占用',
      value: occupied,
      testId: 'capacity-summary-occupied',
    },
    {
      key: 'available',
      label: '可用余量',
      value: available,
      testId: 'capacity-summary-available',
      tone: available <= 0 && items.value.length > 0 ? 'warning' : 'default',
    },
    {
      key: 'max',
      label: '容量上限合计',
      value: max,
      testId: 'capacity-summary-max',
    },
    {
      key: 'saturated',
      label: '已满业务类型',
      value: saturated,
      testId: 'capacity-summary-saturated',
      tone: saturated > 0 ? 'critical' : 'default',
    },
  ]
})

function utilization(item: NetworkPortalCapacityItem) {
  if (!item.maxUnits) return 0
  return Math.min(100, Math.round((item.occupiedUnits / item.maxUnits) * 100))
}

async function load() {
  if (!props.networkContextId) {
    items.value = []
    asOf.value = null
    error.value = '请选择 NETWORK 上下文'
    loading.value = false
    return
  }
  loading.value = true
  try {
    const page = await listNetworkPortalCapacity(props.networkContextId)
    items.value = page.items
    asOf.value = page.asOf
    error.value = null
  } catch (err) {
    items.value = []
    asOf.value = null
    error.value = safeProblemMessage(err) || '产能列表加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
watch(
  () => props.networkContextId,
  () => {
    void load()
  },
)
</script>

<template>
  <section data-testid="network-portal-capacity" data-page-id="NETWORK.CAPACITY" class="capacity-page">
    <header class="hero">
      <div>
        <p class="eyebrow">产能状态</p>
        <h2>本网点产能</h2>
        <p class="hint">按业务类型展示在途占用与上限；网点仅可发起调整申请，不能直接改写容量桶。</p>
      </div>
      <button type="button" data-testid="capacity-reload" @click="load">刷新</button>
    </header>

    <p v-if="asOf" class="as-of" data-testid="capacity-as-of">
      统计时间：{{ formatDateTime(asOf) }}
      <span class="raw">asOf：{{ asOf }}</span>
    </p>

    <PageState v-if="loading && !items.length && !error" kind="loading" />
    <p v-else-if="error" data-testid="network-portal-error">{{ error }}</p>
    <template v-else>
      <SummaryStrip :items="summaryItems" />

      <div class="panel">
        <table data-testid="network-capacity-table">
          <thead>
            <tr>
              <th>业务类型</th>
              <th>占用 / 上限</th>
              <th>可用</th>
              <th>负载</th>
              <th>版本</th>
              <th>更新时间</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in items"
              :key="item.capacityCounterId"
              :data-testid="`capacity-row-${item.businessType}`"
            >
              <td>{{ item.businessType ? statusLabel(item.businessType) : '—' }}</td>
              <td>{{ item.occupiedUnits }} / {{ item.maxUnits }}</td>
              <td>{{ item.availableUnits }}</td>
              <td>
                <div class="meter" :aria-label="`负载 ${utilization(item)}%`">
                  <span :style="{ width: `${utilization(item)}%` }" />
                </div>
                <small>{{ utilization(item) }}%</small>
              </td>
              <td data-testid="capacity-version">{{ item.version }}</td>
              <td>{{ formatDateTime(item.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>
        <PageState
          v-if="!items.length"
          kind="empty"
          guide="暂无产能计数。完成网点容量配置后将在此显示。"
        />
        <p v-if="!items.length" data-testid="capacity-empty" class="sr-only">暂无产能计数</p>
      </div>

      <p class="muted gap-note">
        UI_DATA_GAP：产能调整申请（目标值/生效期/证明材料/审批状态）写模型尚未产品化交付；本页只读展示服务端计数，不提供直接改写。
      </p>
    </template>
  </section>
</template>

<style scoped>
.capacity-page {
  display: grid;
  gap: 14px;
}
.hero {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}
.eyebrow {
  margin: 0 0 4px;
  color: var(--sos-primary-600);
  font-size: 12px;
  letter-spacing: 0.08em;
}
.hero h2 {
  margin: 0 0 6px;
  font-size: 22px;
}
.hint,
.as-of,
.muted,
.gap-note {
  color: var(--sos-color-text-tertiary);
  font-size: 13px;
}
.as-of .raw {
  display: block;
  margin-top: 2px;
}
.panel {
  border: 1px solid var(--sos-color-border-default);
  border-radius: var(--sos-radius-md);
  background: var(--sos-color-surface-card);
  padding: 8px 12px 12px;
  overflow: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  border-bottom: 1px solid var(--sos-color-border-light);
  padding: 0.55rem 0.4rem;
  text-align: left;
  font-size: 13px;
  vertical-align: top;
}
.meter {
  width: 120px;
  height: 8px;
  border-radius: 999px;
  background: var(--sos-color-surface-subtle);
  border: 1px solid var(--sos-color-border-light);
  overflow: hidden;
}
.meter span {
  display: block;
  height: 100%;
  background: var(--sos-primary-600);
}
button {
  border: 1px solid var(--sos-color-border-default);
  background: #fff;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}
</style>
