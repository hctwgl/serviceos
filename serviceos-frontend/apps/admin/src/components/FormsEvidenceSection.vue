<script setup lang="ts">
import type {
  WorkOrderWorkspaceFormsEvidenceSectionData,
} from '@serviceos/api-client'
import { authorizeEvidenceRevisionDownload } from '@serviceos/api-client'
import { useMutation } from '@tanstack/vue-query'
import { Empty, message } from '@serviceos/design-system'
import { computed } from 'vue'
import { formatDateTime } from '../presenters/work-order'
import EvidenceGallery from './serviceos/EvidenceGallery.vue'
import StatusPill from './StatusPill.vue'

/** 工单工作区「表单资料」区块：表单提交列表 + 资料槽位分组 + 受控预览。 */
const props = defineProps<{ data: WorkOrderWorkspaceFormsEvidenceSectionData | null }>()

const submissions = computed(() => props.data?.formSubmissions ?? [])
const slots = computed(() => props.data?.evidenceSlots ?? [])
const items = computed(() => props.data?.evidenceItems ?? [])

const isEmpty = computed(
  () => !submissions.value.length && !slots.value.length && !items.value.length,
)

/**
 * 表单字段值平铺。当前工作区契约的 FormSubmissionSummary 不返回 values/submittedBy，
 * 这里对 values 做防御性处理：后端将来补充对象字段时按键值对平铺展示，中文键名原样保留。
 */
function flattenValues(record: Record<string, unknown>): Array<{ key: string; value: string }> {
  return Object.entries(record).map(([key, value]) => ({
    key,
    value:
      value === null || value === undefined
        ? '—'
        : typeof value === 'object'
          ? JSON.stringify(value)
          : String(value),
  }))
}

function submissionValues(submission: Record<string, unknown>) {
  const values = submission.values
  return values && typeof values === 'object' && !Array.isArray(values)
    ? flattenValues(values as Record<string, unknown>)
    : []
}

// 预览走受控授权：申请短时下载授权后新窗口打开，不内嵌永久 URL。
const preview = useMutation({
  mutationFn: (revisionId: string) => authorizeEvidenceRevisionDownload(revisionId),
  onSuccess: (authorization) => {
    // eslint 环境未声明浏览器全局对象，经 globalThis 打开授权后的短时下载地址。
    globalThis.window.open(authorization.downloadUrl, '_blank', 'noopener')
  },
  onError: (error) =>
    message.error(error instanceof Error ? error.message : '资料预览授权失败，请稍后重试'),
})

const previewingRevisionId = computed(() => (
  preview.isPending.value ? (preview.variables.value as string | undefined) ?? null : null
))
</script>

<template>
  <div class="forms-evidence-section">
    <Empty
      v-if="isEmpty"
      description="当前工单还没有表单提交与资料记录"
    />
    <template v-else>
      <section class="fe-block">
        <h3>表单提交</h3>
        <Empty
          v-if="!submissions.length"
          description="暂无表单提交"
        />
        <article
          v-for="submission in submissions"
          :key="submission.submissionId"
          class="fe-card"
        >
          <header>
            <strong>{{ submission.formKey }}</strong>
            <StatusPill
              :tone="submission.validationStatus === 'VALIDATED' ? 'green' : 'red'"
              :label="submission.validationStatus === 'VALIDATED' ? '校验通过' : '校验未通过'"
            />
          </header>
          <dl class="fe-facts">
            <div>
              <dt>提交时间</dt>
              <dd>{{ formatDateTime(submission.submittedAt) }}</dd>
            </div>
            <div>
              <dt>提交版本</dt>
              <dd>第 {{ submission.submissionVersion }} 版</dd>
            </div>
            <div>
              <dt>校验结果</dt>
              <dd>{{ submission.errorCount }} 个错误 / {{ submission.warningCount }} 个提醒</dd>
            </div>
          </dl>
          <template v-if="submissionValues(submission as unknown as Record<string, unknown>).length">
            <dl class="fe-values">
              <div
                v-for="field in submissionValues(submission as unknown as Record<string, unknown>)"
                :key="field.key"
              >
                <dt>{{ field.key }}</dt>
                <dd>{{ field.value }}</dd>
              </div>
            </dl>
          </template>
        </article>
      </section>

      <EvidenceGallery
        :slots="slots"
        :items="items"
        :previewing-revision-id="previewingRevisionId"
        @preview="preview.mutate"
      />
    </template>
  </div>
</template>

<style scoped>
.forms-evidence-section {
  display: grid;
  gap: 16px;
}

.fe-block > h3 {
  margin: 0 0 10px;
  color: var(--sos-text-strong);
  font-size: 14px;
}

.fe-block {
  display: grid;
  gap: 10px;
  align-content: start;
}

.fe-card {
  display: grid;
  gap: 8px;
  padding: 13px 15px;
  border: 1px solid var(--sos-border-soft);
  border-radius: 7px;
}

.fe-card > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.fe-card > header strong {
  color: var(--sos-text-strong);
  font-size: 13px;
}

.fe-tags {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.fe-required {
  color: var(--sos-danger-text);
  font-size: 11px;
  font-style: normal;
  font-weight: 600;
}

.fe-media {
  color: var(--sos-text-secondary);
  font-size: 11px;
}

.fe-facts,
.fe-values {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 18px;
  margin: 0;
}

.fe-values {
  padding-top: 8px;
  border-top: 1px dashed var(--sos-border-soft);
}

.fe-facts dt,
.fe-values dt {
  color: var(--sos-text-muted);
  font-size: 11px;
}

.fe-facts dd,
.fe-values dd {
  margin: 3px 0 0;
  color: var(--sos-text-strong);
  font-size: 12px;
  word-break: break-all;
}

.fe-slot-meta {
  margin: 0;
  color: var(--sos-text-muted);
  font-size: 11px;
}

.fe-item-list {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 8px 0 0;
  border-top: 1px dashed var(--sos-border-soft);
  list-style: none;
}

.fe-item-list > li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.fe-item-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.fe-item-main strong {
  color: var(--sos-text-strong);
  font-size: 12px;
}

.fe-item-meta {
  color: var(--sos-text-muted);
  font-size: 11px;
}

.fe-slot-empty {
  margin: 0;
  padding-top: 6px;
  border-top: 1px dashed var(--sos-border-soft);
  color: var(--sos-text-muted);
  font-size: 12px;
}
</style>
