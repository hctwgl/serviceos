<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  deleteUiPreference,
  putUiPreferences,
  type DensityPreference,
  type ThemePreference,
  type UiPreferencesDocument,
} from '../api/uiPreferences'
import { loadAndApplyUiPreferences, rememberUiPreferences } from '../preferences/uiPreferenceState'

const theme = ref<ThemePreference>('SYSTEM')
const density = ref<DensityPreference>('COMFORTABLE')
const reduceMotion = ref(false)
const locale = ref('zh-CN')
const busy = ref(false)
const message = ref<string | null>(null)
const documentState = ref<UiPreferencesDocument | null>(null)

function hydrate(doc: UiPreferencesDocument | null) {
  documentState.value = doc
  theme.value = (doc?.preferences.theme?.value as ThemePreference | undefined) ?? 'SYSTEM'
  density.value = (doc?.preferences.density?.value as DensityPreference | undefined) ?? 'COMFORTABLE'
  reduceMotion.value = Boolean(doc?.preferences.reduceMotion?.value)
  locale.value = (doc?.preferences.locale?.value as string | undefined) ?? 'zh-CN'
}

onMounted(async () => {
  hydrate(await loadAndApplyUiPreferences())
})

async function save() {
  busy.value = true
  message.value = null
  try {
    const prefs = documentState.value?.preferences
    const result = await putUiPreferences({
      theme: {
        value: theme.value,
        schemaVersion: 1,
        expectedVersion: prefs?.theme?.aggregateVersion,
      },
      density: {
        value: density.value,
        schemaVersion: 1,
        expectedVersion: prefs?.density?.aggregateVersion,
      },
      reduceMotion: {
        value: reduceMotion.value,
        schemaVersion: 1,
        expectedVersion: prefs?.reduceMotion?.aggregateVersion,
      },
      locale: {
        value: locale.value,
        schemaVersion: 1,
        expectedVersion: prefs?.locale?.aggregateVersion,
      },
    })
    rememberUiPreferences(result.data)
    hydrate(result.data)
    message.value = '偏好已保存'
  } catch (err) {
    message.value = err instanceof Error ? err.message : '保存偏好失败'
  } finally {
    busy.value = false
  }
}

async function restoreTheme() {
  busy.value = true
  message.value = null
  try {
    await deleteUiPreference('theme')
    const doc = await loadAndApplyUiPreferences()
    hydrate(doc)
    message.value = '主题已恢复默认'
  } catch (err) {
    message.value = err instanceof Error ? err.message : '恢复默认失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="page" data-testid="ui-preferences-page">
    <header>
      <h2>界面偏好</h2>
      <p>个人 Admin 展示偏好；不改变授权或安全确认。</p>
    </header>

    <form class="form" @submit.prevent="save">
      <label>
        主题
        <select v-model="theme" data-testid="pref-theme" :disabled="busy">
          <option value="SYSTEM">跟随系统</option>
          <option value="LIGHT">浅色</option>
          <option value="DARK">深色</option>
        </select>
      </label>
      <label>
        密度
        <select v-model="density" data-testid="pref-density" :disabled="busy">
          <option value="COMFORTABLE">舒适</option>
          <option value="COMPACT">紧凑</option>
        </select>
      </label>
      <label>
        语言（locale）
        <select v-model="locale" data-testid="pref-locale" :disabled="busy">
          <option value="zh-CN">zh-CN</option>
          <option value="en">en</option>
        </select>
      </label>
      <label class="check">
        <input v-model="reduceMotion" type="checkbox" data-testid="pref-reduce-motion" :disabled="busy" />
        减少动画
      </label>
      <div class="actions">
        <button type="submit" data-testid="pref-save" :disabled="busy">保存</button>
        <button type="button" data-testid="pref-restore-theme" :disabled="busy" @click="restoreTheme">
          恢复主题默认
        </button>
      </div>
      <p v-if="message" class="msg" data-testid="pref-message">{{ message }}</p>
    </form>
  </section>
</template>

<style scoped>
.page {
  max-width: 40rem;
}
header h2 {
  margin: 0 0 0.35rem;
}
header p {
  margin: 0 0 1.25rem;
  color: #486581;
}
.form {
  display: grid;
  gap: 0.85rem;
  padding: 1rem;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #fff;
}
label {
  display: grid;
  gap: 0.35rem;
  font-size: 0.9rem;
  color: #334e68;
}
.check {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
select,
button {
  border: 1px solid #bcccdc;
  border-radius: 6px;
  padding: 0.45rem 0.7rem;
}
.actions {
  display: flex;
  gap: 0.65rem;
}
button[type='submit'] {
  background: #243b53;
  color: #fff;
  border-color: #243b53;
}
.msg {
  margin: 0;
  color: #243b53;
}
</style>
