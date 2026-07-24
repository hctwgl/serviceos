<script setup lang="ts">
import { CheckOutlined } from '@serviceos/design-system'
import type { ProjectStageBarItem } from './types'

defineProps<{
  stages: ProjectStageBarItem[]
  title?: string
  description?: string
}>()
</script>

<template>
  <section class="sos-stage-bar">
    <header class="sos-panel-heading">
      <div>
        <span class="sos-eyebrow">履约进度</span>
        <h2>{{ title ?? '履约阶段进度' }}</h2>
        <p>{{ description ?? '当前进度来自项目履约与工单投影' }}</p>
      </div>
    </header>
    <div v-if="!stages.length" class="sos-inline-unavailable">
      <strong>尚未生成履约阶段</strong>
      <span>请先完成方案流程设计或等待工单运行数据。</span>
    </div>
    <ol v-else class="sos-stage-bar__list" aria-label="履约阶段">
      <li
        v-for="(stage, index) in stages"
        :key="stage.code"
        class="sos-stage-bar__item"
        :class="`sos-stage-bar__item--${stage.status}`"
      >
        <span class="sos-stage-bar__line sos-stage-bar__line--before" :class="{ 'is-hidden': index === 0 }" />
        <span class="sos-stage-bar__node">
          <CheckOutlined v-if="stage.status === 'completed'" />
          <span v-else>{{ index + 1 }}</span>
        </span>
        <span class="sos-stage-bar__line sos-stage-bar__line--after" :class="{ 'is-hidden': index === stages.length - 1 }" />
        <span class="sos-stage-bar__copy">
          <strong>{{ stage.label }}</strong>
          <small>{{ stage.detail ?? (stage.status === 'current' ? '当前进行中' : stage.status === 'completed' ? '已完成' : stage.status === 'blocked' ? '存在阻塞' : '未开始') }}</small>
        </span>
      </li>
    </ol>
  </section>
</template>
