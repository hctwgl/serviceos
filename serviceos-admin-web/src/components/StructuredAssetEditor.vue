<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ConditionBuilder from './ConditionBuilder.vue'

const props = defineProps<{
  assetType: 'FORM' | 'EVIDENCE' | 'SLA'
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const parseError = ref<string | null>(null)
const draft = ref<Record<string, unknown>>({})

function load(raw: string) {
  try {
    draft.value = JSON.parse(raw) as Record<string, unknown>
    parseError.value = null
  } catch (err) {
    parseError.value = err instanceof Error ? err.message : 'JSON 无效'
  }
}

watch(
  () => props.modelValue,
  (value) => load(value),
  { immediate: true },
)

function commit() {
  emit('update:modelValue', JSON.stringify(draft.value, null, 2))
}

function setRoot(key: string, value: unknown) {
  draft.value = { ...draft.value, [key]: value }
  commit()
}

const formSections = computed(() => {
  const sections = draft.value.sections
  return Array.isArray(sections) ? (sections as Array<Record<string, unknown>>) : []
})

/** 当前 FORM 全部 fieldKey，供 formValues["…"] 积木下拉。 */
const formFieldKeys = computed(() => {
  const keys: string[] = []
  for (const section of formSections.value) {
    if (!Array.isArray(section.fields)) continue
    for (const field of section.fields as Array<Record<string, unknown>>) {
      const key = field.fieldKey
      if (typeof key === 'string' && key.trim()) {
        keys.push(key.trim())
      }
    }
  }
  return keys
})

const evidenceItems = computed(() => {
  const items = draft.value.items
  return Array.isArray(items) ? (items as Array<Record<string, unknown>>) : []
})

const slaTaskTypes = computed(() => {
  const types = draft.value.taskTypes
  return Array.isArray(types) ? (types as string[]).join(',') : ''
})

function exprSource(item: Record<string, unknown>, field: string): string {
  const expr = item[field]
  if (expr && typeof expr === 'object') {
    const source = (expr as { source?: unknown }).source
    return typeof source === 'string' ? source : ''
  }
  return ''
}

/** 空源码清除可选表达式，避免发布期留下空 SERVICEOS_EXPR_V1。 */
function withOptionalExpression(
  item: Record<string, unknown>,
  field: string,
  source: string,
): Record<string, unknown> {
  if (!source.trim()) {
    const next = { ...item }
    delete next[field]
    return next
  }
  return {
    ...item,
    [field]: { language: 'SERVICEOS_EXPR_V1', source },
  }
}

function updateSection(index: number, patch: Record<string, unknown>) {
  const next = formSections.value.map((section, i) =>
    i === index ? { ...section, ...patch } : section,
  )
  setRoot('sections', next)
}

function setSectionExpression(index: number, field: 'visibility', source: string) {
  const section = formSections.value[index]
  if (!section) return
  updateSection(index, withOptionalExpression(section, field, source))
}

function addSection() {
  const key = `section_${formSections.value.length + 1}`
  setRoot('sections', [
    ...formSections.value,
    {
      sectionKey: key,
      title: '新分组',
      fields: [
        {
          fieldKey: `${key}.field1`,
          label: '新字段',
          dataType: 'STRING',
          binding: 'task.input.field1',
          required: false,
        },
      ],
    },
  ])
}

function addField(sectionIndex: number) {
  const section = formSections.value[sectionIndex]
  if (!section) return
  const fields = Array.isArray(section.fields)
    ? ([...section.fields] as Array<Record<string, unknown>>)
    : []
  fields.push({
    fieldKey: `field_${fields.length + 1}`,
    label: '新字段',
    dataType: 'STRING',
    binding: 'task.input.newField',
    required: false,
  })
  updateSection(sectionIndex, { fields })
}

function updateField(
  sectionIndex: number,
  fieldIndex: number,
  patch: Record<string, unknown>,
) {
  const section = formSections.value[sectionIndex]
  if (!section || !Array.isArray(section.fields)) return
  const fields = (section.fields as Array<Record<string, unknown>>).map((field, i) =>
    i === fieldIndex ? { ...field, ...patch } : field,
  )
  updateSection(sectionIndex, { fields })
}

function setFieldExpression(
  sectionIndex: number,
  fieldIndex: number,
  exprField: 'visibleWhen' | 'requiredWhen',
  source: string,
) {
  const section = formSections.value[sectionIndex]
  if (!section || !Array.isArray(section.fields)) return
  const fields = (section.fields as Array<Record<string, unknown>>).map((field, i) =>
    i === fieldIndex ? withOptionalExpression(field, exprField, source) : field,
  )
  updateSection(sectionIndex, { fields })
}

function removeField(sectionIndex: number, fieldIndex: number) {
  const section = formSections.value[sectionIndex]
  if (!section || !Array.isArray(section.fields)) return
  const fields = (section.fields as Array<Record<string, unknown>>).filter(
    (_, i) => i !== fieldIndex,
  )
  updateSection(sectionIndex, { fields })
}

function addEvidenceItem() {
  setRoot('items', [
    ...evidenceItems.value,
    {
      evidenceKey: `item_${evidenceItems.value.length + 1}`,
      name: '新资料项',
      mediaType: 'PHOTO',
      required: true,
      capture: { allowCamera: true, allowGallery: true, minCount: 1, maxCount: 3 },
    },
  ])
}

function updateEvidenceItem(index: number, patch: Record<string, unknown>) {
  const next = evidenceItems.value.map((item, i) => (i === index ? { ...item, ...patch } : item))
  setRoot('items', next)
}

function setEvidenceItemExpression(
  index: number,
  exprField: 'requiredWhen',
  source: string,
) {
  const item = evidenceItems.value[index]
  if (!item) return
  updateEvidenceItem(index, withOptionalExpression(item, exprField, source))
}

function removeEvidenceItem(index: number) {
  setRoot(
    'items',
    evidenceItems.value.filter((_, i) => i !== index),
  )
}

function setSlaTaskTypes(csv: string) {
  const types = csv
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
  setRoot('taskTypes', types)
}

const FORM_STAGES = ['INTAKE', 'SURVEY', 'INSTALLATION', 'REPAIR', 'REVIEW', 'SETTLEMENT', 'OTHER']
const EVIDENCE_STAGES = ['SURVEY', 'INSTALLATION', 'REPAIR', 'REVIEW', 'OTHER']
const DATA_TYPES = [
  'STRING',
  'TEXT',
  'INTEGER',
  'DECIMAL',
  'BOOLEAN',
  'DATE',
  'DATETIME',
  'ENUM',
  'MULTI_ENUM',
  'FILE_REF',
]
const MEDIA_TYPES = ['PHOTO', 'VIDEO', 'DOCUMENT', 'SIGNATURE', 'GENERATED_REPORT']
</script>

<template>
  <section class="structured-editor" data-testid="structured-asset-editor">
    <header>
      <strong>{{ assetType }} 可视配置器</strong>
      <span class="hint">编辑结构化字段并同步定义 JSON；发布仍走服务端 Schema 校验。</span>
    </header>
    <p v-if="parseError" class="err" data-testid="structured-parse-error">{{ parseError }}</p>

    <div v-else-if="assetType === 'FORM'" class="form-editor" data-testid="form-structure-editor">
      <label>
        title
        <input
          data-testid="form-title"
          :value="String(draft.title ?? '')"
          @change="setRoot('title', ($event.target as HTMLInputElement).value)"
        />
      </label>
      <label>
        stage
        <select
          data-testid="form-stage"
          :value="String(draft.stage ?? 'SURVEY')"
          @change="setRoot('stage', ($event.target as HTMLSelectElement).value)"
        >
          <option v-for="stage in FORM_STAGES" :key="stage" :value="stage">{{ stage }}</option>
        </select>
      </label>
      <article
        v-for="(section, sIndex) in formSections"
        :key="sIndex"
        class="card"
        :data-testid="`form-section-${sIndex}`"
      >
        <div class="row">
          <label>
            sectionKey
            <input
              :value="String(section.sectionKey ?? '')"
              @change="
                updateSection(sIndex, { sectionKey: ($event.target as HTMLInputElement).value })
              "
            />
          </label>
          <label>
            title
            <input
              :value="String(section.title ?? '')"
              @change="updateSection(sIndex, { title: ($event.target as HTMLInputElement).value })"
            />
          </label>
        </div>
        <ConditionBuilder
          :model-value="exprSource(section, 'visibility')"
          :label="`分组 ${String(section.sectionKey ?? sIndex)} visibility`"
          :form-field-keys="formFieldKeys"
          data-testid="section-visibility-builder"
          @update:model-value="setSectionExpression(sIndex, 'visibility', $event)"
        />
        <div
          v-for="(field, fIndex) in (Array.isArray(section.fields) ? section.fields : []) as Array<
            Record<string, unknown>
          >"
          :key="fIndex"
          class="field-block"
          :data-testid="`form-field-${sIndex}-${fIndex}`"
        >
          <div class="field-row">
            <input
              data-testid="form-field-key"
              :value="String(field.fieldKey ?? '')"
              placeholder="fieldKey"
              @change="
                updateField(sIndex, fIndex, { fieldKey: ($event.target as HTMLInputElement).value })
              "
            />
            <input
              data-testid="form-field-label"
              :value="String(field.label ?? '')"
              placeholder="label"
              @change="
                updateField(sIndex, fIndex, { label: ($event.target as HTMLInputElement).value })
              "
            />
            <select
              data-testid="form-field-datatype"
              :value="String(field.dataType ?? 'STRING')"
              @change="
                updateField(sIndex, fIndex, {
                  dataType: ($event.target as HTMLSelectElement).value,
                })
              "
            >
              <option v-for="dt in DATA_TYPES" :key="dt" :value="dt">{{ dt }}</option>
            </select>
            <input
              data-testid="form-field-binding"
              :value="String(field.binding ?? '')"
              placeholder="binding"
              @change="
                updateField(sIndex, fIndex, { binding: ($event.target as HTMLInputElement).value })
              "
            />
            <label class="check">
              <input
                type="checkbox"
                data-testid="form-field-required"
                :checked="Boolean(field.required)"
                @change="
                  updateField(sIndex, fIndex, {
                    required: ($event.target as HTMLInputElement).checked,
                  })
                "
              />
              required
            </label>
            <button
              type="button"
              data-testid="remove-form-field"
              @click="removeField(sIndex, fIndex)"
            >
              删除字段
            </button>
          </div>
          <ConditionBuilder
            :model-value="exprSource(field, 'visibleWhen')"
            :label="`字段 ${String(field.fieldKey ?? fIndex)} visibleWhen`"
            :form-field-keys="formFieldKeys"
            data-testid="field-visible-when-builder"
            @update:model-value="setFieldExpression(sIndex, fIndex, 'visibleWhen', $event)"
          />
          <ConditionBuilder
            :model-value="exprSource(field, 'requiredWhen')"
            :label="`字段 ${String(field.fieldKey ?? fIndex)} requiredWhen`"
            :form-field-keys="formFieldKeys"
            data-testid="field-required-when-builder"
            @update:model-value="setFieldExpression(sIndex, fIndex, 'requiredWhen', $event)"
          />
        </div>
        <button type="button" data-testid="add-form-field" @click="addField(sIndex)">
          添加字段
        </button>
      </article>
      <button type="button" data-testid="add-form-section" @click="addSection">添加分组</button>
    </div>

    <div
      v-else-if="assetType === 'EVIDENCE'"
      class="evidence-editor"
      data-testid="evidence-structure-editor"
    >
      <label>
        title
        <input
          data-testid="evidence-title"
          :value="String(draft.title ?? '')"
          @change="setRoot('title', ($event.target as HTMLInputElement).value)"
        />
      </label>
      <label>
        stage
        <select
          data-testid="evidence-stage"
          :value="String(draft.stage ?? 'SURVEY')"
          @change="setRoot('stage', ($event.target as HTMLSelectElement).value)"
        >
          <option v-for="stage in EVIDENCE_STAGES" :key="stage" :value="stage">{{ stage }}</option>
        </select>
      </label>
      <article
        v-for="(item, index) in evidenceItems"
        :key="index"
        class="card"
        :data-testid="`evidence-item-${index}`"
      >
        <div class="row">
          <input
            data-testid="evidence-key"
            :value="String(item.evidenceKey ?? '')"
            placeholder="evidenceKey"
            @change="
              updateEvidenceItem(index, {
                evidenceKey: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <input
            data-testid="evidence-name"
            :value="String(item.name ?? '')"
            placeholder="name"
            @change="updateEvidenceItem(index, { name: ($event.target as HTMLInputElement).value })"
          />
          <select
            data-testid="evidence-media-type"
            :value="String(item.mediaType ?? 'PHOTO')"
            @change="
              updateEvidenceItem(index, {
                mediaType: ($event.target as HTMLSelectElement).value,
              })
            "
          >
            <option v-for="mt in MEDIA_TYPES" :key="mt" :value="mt">{{ mt }}</option>
          </select>
          <label class="check">
            <input
              type="checkbox"
              data-testid="evidence-required"
              :checked="Boolean(item.required)"
              @change="
                updateEvidenceItem(index, {
                  required: ($event.target as HTMLInputElement).checked,
                })
              "
            />
            required
          </label>
          <button
            type="button"
            data-testid="remove-evidence-item"
            @click="removeEvidenceItem(index)"
          >
            删除
          </button>
        </div>
        <ConditionBuilder
          :model-value="exprSource(item, 'requiredWhen')"
          :label="`资料项 ${String(item.evidenceKey ?? index)} requiredWhen`"
          data-testid="evidence-required-when-builder"
          @update:model-value="setEvidenceItemExpression(index, 'requiredWhen', $event)"
        />
        <p class="hint">
          上下文路径可直接积木编辑；同 Bundle 同 stage FORM 的 formValues["…"] 可用高级源码（字段键自动发现递延）。
        </p>
      </article>
      <button type="button" data-testid="add-evidence-item" @click="addEvidenceItem">
        添加资料项
      </button>
    </div>

    <div v-else class="sla-editor" data-testid="sla-structure-editor">
      <label>
        policyKey
        <input
          data-testid="sla-policy-key"
          :value="String(draft.policyKey ?? '')"
          @change="setRoot('policyKey', ($event.target as HTMLInputElement).value)"
        />
      </label>
      <label>
        taskTypes（逗号分隔）
        <input
          data-testid="sla-task-types"
          :value="slaTaskTypes"
          @change="setSlaTaskTypes(($event.target as HTMLInputElement).value)"
        />
      </label>
      <label>
        targetDurationSeconds
        <input
          type="number"
          min="1"
          data-testid="sla-duration"
          :value="Number(draft.targetDurationSeconds ?? 3600)"
          @change="
            setRoot(
              'targetDurationSeconds',
              Number(($event.target as HTMLInputElement).value) || 3600,
            )
          "
        />
      </label>
      <p class="hint">
        subjectType=TASK · startEvent=TASK_CREATED · stopEvent=TASK_COMPLETED · clockMode=ELAPSED
      </p>
    </div>
  </section>
</template>

<style scoped>
.structured-editor {
  border: 1px solid #d0d7de;
  border-radius: 0.5rem;
  padding: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  background: #f6f8fa;
}
header {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}
.hint {
  color: #656d76;
  font-size: 0.85rem;
}
.err {
  color: #cf222e;
  margin: 0;
}
.card {
  border: 1px solid #d0d7de;
  border-radius: 0.4rem;
  padding: 0.6rem;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}
.row,
.field-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  align-items: center;
}
.field-block {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 0.4rem 0;
  border-top: 1px dashed #d0d7de;
}
label {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  font-size: 0.85rem;
}
.check {
  flex-direction: row;
  align-items: center;
  gap: 0.25rem;
}
input,
select,
button {
  font: inherit;
}
</style>
