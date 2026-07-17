<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { listRoles, type Role } from '../api/authorizationGovernance'
import {
  astToFilters,
  createSavedView,
  deleteSavedView,
  filtersToAst,
  listSavedViews,
  shareSavedView,
  visibilityLabel,
  type SavedView,
  type SavedViewPageId,
  type SavedViewVisibility,
} from '../api/savedViews'
import { defaultSavedViewIdForPage } from '../api/uiPreferences'
import { currentUiPreferences, loadAndApplyUiPreferences } from '../preferences/uiPreferenceState'

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
const roles = ref<Role[]>([])
const selectedId = ref('')
const saveName = ref('')
const shareMode = ref<'TENANT' | 'ROLE'>('TENANT')
const shareRoleId = ref('')
const busy = ref(false)
const message = ref<string | null>(null)
const autoAppliedDefault = ref(false)

const selected = computed(() => views.value.find((v) => v.id === selectedId.value) ?? null)
const selectedIsShared = computed(
  () => selected.value != null && selected.value.visibility !== 'PRIVATE',
)

function optionLabel(view: SavedView): string {
  const badge = view.visibility === 'PRIVATE' ? '私有' : visibilityLabel(view.visibility)
  const def = view.isDefault ? '（默认）' : ''
  return `${view.name} [${badge}]${def}`
}

async function refresh() {
  busy.value = true
  message.value = null
  try {
    const page = await listSavedViews(props.pageId)
    views.value = page.items
    if (selectedId.value && !views.value.some((v) => v.id === selectedId.value)) {
      selectedId.value = ''
    }
    await maybeApplyDefaultFromPreference()
  } catch (err) {
    message.value = err instanceof Error ? err.message : '加载保存视图失败'
  } finally {
    busy.value = false
  }
}

async function loadRolesForShare() {
  try {
    const page = await listRoles()
    roles.value = page.items.filter((r) => r.roleStatus === 'ACTIVE')
    if (!shareRoleId.value && roles.value.length > 0) {
      shareRoleId.value = roles.value[0].roleId
    }
  } catch {
    roles.value = []
  }
}

/** 可选：打开页面时按 UI Preference defaultSavedViews 自动应用默认视图一次。 */
async function maybeApplyDefaultFromPreference() {
  if (autoAppliedDefault.value) {
    return
  }
  let prefs = currentUiPreferences()
  if (!prefs) {
    prefs = await loadAndApplyUiPreferences()
  }
  const preferredId = defaultSavedViewIdForPage(prefs, props.pageId)
  const preferred =
    (preferredId ? views.value.find((v) => v.id === preferredId) : undefined) ??
    views.value.find((v) => v.isDefault)
  if (!preferred || preferred.schemaVersion !== props.schemaVersion) {
    return
  }
  selectedId.value = preferred.id
  emit('apply', astToFilters(preferred.filter))
  message.value = `已应用默认视图：${preferred.name}`
  autoAppliedDefault.value = true
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

async function shareSelected() {
  const view = selected.value
  if (!view) {
    return
  }
  const visibility: SavedViewVisibility = shareMode.value
  const sharedScopeRef = visibility === 'ROLE' ? shareRoleId.value || null : null
  if (visibility === 'ROLE' && !sharedScopeRef) {
    message.value = '请选择共享角色'
    return
  }
  busy.value = true
  message.value = null
  try {
    const shared = await shareSavedView(view.id, view.aggregateVersion, {
      visibility,
      sharedScopeRef,
    })
    selectedId.value = shared.data.id
    await refresh()
    message.value = `已共享：${visibilityLabel(shared.data.visibility)}`
  } catch (err) {
    message.value = err instanceof Error ? err.message : '共享视图失败'
  } finally {
    busy.value = false
  }
}

async function unshareSelected() {
  const view = selected.value
  if (!view || view.visibility === 'PRIVATE') {
    return
  }
  busy.value = true
  message.value = null
  try {
    const result = await shareSavedView(view.id, view.aggregateVersion, {
      visibility: 'PRIVATE',
      sharedScopeRef: null,
    })
    selectedId.value = result.data.id
    await refresh()
    message.value = '已取消共享'
  } catch (err) {
    message.value = err instanceof Error ? err.message : '取消共享失败'
  } finally {
    busy.value = false
  }
}

watch(
  () => props.pageId,
  () => {
    selectedId.value = ''
    autoAppliedDefault.value = false
    return refresh()
  },
)

onMounted(async () => {
  await Promise.all([refresh(), loadRolesForShare()])
})
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
          {{ optionLabel(view) }}
        </option>
      </select>
    </label>
    <span
      v-if="selected"
      class="badge"
      :data-visibility="selected.visibility"
      data-testid="saved-view-visibility-badge"
    >
      {{ visibilityLabel(selected.visibility) }}
    </span>
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

    <div class="share-row" data-testid="saved-view-share-panel">
      <label>
        共享范围
        <select
          v-model="shareMode"
          aria-label="saved view share mode"
          data-testid="saved-view-share-mode"
          :disabled="busy"
        >
          <option value="TENANT">租户</option>
          <option value="ROLE">角色</option>
        </select>
      </label>
      <label v-if="shareMode === 'ROLE'">
        角色
        <select
          v-model="shareRoleId"
          aria-label="saved view share role"
          data-testid="saved-view-share-role"
          :disabled="busy || roles.length === 0"
        >
          <option v-if="roles.length === 0" value="">（无可用角色）</option>
          <option v-for="role in roles" :key="role.roleId" :value="role.roleId">
            {{ role.roleName }} ({{ role.roleCode }})
          </option>
        </select>
      </label>
      <button
        type="button"
        data-testid="saved-view-share"
        :disabled="busy || !selectedId"
        @click="shareSelected"
      >
        共享
      </button>
      <button
        type="button"
        data-testid="saved-view-unshare"
        :disabled="busy || !selectedId || !selectedIsShared"
        @click="unshareSelected"
      >
        取消共享
      </button>
    </div>

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
.share-row {
  flex-basis: 100%;
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
  align-items: end;
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
.badge {
  align-self: center;
  font-size: 0.75rem;
  padding: 0.2rem 0.45rem;
  border-radius: 4px;
  background: #d9e2ec;
  color: #243b53;
}
.badge[data-visibility='TENANT'],
.badge[data-visibility='ROLE'] {
  background: #d9f0e6;
  color: #147d64;
}
.msg {
  flex-basis: 100%;
  margin: 0;
  font-size: 0.85rem;
  color: #334e68;
}
</style>
