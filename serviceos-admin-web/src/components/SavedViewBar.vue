<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Button, Select, Space, Modal, Input, Radio, Tag } from 'ant-design-vue'
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
const saveOpen = ref(false)
const shareOpen = ref(false)

const selected = computed(() => views.value.find((v) => v.id === selectedId.value) ?? null)
const selectedIsShared = computed(
  () => selected.value != null && selected.value.visibility !== 'PRIVATE',
)

const pickerOptions = computed(() =>
  views.value.map((view) => ({
    value: view.id,
    label: optionLabel(view),
  })),
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
    saveOpen.value = false
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
    shareOpen.value = false
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
  if (!view) {
    return
  }
  busy.value = true
  message.value = null
  try {
    const shared = await shareSavedView(view.id, view.aggregateVersion, {
      visibility: 'PRIVATE',
      sharedScopeRef: null,
    })
    selectedId.value = shared.data.id
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
    autoAppliedDefault.value = false
    selectedId.value = ''
    void refresh()
  },
)

onMounted(async () => {
  await Promise.all([refresh(), loadRolesForShare()])
})
</script>

<template>
  <div class="saved-view-bar" data-testid="saved-view-bar">
    <Space wrap>
      <span class="label">视图</span>
      <Select
        v-model:value="selectedId"
        allow-clear
        show-search
        style="min-width: 220px"
        placeholder="选择已保存视图"
        aria-label="saved view picker"
        data-testid="saved-view-picker"
        :disabled="busy"
        :options="pickerOptions"
        :filter-option="
          (input, option) =>
            String(option?.label ?? '')
              .toLowerCase()
              .includes(input.toLowerCase())
        "
      />
      <Tag
        v-if="selected"
        :data-visibility="selected.visibility"
        data-testid="saved-view-visibility-badge"
      >
        {{ visibilityLabel(selected.visibility) }}
      </Tag>
      <Button
        data-testid="saved-view-apply"
        :disabled="busy || !selectedId"
        @click="applySelected"
      >
        应用
      </Button>
      <Button data-testid="saved-view-save" :disabled="busy" @click="saveOpen = true">
        保存当前
      </Button>
      <Button
        data-testid="saved-view-share"
        :disabled="busy || !selectedId"
        @click="shareOpen = true"
      >
        分享
      </Button>
      <Button
        danger
        data-testid="saved-view-delete"
        :disabled="busy || !selectedId"
        @click="removeSelected"
      >
        删除
      </Button>
      <Button
        type="link"
        data-testid="saved-view-unshare"
        :disabled="busy || !selectedId || !selectedIsShared"
        @click="unshareSelected"
      >
        取消共享
      </Button>
    </Space>
    <p v-if="message" class="msg" data-testid="saved-view-message">{{ message }}</p>

    <Modal
      v-model:open="saveOpen"
      title="保存当前筛选为视图"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="busy"
      data-testid="saved-view-save-modal"
      @ok="saveCurrent"
    >
      <label class="field">
        视图名称
        <Input
          v-model:value="saveName"
          aria-label="saved view name"
          data-testid="saved-view-name"
          placeholder="例如：待处理安装工单"
          :disabled="busy"
        />
      </label>
    </Modal>

    <Modal
      v-model:open="shareOpen"
      title="分享视图"
      ok-text="确认分享"
      cancel-text="取消"
      :confirm-loading="busy"
      data-testid="saved-view-share-modal"
      @ok="shareSelected"
    >
      <div class="share-panel" data-testid="saved-view-share-panel">
        <div class="field">
          <span>共享范围</span>
          <Radio.Group
            v-model:value="shareMode"
            aria-label="saved view share mode"
            data-testid="saved-view-share-mode"
            :disabled="busy"
          >
            <Radio value="TENANT">租户</Radio>
            <Radio value="ROLE">角色</Radio>
          </Radio.Group>
        </div>
        <label v-if="shareMode === 'ROLE'" class="field">
          角色
          <Select
            v-model:value="shareRoleId"
            aria-label="saved view share role"
            data-testid="saved-view-share-role"
            style="width: 100%"
            :disabled="busy || roles.length === 0"
            :options="
              roles.map((role) => ({
                value: role.roleId,
                label: `${role.roleName} (${role.roleCode})`,
              }))
            "
          />
        </label>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.saved-view-bar {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.label {
  font-size: 13px;
  color: var(--sos-color-text-secondary, #4b5563);
}
.msg {
  margin: 0;
  font-size: 12px;
  color: var(--sos-color-text-secondary, #4b5563);
}
.field {
  display: grid;
  gap: 8px;
  margin-bottom: 12px;
}
.share-panel {
  display: grid;
  gap: 8px;
}
</style>
