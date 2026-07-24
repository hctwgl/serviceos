<script setup lang="ts">
export type BlueprintSidebarPlan = {
  id: string
  name: string
  productLabel: string
  status: string
  statusClass: string
  version: string | null
  stageCount: number | null
}

export type BlueprintSidebarSection = {
  key: string
  label: string
  detail: string
}

defineProps<{
  plans: BlueprintSidebarPlan[]
  sections: BlueprintSidebarSection[]
  selectedPlanId?: string
  activeSection: string
}>()

const emit = defineEmits<{
  selectPlan: [id: string]
  selectSection: [key: string]
}>()
</script>

<template>
  <aside class="sos-blueprint-sidebar">
    <div class="sos-blueprint-sidebar__heading">
      <span class="sos-eyebrow">方案设计</span>
      <strong>履约方案</strong>
      <small>{{ plans.length }} 套方案</small>
    </div>

    <div v-if="plans.length" class="sos-blueprint-sidebar__plans">
      <button
        v-for="plan in plans"
        :key="plan.id"
        type="button"
        :class="{ active: plan.id === selectedPlanId }"
        @click="emit('selectPlan', plan.id)"
      >
        <span class="sos-blueprint-sidebar__plan-title">
          <strong>{{ plan.name }}</strong>
          <i :class="`is-${plan.statusClass}`" aria-hidden="true" />
        </span>
        <small>{{ plan.productLabel }}</small>
        <span>{{ plan.version ? `V${plan.version}` : '尚未发布' }} · {{ plan.stageCount ?? 0 }} 个阶段</span>
      </button>
    </div>
    <div v-else class="sos-blueprint-sidebar__empty">
      <strong>尚未建立方案</strong>
      <span>先创建一个服务场景，再配置履约蓝图。</span>
    </div>

    <nav class="sos-blueprint-sidebar__nav" aria-label="蓝图设计导航">
      <span class="sos-blueprint-sidebar__label">设计目录</span>
      <button
        v-for="section in sections"
        :key="section.key"
        type="button"
        :class="{ active: activeSection === section.key }"
        @click="emit('selectSection', section.key)"
      >
        <span>{{ section.label }}</span>
        <small>{{ section.detail }}</small>
      </button>
    </nav>

    <footer class="sos-blueprint-sidebar__footer">
      <slot name="footer" />
    </footer>
  </aside>
</template>
