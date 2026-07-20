<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert, Button } from 'ant-design-vue'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import DetailPageLayout from '../patterns/templates/DetailPageLayout.vue'
import FulfillmentRunbookTable from '../components/fulfillment/FulfillmentRunbookTable.vue'
import {
  compileProjectFulfillmentPreview,
  getProjectFulfillmentProfile,
  type ProjectFulfillmentManifest,
  type ProjectFulfillmentProfileDetail,
} from '../api/fulfillmentProfiles'
import { toUserFacingError } from '../product/errorMessages'
import { useDeveloperDiagnostics } from '../composables/useDeveloperDiagnostics'

const route = useRoute()
const router = useRouter()
const diagnostics = useDeveloperDiagnostics()
const projectId = computed(() => String(route.params.id ?? ''))
const profileId = computed(() => String(route.params.profileId ?? ''))

const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<ProjectFulfillmentProfileDetail | null>(null)
const manifest = ref<ProjectFulfillmentManifest | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    detail.value = (await getProjectFulfillmentProfile(projectId.value, profileId.value)).data
    manifest.value = (
      await compileProjectFulfillmentPreview(projectId.value, profileId.value)
    ).data
    if (manifest.value?.manifestJson) {
      diagnostics.pushDiagnostic({
        title: '履约 Manifest（仅诊断）',
        fields: {
          contentDigest: manifest.value.contentDigest,
          manifestJson: manifest.value.manifestJson.slice(0, 4000),
        },
      })
    }
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
    :title="detail?.profileName || '运行说明书预览'"
    description="服务端编译的业务运行说明。普通页面不展示 Manifest JSON。"
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
        返回详情
      </Button>
    </template>
    <template #feedback>
      <Alert v-if="error" type="error" show-icon :message="error" style="margin-bottom: 12px" />
      <Alert
        v-if="manifest?.runbook?.impactSummary"
        type="info"
        show-icon
        :message="manifest.runbook.impactSummary"
        style="margin-bottom: 12px"
      />
    </template>

    <FulfillmentRunbookTable :runbook="manifest?.runbook" :loading="loading" />
  </DetailPageLayout>
</template>
