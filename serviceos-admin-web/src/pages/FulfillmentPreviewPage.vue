<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert, Button } from 'ant-design-vue'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import DetailPageLayout from '../patterns/templates/DetailPageLayout.vue'
import FulfillmentRunbookTable from '../components/fulfillment/FulfillmentRunbookTable.vue'
import { compileProjectFulfillmentPreview } from '../api/fulfillmentProfiles'
import { toUserFacingError } from '../product/errorMessages'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.id ?? ''))
const profileId = computed(() => String(route.params.profileId ?? ''))

const loading = ref(false)
const error = ref<string | null>(null)
const manifestJson = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  manifestJson.value = null
  try {
    const manifest = (
      await compileProjectFulfillmentPreview(projectId.value, profileId.value)
    ).data
    manifestJson.value = manifest.manifestJson
  } catch (err) {
    error.value = toUserFacingError(err).message
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <DetailPageLayout
    title="工单运行说明书"
    description="按业务顺序预览每个阶段由谁处理、需要什么资料、可以执行哪些动作以及下一步去向。"
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
        v-else
        type="info"
        show-icon
        message="这是草稿运行预览"
        description="预览仅用于校验配置理解；只有发布并到达生效时间后，新工单才会使用该版本。"
        style="margin-bottom: 12px"
      />
    </template>

    <FulfillmentRunbookTable :manifest-json="manifestJson" :loading="loading" />
  </DetailPageLayout>
</template>
