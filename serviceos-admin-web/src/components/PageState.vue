<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

type Kind = 'loading' | 'empty' | 'error' | 'forbidden' | 'unauthorized' | 'notfound'

const props = withDefaults(
  defineProps<{
    kind: Kind
    title?: string
    description?: string
    guide?: string
    errorCode?: string
    failedAt?: string
    showRelogin?: boolean
    /** 卡片内嵌时隐藏「返回工作台」等全局动作 */
    compact?: boolean
  }>(),
  {
    title: undefined,
    description: undefined,
    guide: undefined,
    errorCode: undefined,
    failedAt: undefined,
    showRelogin: false,
    compact: false,
  },
)

const emit = defineEmits<{ reload: []; back: [] }>()
const router = useRouter()

const resolved = computed(() => {
  switch (props.kind) {
    case 'loading':
      return {
        title: props.title ?? '正在加载数据，请稍候……',
        description: props.description ?? '',
      }
    case 'empty':
      return {
        title: props.title ?? '暂无相关数据',
        description:
          props.description ??
          props.guide ??
          '当前没有可展示的记录，可以调整筛选条件或创建演示数据。',
      }
    case 'error':
      return {
        title: props.title ?? '数据加载失败',
        description: props.description ?? '请稍后重试，或返回上一页继续操作。',
      }
    case 'forbidden':
      return {
        title: props.title ?? '您当前没有访问该功能的权限',
        description: props.description ?? '如需开通，请联系平台管理员。',
      }
    case 'unauthorized':
      return {
        title: props.title ?? '登录状态已失效，请重新登录',
        description: props.description ?? '重新登录后可继续此前未完成的操作。',
      }
    case 'notfound':
      return {
        title: props.title ?? '页面不存在或功能已被调整',
        description: props.description ?? '请从工作台重新进入所需功能。',
      }
  }
})

function goWorkbench() {
  void router.push({ name: 'ADMIN.WORKBENCH' })
}

function goBack() {
  emit('back')
  if (window.history.length > 1) {
    router.back()
  } else {
    goWorkbench()
  }
}

function relogin() {
  void router.push('/settings/token')
}
</script>

<template>
  <section class="page-state" :data-testid="`page-state-${kind}`" :data-kind="kind">
    <h2>{{ resolved.title }}</h2>
    <p v-if="resolved.description" class="desc">{{ resolved.description }}</p>
    <p v-if="guide && kind === 'empty'" class="guide">{{ guide }}</p>
    <p v-if="errorCode" class="meta" data-testid="page-state-error-code">
      错误编号：{{ errorCode }}
    </p>
    <p v-if="failedAt" class="meta">失败时间：{{ failedAt }}</p>
    <div v-if="kind !== 'loading'" class="actions">
      <button
        v-if="kind === 'error'"
        type="button"
        data-testid="page-state-reload"
        @click="emit('reload')"
      >
        重新加载
      </button>
      <button
        v-if="!compact"
        type="button"
        data-testid="page-state-workbench"
        @click="goWorkbench"
      >
        返回工作台
      </button>
      <button
        v-if="!compact && (kind === 'error' || kind === 'notfound')"
        type="button"
        data-testid="page-state-back"
        @click="goBack"
      >
        返回上一页
      </button>
      <button
        v-if="kind === 'unauthorized' || showRelogin"
        type="button"
        data-testid="page-state-relogin"
        @click="relogin"
      >
        重新登录
      </button>
      <p v-if="kind === 'forbidden'" class="meta">联系管理员开通权限后重试。</p>
    </div>
  </section>
</template>

<style scoped>
.page-state {
  background: #fff;
  border-radius: 12px;
  padding: 1.5rem 1.25rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
  max-width: 40rem;
}
h2 {
  margin: 0 0 0.5rem;
  font-size: 1.15rem;
}
.desc,
.guide,
.meta {
  margin: 0.35rem 0;
  color: #486581;
  font-size: 0.92rem;
}
.guide {
  color: #243b53;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 1rem;
  align-items: center;
}
button {
  border: 1px solid #bcccdc;
  background: #f0f4f8;
  border-radius: 6px;
  padding: 0.45rem 0.85rem;
  cursor: pointer;
}
button:hover {
  background: #d9e2ec;
}
</style>
