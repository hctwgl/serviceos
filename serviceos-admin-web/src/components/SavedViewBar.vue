<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  astToFilters,
  createSavedView,
  deleteSavedView,
  filtersToAst,
  listSavedViews,
  type SavedView,
  type SavedViewPageId,
} from '../api/savedViews'

const props = defineProps<{
  pageId: SavedViewPageId
  schemaVersion: number
  /** 当前页面筛选快照，用于保存 */
  currentFilters: Record<string, string | undefined>
}>()

const emit = defineEmits<{
  apply: [filters: Record<string, string>]
}>()

const views = ref<SavedView[]>([])
const selectedId = ref('')
const saveName = ref('')
const busy = ref(false)
const message = ref<string | null>(null)

async function refresh() {
  busy.value = true
  message.value = null
  try {
    const page = await listSavedViews(props.pageId)
    views.value = page.items
    if (selectedId.value && !views.value.some((v) => v.id === selectedId.value)) {
      selectedId.value = ''
    }
  } catch (err) {
    message.value = err instanceof Error ? err.message : '加载保存视图失败'
  } finally {
    busy.value = false
  }
}

async function applySelected() {
  const view = views.value.find((v) => v.id === selectedId.value)
  if (!view) {
    return
  }
  if (view.schemaVersion !== props.schemaVersion) {
    message.value = '该视图字段目录已过期，请删除后重新保存'
    return
  }
  emit('apply', astToFilters(view.filter))
  message.value = `已应用：${view.name}`
}

async function saveCurrent() {
  const name = saveName.value.trim()
  if (!name) {
    message.value = '请输入视图名称'
    return
  }
  busy.value = true
  message.value = null
  try {
    const created = await createSavedView({
      pageId: props.pageId,
      name,
      schemaVersion: props.schemaVersion,
      filter: filtersToAst(props.currentFilters),
      isDefault: false,
    })
    saveName.value = ''
    selectedId.value = created.data.id
    await refresh()
    message.value = `已保存：${created.data.name}`
  } catch (err) {
    message.value = err instanceof Error ? err.message : '保存视图失败'
  } finally {
    busy.value = false
  }
}

async function removeSelected() {
  if (!selectedId.value) {
    return
  }
  busy.value = true
  message.value = null
  try {
    await deleteSavedView(selectedId.value)
    selectedId.value = ''
    await refresh()
    message.value = '已删除视图'
  } catch (err) {
    message.value = err instanceof Error ? err.message : '删除视图失败'
  } finally {
    busy.value = false
  }
}

watch(
  () => props.pageId,
  () => {
    selectedId.value = ''
    return refresh()
  },
)

onMounted(() => refresh())
</script>

<template>
  <div class="saved-view-bar" data-testid="saved-view-bar">
    <label>
      保存的视图
      <select
        v-model="selectedId"
        aria-label="saved view picker"
        data-testid="saved-view-picker"
        :disabled="busy"
      >
        <option value="">（未选择）</option>
        <option v-for="view in views" :key="view.id" :value="view.id">
          {{ view.name }}{{ view.isDefault ? '（默认）' : '' }}
        </option>
      </select>
    </label>
    <button
      type="button"
      data-testid="saved-view-apply"
      :disabled="busy || !selectedId"
      @click="applySelected"
    >
      应用
    </button>
    <button
      type="button"
      data-testid="saved-view-delete"
      :disabled="busy || !selectedId"
      @click="removeSelected"
    >
      删除
    </button>
    <label class="save-name">
      新视图名称
      <input
        v-model="saveName"
        aria-label="saved view name"
        data-testid="saved-view-name"
        placeholder="例如：READY 任务"
        :disabled="busy"
      />
    </label>
    <button
      type="button"
      data-testid="saved-view-save"
      :disabled="busy"
      @click="saveCurrent"
    >
      保存当前筛选
    </button>
    <p v-if="message" class="msg" data-testid="saved-view-message">{{ message }}</p>
  </div>
</template>

<style scoped>
.saved-view-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
  align-items: end;
  margin-bottom: 0.85rem;
  padding: 0.65rem 0.75rem;
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  background: #f0f4f8;
}
label {
  display: grid;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: #486581;
}
.save-name {
  min-width: 12rem;
}
select,
input,
button {
  border: 1px solid #bcccdc;
  border-radius: 6px;
  padding: 0.4rem 0.65rem;
}
button {
  background: #243b53;
  color: #fff;
  border-color: #243b53;
  cursor: pointer;
}
button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.msg {
  flex-basis: 100%;
  margin: 0;
  font-size: 0.85rem;
  color: #334e68;
}
</style>
