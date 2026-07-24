<script setup lang="ts">
import StatusPill from '../StatusPill.vue'

withDefaults(defineProps<{
  projectName: string
  projectCode: string
  clientName: string
  regionLabel: string
  statusLabel: string
  statusTone: 'blue' | 'green' | 'orange' | 'red' | 'gray'
  fulfillmentName: string
  version?: string | null
}>(), {
  version: null,
})
</script>

<template>
  <header class="sos-project-summary-header">
    <div class="sos-project-summary-header__title">
      <div class="sos-project-summary-header__name">
        <h1>{{ projectName }}</h1>
        <StatusPill :tone="statusTone" :label="statusLabel" />
      </div>
      <p class="sos-project-summary-header__subline">
        <span>{{ clientName }}</span>
        <i aria-hidden="true" />
        <span>{{ regionLabel }}</span>
        <i aria-hidden="true" />
        <span>{{ projectCode }}</span>
      </p>
    </div>

    <div class="sos-project-summary-header__actions">
      <slot name="actions" />
    </div>

    <dl class="sos-project-summary-header__facts" aria-label="项目当前事实">
      <div>
        <dt>客户品牌</dt>
        <dd>{{ clientName }}</dd>
      </div>
      <div>
        <dt>服务区域</dt>
        <dd>{{ regionLabel }}</dd>
      </div>
      <div>
        <dt>当前履约方案</dt>
        <dd>{{ fulfillmentName }}</dd>
      </div>
      <div>
        <dt>当前版本</dt>
        <dd>{{ version ? `V${version}` : '尚未发布' }}</dd>
      </div>
    </dl>
  </header>
</template>
