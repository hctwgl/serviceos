<script setup lang="ts">
import type { AdminWorkOrderWorkspaceView } from '@serviceos/api-client'
import { computed } from 'vue'
import { formatDateTime } from '../presenters/work-order'

/** 工单基本信息事实卡；只展示工作区视图已有字段，缺失值统一显示「未提供」。 */
const props = defineProps<{ view: AdminWorkOrderWorkspaceView }>()

const facts = computed(() => {
  const view = props.view
  const header = view.workspace.header
  const region = view.workspace.projectPersonnel.find(
    (person) => person.matchedRegionName ?? person.requestedRegionCode,
  )
  return [
    { label: '项目', value: view.projectName },
    { label: '客户', value: view.clientName ?? header.clientCode },
    { label: '服务产品', value: view.serviceName ?? header.serviceProductCode },
    { label: '客户品牌', value: header.brandCode },
    { label: '联系人', value: view.workspace.maskedCustomerName },
    { label: '联系电话', value: view.workspace.maskedCustomerPhone },
    { label: '服务地址', value: view.workspace.maskedServiceAddress },
    { label: '服务区域', value: region?.matchedRegionName ?? region?.requestedRegionCode ?? null },
    { label: '接收时间', value: formatDateTime(header.receivedAt) },
    { label: '配置版本', value: header.configurationBundleVersion },
    { label: '当前网点', value: header.currentNetworkDisplayName },
    { label: '当前师傅', value: header.currentTechnicianDisplayName },
  ]
})
</script>

<template>
  <dl class="overview-facts">
    <div
      v-for="fact in facts"
      :key="fact.label"
    >
      <dt>{{ fact.label }}</dt>
      <dd>{{ fact.value ?? '未提供' }}</dd>
    </div>
  </dl>
</template>

<style scoped>
.overview-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px 20px;
  margin: 0;
}

.overview-facts > div {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid var(--sos-border-soft);
  border-radius: 7px;
}

.overview-facts dt {
  color: var(--sos-text-muted);
  font-size: 11px;
}

.overview-facts dd {
  overflow: hidden;
  margin: 0;
  color: var(--sos-text-strong);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
