<script setup lang="ts">
import { statusLabel } from '../product/labels'
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import {
  listNetworkPortalQualifications,
  type NetworkPortalQualificationItem,
} from '../api/networkPortal'

const props = defineProps<{ networkContextId: string | null }>()
const items = ref<NetworkPortalQualificationItem[]>([])
const error = ref<string | null>(null)

async function load() {
  if (!props.networkContextId) {
    items.value = []
    error.value = '请选择 NETWORK 上下文'
    return
  }
  try {
    const page = await listNetworkPortalQualifications(props.networkContextId)
    items.value = page.items
    error.value = null
  } catch (err) {
    items.value = []
    error.value = err instanceof Error ? err.message : '资质列表加载失败'
  }
}

onMounted(() => {
  void load()
})
watch(() => props.networkContextId, () => {
  void load()
})
</script>

<template>
  <section data-testid="network-portal-qualifications" data-page-id="NETWORK.QUALIFICATION">
    <h2>本网点资质</h2>
    <p class="hint">ACTIVE 师傅的资质记录；M220 展示 Accepted 字段；只读，不含裁决控件。</p>
    <p v-if="error" data-testid="network-portal-error">{{ error }}</p>
    <table v-else data-testid="network-qualifications-table">
      <thead>
        <tr>
          <th>资质</th>
          <th>师傅档案</th>
          <th>代码</th>
          <th>状态</th>
          <th>有效期</th>
          <th>提交人/时间</th>
          <th>裁决人/时间</th>
          <th>裁决原因</th>
          <th>版本</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id" :data-testid="`qualification-row-${item.id}`">
          <td>
            <RouterLink
              :to="`/network-portal/qualifications/${item.id}`"
              data-testid="qualification-case-deeplink"
            >
              {{ item.id }}
            </RouterLink>
          </td>
          <td>{{ item.technicianProfileId }}</td>
          <td>{{ item.qualificationCode }}</td>
          <td>{{ item.status ? statusLabel(item.status) : '—' }}</td>
          <td>{{ item.validFrom }} → {{ item.validTo ?? '—' }}</td>
          <td data-testid="qualification-submitted">
            {{ item.submittedBy }} / {{ item.submittedAt }}
          </td>
          <td data-testid="qualification-decided">
            {{ item.decidedBy ?? '—' }} / {{ item.decidedAt ?? '—' }}
          </td>
          <td data-testid="qualification-decision-reason">{{ item.decisionReason ?? '—' }}</td>
          <td data-testid="qualification-version">{{ item.version }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="!error && items.length === 0">暂无资质记录</p>
  </section>
</template>

<style scoped>
.hint {
  color: #5b6573;
  font-size: 0.9rem;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  text-align: left;
  padding: 0.4rem 0.5rem;
  border-bottom: 1px solid #e5e9ef;
  font-size: 0.9rem;
}
</style>
