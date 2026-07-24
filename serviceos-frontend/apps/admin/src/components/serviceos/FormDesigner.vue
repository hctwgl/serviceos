<script setup lang="ts">
import { Button, Input, Select, Tag } from '@serviceos/design-system'
import { computed, ref, watch } from 'vue'

export type FormFieldItem = {
  id: string
  label: string
  type: 'text' | 'number' | 'select' | 'image' | 'date'
  required: boolean
  placeholder: string
  options?: string[]
}

const props = withDefaults(defineProps<{
  fields: FormFieldItem[]
  editable?: boolean
}>(), {
  editable: false,
})

const emit = defineEmits<{
  'update:fields': [fields: FormFieldItem[]]
}>()

const selectedFieldId = ref<string>()
const selectedField = computed(() => props.fields.find((field) => field.id === selectedFieldId.value))
const fieldTypeLabels: Record<FormFieldItem['type'], string> = {
  text: '文本',
  number: '数字',
  select: '选择',
  image: '图片',
  date: '日期',
}
const fieldLibrary: Array<{ type: FormFieldItem['type']; label: string; detail: string }> = [
  { type: 'text', label: '文本', detail: '单行或多行描述' },
  { type: 'number', label: '数字', detail: '数值与计量' },
  { type: 'select', label: '选择', detail: '从业务选项中选择' },
  { type: 'image', label: '图片', detail: '现场照片与凭证' },
  { type: 'date', label: '日期', detail: '预约或作业日期' },
]

watch(
  () => props.fields,
  (fields) => {
    if (!fields.some((field) => field.id === selectedFieldId.value)) selectedFieldId.value = fields[0]?.id
  },
  { immediate: true },
)

function patchSelected(patch: Partial<FormFieldItem>) {
  if (!selectedField.value) return
  emit('update:fields', props.fields.map((field) => (
    field.id === selectedField.value?.id ? { ...field, ...patch } : field
  )))
}

function onRequiredChange(event: unknown) {
  const target = (event as { target?: { checked?: boolean } }).target
  patchSelected({ required: Boolean(target?.checked) })
}

function addField(type: FormFieldItem['type']) {
  if (!props.editable) return
  const next: FormFieldItem = {
    id: `field-${Date.now()}`,
    label: `${fieldTypeLabels[type]}字段`,
    type,
    required: false,
    placeholder: type === 'image' ? '上传现场照片' : `请输入${fieldTypeLabels[type]}`,
    options: type === 'select' ? ['选项一', '选项二'] : undefined,
  }
  emit('update:fields', [...props.fields, next])
  selectedFieldId.value = next.id
}
</script>

<template>
  <section class="sos-form-designer">
    <header class="sos-section-heading">
      <div>
        <span class="sos-eyebrow">FORM DESIGNER</span>
        <h3>现场勘测表</h3>
        <span>用业务字段组织现场采集，不暴露技术结构；表单随方案版本整体冻结。</span>
      </div>
      <Tag :color="editable ? 'processing' : 'default'">{{ editable ? '活动草稿' : '只读预览' }}</Tag>
    </header>

    <div class="sos-form-designer__workspace">
      <aside class="sos-form-designer__library">
        <span class="sos-form-designer__label">字段库</span>
        <button v-for="item in fieldLibrary" :key="item.type" type="button" :disabled="!editable" @click="addField(item.type)">
          <strong>{{ item.label }}</strong><small>{{ item.detail }}</small><span>＋</span>
        </button>
        <p>字段类型保持克制，先覆盖现场履约最常用的采集动作。</p>
      </aside>

      <main class="sos-form-designer__preview">
        <div class="sos-form-preview-phone">
          <header><span>现场勘测表</span><small>BYD 山东家充项目</small></header>
          <div v-if="!fields.length" class="sos-inline-empty"><strong>表单尚未添加字段</strong><span>从左侧字段库选择字段。</span></div>
          <div v-else class="sos-form-preview-fields">
            <label v-for="field in fields" :key="field.id" :class="{ active: field.id === selectedFieldId }" @click="selectedFieldId = field.id">
              <span>{{ field.label }}<b v-if="field.required">*</b></span>
              <Input v-if="field.type === 'text'" :placeholder="field.placeholder" disabled />
              <Input v-else-if="field.type === 'number'" type="number" :placeholder="field.placeholder" disabled />
              <Select v-else-if="field.type === 'select'" :placeholder="field.placeholder" :options="(field.options ?? []).map((option) => ({ value: option, label: option }))" disabled />
              <div v-else-if="field.type === 'image'" class="sos-form-preview-upload"><span>＋</span><small>{{ field.placeholder }}</small></div>
              <Input v-else type="date" disabled />
            </label>
          </div>
          <footer><Button type="primary" disabled>提交前检查</Button></footer>
        </div>
      </main>

      <aside class="sos-form-designer__inspector">
        <span class="sos-form-designer__label">字段属性</span>
        <template v-if="selectedField">
          <label><span>字段名称</span><Input :value="selectedField.label" :disabled="!editable" @update:value="patchSelected({ label: String($event) })" /></label>
          <label><span>字段类型</span><Select :value="selectedField.type" :options="fieldLibrary.map((item) => ({ value: item.type, label: item.label }))" disabled /></label>
          <label><span>填写提示</span><Input :value="selectedField.placeholder" :disabled="!editable" @update:value="patchSelected({ placeholder: String($event) })" /></label>
          <label class="sos-form-designer__checkbox"><input type="checkbox" :checked="selectedField.required" :disabled="!editable" @change="onRequiredChange" /><span>必填字段</span></label>
          <div v-if="selectedField.type === 'select'" class="sos-form-designer__options"><span>选择项</span><Tag v-for="option in selectedField.options ?? []" :key="option">{{ option }}</Tag></div>
        </template>
        <p v-else>点击中间预览中的字段，查看和编辑字段属性。</p>
      </aside>
    </div>
  </section>
</template>
