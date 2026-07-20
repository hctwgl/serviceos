<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert, Button, Table } from 'ant-design-vue'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import DetailPageLayout from '../patterns/templates/DetailPageLayout.vue'
import { compileProjectFulfillmentPreview } from '../api/fulfillmentProfiles'
import { toUserFacingError } from '../product/errorMessages'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.id ?? ''))
const profileId = computed(() => String(route.params.profileId ?? ''))

const loading = ref(false)
const error = ref<string | null>(null)
const digest = ref<string | null>(null)
const rows = ref<
  Array<{
    stageName: string
    owner: string
    forms: string
    evidence: string
    actions: string
    nextStage: string
    exceptions: string
    sla: string
  }>
>([])

async function load() {
  loading.value = true
  error.value = null
  try {
    const manifest = (
      await compileProjectFulfillmentPreview(projectId.value, profileId.value)
    ).data
    digest.value = manifest.contentDigest.slice(0, 12) + '…'
    const parsed = JSON.parse(manifest.manifestJson) as {
      stages?: Array<Record<string, unknown>>
    }
    rows.value = (parsed.stages ?? []).map((stage) => {
      const actions = Array.isArray(stage.actions)
        ? stage.actions
            .map((a) => String((a as { actionLabel?: string }).actionLabel ?? ''))
            .filter(Boolean)
            .join('、')
        : '—'
      const forms = Array.isArray(stage.formRefs) ? `${stage.formRefs.length} 项` : '—'
      const evidence = Array.isArray(stage.evidenceRefs)
        ? `${stage.evidenceRefs.length} 项`
        : '—'
      const exceptions = Array.isArray(stage.exceptionPaths)
        ? `${stage.exceptionPaths.length} 条`
        : '—'
      const transitions = Array.isArray(stage.transitions) ? stage.transitions : []
      const next = transitions.length
        ? String((transitions[0] as { targetStage?: string }).targetStage ?? '—')
        : '—'
      const owner = stage.ownerType ? String(stage.ownerType) : '—'
      return {
        stageName: String(stage.stageName ?? stage.stageCode ?? '—'),
        owner,
        forms,
        evidence,
        actions: actions || '—',
        nextStage: next,
        exceptions,
        sla: stage.slaRef ? String(stage.slaRef) : '未绑定',
      }
    })
  } catch (err) {
    error.value = toUserFacingError(err).message
    rows.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <DetailPageLayout
    title="工单运行说明书"
    description="预览数据来自服务端 Compile Preview，前端不得自行拼装流程。"
    :loading="loading"
  >
    <template #secondary-actions>
      <Button
        @click="
          router.push({
            name: 'ADMIN.PROJECT.FULFILLMENT.DETAIL',
            params: { id: projectId, profileId },
          })
        "
      >
        <template #icon><ArrowLeftOutlined /></template>
        返回配置
      </Button>
    </template>
    <template #feedback>
      <Alert v-if="error" type="error" show-icon :message="error" style="margin-bottom: 12px" />
      <Alert
        v-else-if="digest"
        type="info"
        show-icon
        :message="`服务端 Manifest 摘要：${digest}`"
        style="margin-bottom: 12px"
      />
    </template>

    <Table
      size="middle"
      row-key="stageName"
      :pagination="false"
      :loading="loading"
      :data-source="rows"
      :columns="[
        { title: '阶段', dataIndex: 'stageName' },
        { title: '责任人', dataIndex: 'owner' },
        { title: '表单', dataIndex: 'forms' },
        { title: '必传资料', dataIndex: 'evidence' },
        { title: '可执行动作', dataIndex: 'actions' },
        { title: '正常下一阶段', dataIndex: 'nextStage' },
        { title: '异常路径', dataIndex: 'exceptions' },
        { title: 'SLA', dataIndex: 'sla' },
      ]"
    />
  </DetailPageLayout>
</template>
