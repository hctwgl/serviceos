<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  availablePaths,
  compileCondition,
  emptyAtom,
  emptyGroup,
  isFormValuesPath,
  tryParseCondition,
  type ConditionAtom,
  type ConditionGroup,
  type ConditionNode,
  type JoinOp,
  type LiteralKind,
} from '../expression/serviceosExprV1Blocks'

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

const pathOptions = computed(() => availablePaths(props.formFieldKeys ?? []))

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

function updateAtom(index: number, patch: Partial<ConditionAtom>) {
  const child = root.value.children[index]
  if (!child || child.kind !== 'atom') return
  const next: ConditionAtom = { ...child, ...patch }
  // 切换到 formValues 路径时默认布尔字面量；切回上下文路径时默认字符串
  if (patch.path != null && patch.valueKind == null) {
    if (isFormValuesPath(patch.path) && !isFormValuesPath(child.path)) {
      next.valueKind = 'boolean'
      if (next.value !== 'true' && next.value !== 'false') {
        next.value = 'true'
      }
    } else if (!isFormValuesPath(patch.path) && isFormValuesPath(child.path)) {
      next.valueKind = 'string'
      if (next.value === 'true' || next.value === 'false') {
        next.value = ''
      }
    }
  }
  root.value.children[index] = next
  emitCompiled()
}

function setJoin(join: JoinOp) {
  root.value = { ...root.value, join }
  emitCompiled()
}

function addAtom() {
  const defaultPath = pathOptions.value[0] ?? 'workOrder.brandCode'
  root.value = {
    ...root.value,
    children: [...root.value.children, emptyAtom(defaultPath)],
  }
  emitCompiled()
}

function removeAtom(index: number) {
  if (root.value.children.length <= 1) {
    root.value = emptyGroup()
  } else {
    root.value = {
      ...root.value,
      children: root.value.children.filter((_, i) => i !== index),
    }
  }
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

function asAtom(node: ConditionNode): ConditionAtom | null {
  return node.kind === 'atom' ? node : null
}

function valueKindOf(atom: ConditionAtom): LiteralKind {
  return atom.valueKind ?? 'string'
}
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
      <label class="join">
        组合
        <select
          :value="root.join"
          data-testid="condition-join"
          @change="setJoin(($event.target as HTMLSelectElement).value as JoinOp)"
        >
          <option value="AND">AND (&&)</option>
          <option value="OR">OR (||)</option>
        </select>
      </label>
      <div
        v-for="(child, index) in root.children"
        :key="index"
        class="atom-row"
        :data-testid="`condition-atom-${index}`"
      >
        <template v-if="asAtom(child)">
          <select
            :value="asAtom(child)!.path"
            data-testid="condition-path"
            @change="
              updateAtom(index, {
                path: ($event.target as HTMLSelectElement).value,
              })
            "
          >
            <option v-for="path in pathOptions" :key="path" :value="path">{{ path }}</option>
            <!-- 解析出的路径若不在当前选项中，仍保留可选以免丢失 -->
            <option
              v-if="!pathOptions.includes(asAtom(child)!.path)"
              :value="asAtom(child)!.path"
            >
              {{ asAtom(child)!.path }}
            </option>
          </select>
          <select
            :value="asAtom(child)!.op"
            data-testid="condition-op"
            @change="
              updateAtom(index, {
                op: ($event.target as HTMLSelectElement).value as '==' | '!=',
              })
            "
          >
            <option value="==">==</option>
            <option value="!=">!=</option>
          </select>
          <select
            :value="valueKindOf(asAtom(child)!)"
            data-testid="condition-value-kind"
            @change="
              updateAtom(index, {
                valueKind: ($event.target as HTMLSelectElement).value as LiteralKind,
                value:
                  ($event.target as HTMLSelectElement).value === 'boolean'
                    ? 'true'
                    : ($event.target as HTMLSelectElement).value === 'number'
                      ? '0'
                      : '',
              })
            "
          >
            <option value="string">字符串</option>
            <option value="boolean">布尔</option>
            <option value="number">数值</option>
          </select>
          <select
            v-if="valueKindOf(asAtom(child)!) === 'boolean'"
            :value="asAtom(child)!.value || 'true'"
            data-testid="condition-value"
            @change="
              updateAtom(index, {
                value: ($event.target as HTMLSelectElement).value,
              })
            "
          >
            <option value="true">true</option>
            <option value="false">false</option>
          </select>
          <input
            v-else
            :value="asAtom(child)!.value"
            data-testid="condition-value"
            :placeholder="valueKindOf(asAtom(child)!) === 'number' ? '数值' : '字面量'"
            @input="
              updateAtom(index, {
                value: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <button type="button" data-testid="remove-atom" @click="removeAtom(index)">删除</button>
        </template>
      </div>
      <button type="button" data-testid="add-atom" @click="addAtom">添加条件</button>
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
.atom-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  align-items: center;
}
.atom-row select,
.atom-row input,
.join select,
.advanced textarea {
  font: inherit;
}
.atom-row input {
  min-width: 8rem;
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
