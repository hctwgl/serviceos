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

function replaceChild(index: number, node: ConditionNode) {
  const children = [...props.group.children]
  children[index] = node
  emitGroup({ ...props.group, children })
}

function unwrapAtom(node: ConditionNode): ConditionAtom | null {
  if (node.kind === 'atom') return node
  if (node.kind === 'not' && node.child.kind === 'atom') return node.child
  return null
}

function isNegated(node: ConditionNode): boolean {
  return node.kind === 'not'
}

function patchAtom(index: number, patch: Partial<ConditionAtom>) {
  const child = props.group.children[index]
  if (!child) return
  const atom = unwrapAtom(child)
  if (!atom) return
  const next: ConditionAtom = { ...atom, ...patch }
  if (patch.path != null && patch.valueKind == null) {
    if (isFormValuesPath(patch.path) && !isFormValuesPath(atom.path)) {
      next.valueKind = 'boolean'
      if (next.value !== 'true' && next.value !== 'false') {
        next.value = 'true'
      }
    } else if (!isFormValuesPath(patch.path) && isFormValuesPath(atom.path)) {
      next.valueKind = 'string'
      if (next.value === 'true' || next.value === 'false') {
        next.value = ''
      }
    }
  }
  replaceChild(index, child.kind === 'not' ? { kind: 'not', child: next } : next)
}

function toggleNot(index: number) {
  const child = props.group.children[index]
  if (!child) return
  if (child.kind === 'not') {
    replaceChild(index, child.child)
  } else {
    replaceChild(index, { kind: 'not', child })
  }
}

function groupChild(node: ConditionNode): ConditionGroup | null {
  if (node.kind === 'group') return node
  if (node.kind === 'not' && node.child.kind === 'group') return node.child
  return null
}

function onGroupChange(index: number, next: ConditionGroup) {
  const child = props.group.children[index]
  if (child?.kind === 'not') {
    replaceChild(index, { kind: 'not', child: next })
  } else {
    replaceChild(index, next)
  }
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
        v-if="unwrapAtom(child)"
        class="atom-row"
        :data-testid="`condition-atom-${pathKey(childPath(index))}`"
        :data-negated="isNegated(child) ? 'true' : 'false'"
      >
        <label class="not-toggle">
          <input
            type="checkbox"
            data-testid="condition-not"
            :checked="isNegated(child)"
            @change="toggleNot(index)"
          />
          !
        </label>
        <select
          :value="unwrapAtom(child)!.path"
          data-testid="condition-path"
          @change="
            patchAtom(index, {
              path: ($event.target as HTMLSelectElement).value,
            })
          "
        >
          <option v-for="p in pathOptions()" :key="p" :value="p">{{ p }}</option>
          <option
            v-if="!pathOptions().includes(unwrapAtom(child)!.path)"
            :value="unwrapAtom(child)!.path"
          >
            {{ unwrapAtom(child)!.path }}
          </option>
        </select>
        <select
          :value="unwrapAtom(child)!.op"
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
          :value="valueKindOf(unwrapAtom(child)!)"
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
          v-if="valueKindOf(unwrapAtom(child)!) === 'boolean'"
          :value="unwrapAtom(child)!.value || 'true'"
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
          :value="unwrapAtom(child)!.value"
          data-testid="condition-value"
          :placeholder="valueKindOf(unwrapAtom(child)!) === 'number' ? '数值' : '字面量'"
          @input="
            patchAtom(index, {
              value: ($event.target as HTMLInputElement).value,
            })
          "
        />
        <button type="button" data-testid="remove-atom" @click="removeChild(index)">删除</button>
      </div>
      <div v-else-if="groupChild(child)" class="nested-group">
        <label v-if="isNegated(child)" class="not-toggle nested-not">
          <input
            type="checkbox"
            data-testid="condition-not"
            :checked="true"
            @change="toggleNot(index)"
          />
          !（取反本组）
        </label>
        <button
          v-else
          type="button"
          data-testid="negate-group"
          @click="toggleNot(index)"
        >
          取反本组
        </button>
        <ConditionGroupBlock
          :group="groupChild(child)!"
          :path="childPath(index)"
          :form-field-keys="formFieldKeys"
          removable
          @change="onGroupChange(index, $event)"
          @remove="removeChild(index)"
        />
      </div>
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
.child,
.nested-group {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.join,
.not-toggle {
  display: flex;
  align-items: center;
  gap: 0.25rem;
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
.atom-row[data-negated='true'] {
  outline: 1px dashed #0969da;
  border-radius: 0.25rem;
  padding: 0.2rem;
}
</style>
