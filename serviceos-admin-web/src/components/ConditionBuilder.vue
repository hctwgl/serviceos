<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  compileCondition,
  emptyGroup,
  tryParseCondition,
  type ConditionGroup,
} from '../expression/serviceosExprV1Blocks'
import ConditionGroupBlock from './ConditionGroupBlock.vue'

const props = defineProps<{
  modelValue: string
  label?: string
  /** 当前 FORM 资产内已声明字段键；用于生成 formValues["…"] 积木选项 */
  formFieldKeys?: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const root = ref<ConditionGroup>(emptyGroup())
const compileError = ref<string | null>(null)
const advancedMode = ref(false)
const advancedDraft = ref(props.modelValue)

function syncFromSource(source: string) {
  const parsed = tryParseCondition(source)
  if (parsed) {
    root.value = parsed
    advancedMode.value = false
    compileError.value = null
  } else if (source.trim()) {
    advancedMode.value = true
    advancedDraft.value = source
  } else {
    root.value = emptyGroup()
    advancedMode.value = false
  }
}

watch(
  () => props.modelValue,
  (value) => {
    if (advancedMode.value && value === advancedDraft.value) {
      return
    }
    const current = advancedMode.value ? advancedDraft.value : safeCompile()
    if (value !== current) {
      syncFromSource(value)
    }
  },
  { immediate: true },
)

function safeCompile(): string {
  try {
    return compileCondition(root.value)
  } catch {
    return ''
  }
}

function emitCompiled() {
  try {
    const source = compileCondition(root.value)
    compileError.value = null
    emit('update:modelValue', source)
  } catch (err) {
    compileError.value = err instanceof Error ? err.message : '编译失败'
  }
}

function onRootChange(group: ConditionGroup) {
  root.value = group
  emitCompiled()
}

function applyAdvanced() {
  emit('update:modelValue', advancedDraft.value)
  syncFromSource(advancedDraft.value)
}

const preview = computed(() => {
  if (advancedMode.value) {
    return advancedDraft.value
  }
  try {
    return compileCondition(root.value)
  } catch (err) {
    return err instanceof Error ? err.message : ''
  }
})
</script>

<template>
  <section class="condition-builder" data-testid="condition-builder">
    <header>
      <strong>{{ label || '条件积木' }}</strong>
      <button
        type="button"
        data-testid="toggle-advanced-expr"
        @click="advancedMode = !advancedMode"
      >
        {{ advancedMode ? '返回积木' : '高级源码' }}
      </button>
    </header>

    <div v-if="!advancedMode" class="blocks">
      <ConditionGroupBlock
        :group="root"
        :path="[]"
        :form-field-keys="formFieldKeys"
        @change="onRootChange"
      />
    </div>

    <div v-else class="advanced">
      <textarea
        v-model="advancedDraft"
        rows="3"
        data-testid="advanced-expr-source"
        spellcheck="false"
      />
      <button type="button" data-testid="apply-advanced-expr" @click="applyAdvanced">应用源码</button>
    </div>

    <p class="preview" data-testid="condition-preview">
      <span>SERVICEOS_EXPR_V1：</span>
      <code>{{ preview }}</code>
    </p>
    <p v-if="compileError" class="err" data-testid="condition-error">{{ compileError }}</p>
  </section>
</template>

<style scoped>
.condition-builder {
  border: 1px solid #d0d7de;
  border-radius: 0.5rem;
  padding: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  background: #f6f8fa;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.blocks,
.advanced {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.advanced textarea {
  font: inherit;
}
.preview {
  margin: 0;
  font-size: 0.85rem;
}
.preview code {
  display: inline-block;
  margin-left: 0.25rem;
  word-break: break-all;
}
.err {
  color: #cf222e;
  margin: 0;
}
</style>
