<script setup lang="ts">
import type {
  WorkOrderWorkspaceEvidenceItemSummary,
  WorkOrderWorkspaceEvidenceSlotSummary,
} from '@serviceos/api-client'
import { Button, FileDoneOutlined, Tag } from '@serviceos/design-system'
import {
  evidenceItemStatusLabel,
  evidenceMediaTypeLabel,
  evidenceRevisionStatusLabel,
} from '../../presenters/work-order'

defineProps<{
  slots: WorkOrderWorkspaceEvidenceSlotSummary[]
  items: WorkOrderWorkspaceEvidenceItemSummary[]
  previewingRevisionId?: string | null
}>()

const emit = defineEmits<{
  preview: [revisionId: string]
}>()

function itemsForSlot(items: WorkOrderWorkspaceEvidenceItemSummary[], slotId: string) {
  return items.filter((item) => item.evidenceSlotId === slotId)
}

function tone(status: string) {
  if (status === 'ACCEPTED' || status === 'VALIDATED') return 'success'
  if (status === 'REJECTED' || status === 'VALIDATION_FAILED') return 'error'
  if (status === 'UNDER_REVIEW' || status === 'VALIDATING') return 'processing'
  return 'default'
}
</script>

<template>
  <section class="sos-evidence-gallery">
    <header class="sos-section-heading">
      <div><span class="sos-eyebrow">EVIDENCE CONTROL</span><h3>资料要求与收集进度</h3></div>
      <span>按资料槽位展示 · 不暴露文件对象标识</span>
    </header>
    <div v-if="!slots.length" class="sos-inline-empty"><strong>暂无资料要求</strong><span>当前履约阶段没有可展示的资料槽位。</span></div>
    <div v-else class="sos-evidence-grid">
      <article v-for="slot in slots" :key="slot.slotId" class="sos-evidence-slot">
        <header>
          <div class="sos-evidence-slot__name"><FileDoneOutlined /><strong>{{ slot.requirementName }}</strong></div>
          <Tag v-if="slot.required" color="warning">必传</Tag>
        </header>
        <p>{{ evidenceMediaTypeLabel(slot.mediaType) }} · {{ slot.minCount }}～{{ slot.maxCount ?? '不限' }} 份</p>
        <div class="sos-evidence-slot__status">
          <Tag :color="tone(slot.status)">{{ evidenceItemStatusLabel(slot.status) }}</Tag>
          <span>{{ itemsForSlot(items, slot.slotId).length }} 项已建档</span>
        </div>
        <ul v-if="itemsForSlot(items, slot.slotId).length" class="sos-evidence-items">
          <li v-for="item in itemsForSlot(items, slot.slotId)" :key="item.evidenceItemId">
            <div><strong>资料 {{ item.itemOrdinal }}</strong><small>{{ item.latestRevisionNumber ? `第 ${item.latestRevisionNumber} 版` : '暂无版本' }}<template v-if="item.latestRevisionStatus"> · {{ evidenceRevisionStatusLabel(item.latestRevisionStatus) }}</template></small></div>
            <Button
              v-if="item.latestRevisionId"
              size="small"
              :loading="previewingRevisionId === item.latestRevisionId"
              @click="emit('preview', item.latestRevisionId)"
            >
              预览
            </Button>
          </li>
        </ul>
        <span v-else class="sos-evidence-slot__empty">尚未提交资料</span>
      </article>
    </div>
  </section>
</template>
