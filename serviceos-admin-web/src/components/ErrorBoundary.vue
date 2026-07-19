<script setup lang="ts">
import { onErrorCaptured, ref } from 'vue'
import { useRouter } from 'vue-router'
import { toUserFacingError } from '../product/errorMessages'
import { formatDateTime } from '../product/formatTime'

const props = withDefaults(
  defineProps<{
    /** 子树标识，写入错误编号上下文 */
    scope?: string
  }>(),
  { scope: 'page' },
)

const failed = ref<{
  message: string
  errorCode: string
  detailDev?: string
  failedAt: string
} | null>(null)

const router = useRouter()

onErrorCaptured((err) => {
  const facing = toUserFacingError(err)
  failed.value = {
    message: facing.message,
    errorCode: `${facing.errorCode}-${props.scope}`,
    detailDev: facing.detailDev,
    failedAt: formatDateTime(new Date()),
  }
  console.error(`[ErrorBoundary:${props.scope}]`, err)
  return false
})

function reload() {
  failed.value = null
  window.location.reload()
}

function goWorkbench() {
  failed.value = null
  void router.push({ name: 'ADMIN.WORKBENCH' })
}

function goBack() {
  failed.value = null
  if (window.history.length > 1) {
    router.back()
  } else {
    goWorkbench()
  }
}

defineExpose({ reset: () => { failed.value = null } })
</script>

<template>
  <section v-if="failed" class="boundary-fallback" data-testid="error-boundary">
    <h2>页面出现异常</h2>
    <p class="desc">{{ failed.message }}</p>
    <p class="meta" data-testid="error-boundary-code">错误编号：{{ failed.errorCode }}</p>
    <p class="meta">失败时间：{{ failed.failedAt }}</p>
    <pre v-if="failed.detailDev" class="dev" data-testid="error-boundary-dev">{{ failed.detailDev }}</pre>
    <div class="actions">
      <button type="button" data-testid="error-boundary-reload" @click="reload">重新加载</button>
      <button type="button" data-testid="error-boundary-workbench" @click="goWorkbench">返回工作台</button>
      <button type="button" data-testid="error-boundary-back" @click="goBack">返回上一页</button>
    </div>
  </section>
  <slot v-else />
</template>

<style scoped>
.boundary-fallback {
  background: #fff;
  border: 1px solid #f5c1c1;
  border-radius: 12px;
  padding: 1.5rem;
  max-width: 40rem;
}
h2 {
  margin: 0 0 0.5rem;
  color: #9b1c1c;
}
.desc {
  color: #243b53;
}
.meta {
  color: #627d98;
  font-size: 0.9rem;
}
.dev {
  margin-top: 0.75rem;
  padding: 0.75rem;
  background: #f0f4f8;
  border-radius: 8px;
  font-size: 0.8rem;
  overflow: auto;
  white-space: pre-wrap;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 1rem;
}
button {
  border: 1px solid #bcccdc;
  background: #f0f4f8;
  border-radius: 6px;
  padding: 0.45rem 0.85rem;
  cursor: pointer;
}
</style>
