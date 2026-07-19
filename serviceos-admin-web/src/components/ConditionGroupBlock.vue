<script setup lang="ts">
import {
  availablePaths,
  emptyAtom,
  emptyGroup,
  isFormValuesPath,
  type ConditionAtom,
  type ConditionGroup,
  type ConditionNode,
  type JoinOp,
  type LiteralKind,
} from '../expression/serviceosExprV1Blocks'

const props = defineProps<{
  group: ConditionGroup
  /** 树路径，根为空数组；用于 testid */
  path: number[]
  formFieldKeys?: string[]
  /** 是否允许删除整个组（根组不可删） */
  removable?: boolean
}>()

const emit = defineEmits<{
  change: [group: ConditionGroup]
  remove: []
}>()

const pathOptions = () => availablePaths(props.formFieldKeys ?? [])

function emitGroup(next: ConditionGroup) {
  emit('change', next)
}

function setJoin(join: JoinOp) {
  emitGroup({ ...props.group, join })
}

function patchAtom(index: number, patch: Partial<ConditionAtom>) {
  const child = props.group.children[index]
  if (!child || child.kind !== 'atom') return
  const next: ConditionAtom = { ...child, ...patch }
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
  const children = [...props.group.children]
  children[index] = next
  emitGroup({ ...props.group, children })
}

function replaceChild(index: number, node: ConditionNode) {
  const children = [...props.group.children]
  children[index] = node
  emitGroup({ ...props.group, children })
}

function addAtom() {
  const defaultPath = pathOptions()[0] ?? 'workOrder.brandCode'
  emitGroup({
    ...props.group,
    children: [...props.group.children, emptyAtom(defaultPath)],
  })
}

function addGroup() {
  emitGroup({
    ...props.group,
    children: [...props.group.children, emptyGroup('AND')],
  })
}

function removeChild(index: number) {
  if (props.group.children.length <= 1) {
    emitGroup(emptyGroup(props.group.join))
    return
  }
  emitGroup({
    ...props.group,
    children: props.group.children.filter((_, i) => i !== index),
  })
}

function valueKindOf(atom: ConditionAtom): LiteralKind {
  return atom.valueKind ?? 'string'
}

function childPath(index: number): number[] {
  return [...props.path, index]
}

function pathKey(p: number[]): string {
  return p.length ? p.join('-') : 'root'
}
</script>

<template>
  <div
    class="group-block"
    :data-testid="`condition-group-${pathKey(path)}`"
    :data-depth="path.length"
  >
    <div class="group-header">
      <label class="join">
        组合
        <select
          :value="group.join"
          :data-testid="path.length === 0 ? 'condition-join' : `condition-join-${pathKey(path)}`"
          @change="setJoin(($event.target as HTMLSelectElement).value as JoinOp)"
        >
          <option value="AND">AND (&&)</option>
          <option value="OR">OR (||)</option>
        </select>
      </label>
      <button
        v-if="removable"
        type="button"
        :data-testid="`remove-group-${pathKey(path)}`"
        @click="emit('remove')"
      >
        删除组
      </button>
    </div>

    <div
      v-for="(child, index) in group.children"
      :key="`${pathKey(path)}-${index}`"
      class="child"
    >
      <div
        v-if="child.kind === 'atom'"
        class="atom-row"
        :data-testid="`condition-atom-${pathKey(childPath(index))}`"
      >
        <select
          :value="child.path"
          data-testid="condition-path"
          @change="
            patchAtom(index, {
              path: ($event.target as HTMLSelectElement).value,
            })
          "
        >
          <option v-for="p in pathOptions()" :key="p" :value="p">{{ p }}</option>
          <option v-if="!pathOptions().includes(child.path)" :value="child.path">
            {{ child.path }}
          </option>
        </select>
        <select
          :value="child.op"
          data-testid="condition-op"
          @change="
            patchAtom(index, {
              op: ($event.target as HTMLSelectElement).value as '==' | '!=',
            })
          "
        >
          <option value="==">==</option>
          <option value="!=">!=</option>
        </select>
        <select
          :value="valueKindOf(child)"
          data-testid="condition-value-kind"
          @change="
            patchAtom(index, {
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
          v-if="valueKindOf(child) === 'boolean'"
          :value="child.value || 'true'"
          data-testid="condition-value"
          @change="
            patchAtom(index, {
              value: ($event.target as HTMLSelectElement).value,
            })
          "
        >
          <option value="true">true</option>
          <option value="false">false</option>
        </select>
        <input
          v-else
          :value="child.value"
          data-testid="condition-value"
          :placeholder="valueKindOf(child) === 'number' ? '数值' : '字面量'"
          @input="
            patchAtom(index, {
              value: ($event.target as HTMLInputElement).value,
            })
          "
        />
        <button
          type="button"
          data-testid="remove-atom"
          @click="removeChild(index)"
        >
          删除
        </button>
      </div>
      <ConditionGroupBlock
        v-else
        :group="child"
        :path="childPath(index)"
        :form-field-keys="formFieldKeys"
        removable
        @change="replaceChild(index, $event)"
        @remove="removeChild(index)"
      />
    </div>

    <div class="actions">
      <button type="button" data-testid="add-atom" @click="addAtom">添加条件</button>
      <button type="button" data-testid="add-group" @click="addGroup">添加条件组</button>
    </div>
  </div>
</template>

<style scoped>
.group-block {
  border: 1px dashed #d0d7de;
  border-radius: 0.4rem;
  padding: 0.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  background: #fff;
}
.group-block[data-depth='0'] {
  border-style: solid;
  background: transparent;
}
.group-header,
.actions,
.atom-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  align-items: center;
}
.child {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.join {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.85rem;
}
.atom-row select,
.atom-row input,
.join select {
  font: inherit;
}
.atom-row input {
  min-width: 8rem;
}
</style>
