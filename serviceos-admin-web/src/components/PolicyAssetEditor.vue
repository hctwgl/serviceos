<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ConditionBuilder from './ConditionBuilder.vue'

type PolicyType =
  | 'RULE'
  | 'DISPATCH'
  | 'NOTIFICATION'
  | 'ASSIGNEE_POLICY'
  | 'INTEGRATION'
  | 'PRICING'

const props = defineProps<{
  assetType: PolicyType
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

function asObjectArray(key: string): Array<Record<string, unknown>> {
  const value = draft.value[key]
  return Array.isArray(value) ? (value as Array<Record<string, unknown>>) : []
}

function updateArrayItem(key: string, index: number, patch: Record<string, unknown>) {
  const next = asObjectArray(key).map((item, i) => (i === index ? { ...item, ...patch } : item))
  setRoot(key, next)
}

function removeArrayItem(key: string, index: number) {
  setRoot(
    key,
    asObjectArray(key).filter((_, i) => i !== index),
  )
}

function setItemExpression(key: string, index: number, exprField: 'when' | 'expression', source: string) {
  updateArrayItem(key, index, {
    [exprField]: { language: 'SERVICEOS_EXPR_V1', source },
  })
}

function exprSource(item: Record<string, unknown>, field: 'when' | 'expression' = 'when'): string {
  const expr = item[field]
  if (expr && typeof expr === 'object') {
    const source = (expr as { source?: unknown }).source
    return typeof source === 'string' ? source : ''
  }
  return ''
}

const rules = computed(() => asObjectArray('rules'))
const triggers = computed(() => asObjectArray('triggers'))
const strategies = computed(() => asObjectArray('strategies'))
const hardFilters = computed(() => asObjectArray('hardFilters'))
const scoring = computed(() => asObjectArray('scoring'))
const fieldMappings = computed(() => asObjectArray('fieldMappings'))
const lines = computed(() => asObjectArray('lines'))

function addRule() {
  setRoot('rules', [
    ...rules.value,
    {
      ruleCode: `RULE_${rules.value.length + 1}`,
      name: '新规则',
      severity: 'WARN',
      when: { language: 'SERVICEOS_EXPR_V1', source: 'workOrder.brandCode == "PLATFORM"' },
      rejectReasonCode: 'NEW_RULE',
      message: '',
    },
  ])
}

function addTrigger() {
  setRoot('triggers', [
    ...triggers.value,
    {
      triggerKey: `trigger_${triggers.value.length + 1}`,
      eventType: 'task.completed',
      templateKey: 'task.completed.inapp',
      channel: 'IN_APP',
      when: { language: 'SERVICEOS_EXPR_V1', source: 'task.taskType == "DESIGNER_DEMO"' },
      recipientRole: 'PROJECT_MANAGER',
    },
  ])
}

function addStrategy() {
  setRoot('strategies', [
    ...strategies.value,
    {
      strategyKey: `strategy_${strategies.value.length + 1}`,
      candidateType: 'ROLE',
      priority: 10,
      when: { language: 'SERVICEOS_EXPR_V1', source: 'workOrder.brandCode == "PLATFORM"' },
      roleCode: 'TECHNICIAN',
      maxCandidates: 10,
    },
  ])
}

function addHardFilter() {
  setRoot('hardFilters', [
    ...hardFilters.value,
    {
      filterKey: `FILTER_${hardFilters.value.length + 1}`,
      order: hardFilters.value.length + 1,
      expression: {
        language: 'SERVICEOS_EXPR_V1',
        source: 'workOrder.brandCode == "PLATFORM"',
      },
      failureCode: 'FILTER_FAILED',
    },
  ])
}

function addScoring() {
  setRoot('scoring', [
    ...scoring.value,
    {
      factorKey: `FACTOR_${scoring.value.length + 1}`,
      weight: 1.0,
      expression: {
        language: 'SERVICEOS_EXPR_V1',
        source: 'workOrder.brandCode == "PLATFORM"',
      },
    },
  ])
}

function addFieldMapping() {
  setRoot('fieldMappings', [
    ...fieldMappings.value,
    {
      mappingId: `map_${fieldMappings.value.length + 1}`,
      externalPath: 'external.field',
      internalPath: 'internal.field',
      required: false,
      transform: 'TRIM',
    },
  ])
}

const MESSAGE_TYPES = ['CREATE_WORK_ORDER', 'UPDATE_WORK_ORDER', 'CANCEL_WORK_ORDER'] as const
const CONDITION_OPS = ['PRESENT', 'EQUALS', 'NOT_EQUALS', 'IN', 'NOT_IN'] as const

function parseScalar(raw: string): string | number | boolean {
  const trimmed = raw.trim()
  if (trimmed === 'true') return true
  if (trimmed === 'false') return false
  if (/^-?\d+(\.\d+)?$/.test(trimmed)) return Number(trimmed)
  return trimmed
}

function scalarToInput(value: unknown): string {
  if (value === undefined || value === null) return ''
  return String(value)
}

/** 空值删除可选标量字段；constantValue 与 defaultValue/enumMap 互斥。 */
function setMappingOptionalScalar(
  index: number,
  field: 'constantValue' | 'defaultValue',
  raw: string,
) {
  const item = { ...fieldMappings.value[index] }
  if (!raw.trim()) {
    delete item[field]
    // updateArrayItem 是 merge patch，无法删除键；必须整项替换
    replaceFieldMapping(index, item)
    return
  }
  if (field === 'constantValue') {
    delete item.defaultValue
    delete item.enumMap
  } else {
    delete item.constantValue
  }
  item[field] = parseScalar(raw)
  replaceFieldMapping(index, item)
}

function replaceFieldMapping(index: number, next: Record<string, unknown>) {
  const list = fieldMappings.value.map((item, i) => (i === index ? next : item))
  setRoot('fieldMappings', list)
}

function enumMapToText(mapping: Record<string, unknown>): string {
  const enumMap = mapping.enumMap
  if (!enumMap || typeof enumMap !== 'object') return ''
  return Object.entries(enumMap as Record<string, string>)
    .map(([k, v]) => `${k}=${v}`)
    .join('\n')
}

function setMappingEnumMap(index: number, text: string) {
  const item = { ...fieldMappings.value[index] }
  const lines = text
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
  if (lines.length === 0) {
    delete item.enumMap
    replaceFieldMapping(index, item)
    return
  }
  const enumMap: Record<string, string> = {}
  for (const line of lines) {
    const eq = line.indexOf('=')
    if (eq <= 0) continue
    const key = line.slice(0, eq).trim()
    const value = line.slice(eq + 1).trim()
    if (key && value) enumMap[key] = value
  }
  delete item.constantValue
  if (Object.keys(enumMap).length === 0) {
    delete item.enumMap
  } else {
    item.enumMap = enumMap
  }
  replaceFieldMapping(index, item)
}

function mappingCondition(mapping: Record<string, unknown>): Record<string, unknown> | null {
  const condition = mapping.condition
  return condition && typeof condition === 'object' ? (condition as Record<string, unknown>) : null
}

function setMappingCondition(index: number, patch: Record<string, unknown> | null) {
  const item = { ...fieldMappings.value[index] }
  if (patch == null) {
    delete item.condition
  } else {
    item.condition = patch
  }
  replaceFieldMapping(index, item)
}

function updateMappingCondition(index: number, patch: Record<string, unknown>) {
  const current = mappingCondition(fieldMappings.value[index] ?? {}) ?? {
    sourcePath: 'external.field',
    operator: 'PRESENT',
  }
  const next = { ...current, ...patch }
  const op = String(next.operator ?? 'PRESENT')
  if (op === 'PRESENT') {
    delete next.value
    delete next.values
  } else if (op === 'EQUALS' || op === 'NOT_EQUALS') {
    delete next.values
    if (next.value === undefined) next.value = ''
  } else {
    delete next.value
    if (!Array.isArray(next.values)) next.values = []
  }
  setMappingCondition(index, next)
}

function setDirection(direction: string) {
  if (direction === 'OUTBOUND') {
    const next: Record<string, unknown> = { ...draft.value, direction }
    delete next.messageType
    draft.value = next
    commit()
    return
  }
  const next: Record<string, unknown> = { ...draft.value, direction }
  if (!next.messageType) {
    next.messageType = 'CREATE_WORK_ORDER'
  }
  draft.value = next
  commit()
}

function addPricingLine() {
  setRoot('lines', [
    ...lines.value,
    {
      lineKey: `line_${lines.value.length + 1}`,
      chargeCode: 'CHARGE',
      amountMinor: 0,
      when: { language: 'SERVICEOS_EXPR_V1', source: 'workOrder.brandCode == "PLATFORM"' },
      billableTo: 'OEM',
    },
  ])
}

const SEVERITIES = ['BLOCK', 'WARN', 'REQUIRE_APPROVAL']
const CHANNELS = ['IN_APP', 'SMS', 'EMAIL', 'PUSH']
const TRANSFORMS = ['NONE', 'TRIM', 'UPPER', 'LOWER', 'DATE_ISO']
const BILLABLE = ['OEM', 'NETWORK', 'PLATFORM', 'CUSTOMER']
</script>

<template>
  <section class="policy-editor" data-testid="policy-asset-editor">
    <header>
      <strong>{{ assetType }} 结构化设计器</strong>
      <span class="hint">编辑策略条目并同步 JSON；条件可就地用积木改写。</span>
    </header>
    <p v-if="parseError" class="err">{{ parseError }}</p>

    <div v-else-if="assetType === 'RULE'" data-testid="rule-structure-editor">
      <div class="row">
        <label>
          ruleKey
          <input
            data-testid="rule-key"
            :value="String(draft.ruleKey ?? '')"
            @change="setRoot('ruleKey', ($event.target as HTMLInputElement).value)"
          />
        </label>
        <label>
          defaultAction
          <select
            data-testid="rule-default-action"
            :value="String(draft.defaultAction ?? 'PASS')"
            @change="setRoot('defaultAction', ($event.target as HTMLSelectElement).value)"
          >
            <option value="PASS">PASS</option>
            <option value="REQUIRE_MANUAL">REQUIRE_MANUAL</option>
          </select>
        </label>
      </div>
      <article
        v-for="(rule, index) in rules"
        :key="index"
        class="card"
        :data-testid="`rule-item-${index}`"
      >
        <div class="row">
          <input
            data-testid="rule-code"
            :value="String(rule.ruleCode ?? '')"
            placeholder="ruleCode"
            @change="
              updateArrayItem('rules', index, {
                ruleCode: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <input
            :value="String(rule.name ?? '')"
            placeholder="name"
            @change="
              updateArrayItem('rules', index, { name: ($event.target as HTMLInputElement).value })
            "
          />
          <select
            data-testid="rule-severity"
            :value="String(rule.severity ?? 'WARN')"
            @change="
              updateArrayItem('rules', index, {
                severity: ($event.target as HTMLSelectElement).value,
              })
            "
          >
            <option v-for="s in SEVERITIES" :key="s" :value="s">{{ s }}</option>
          </select>
          <input
            :value="String(rule.rejectReasonCode ?? '')"
            placeholder="rejectReasonCode"
            @change="
              updateArrayItem('rules', index, {
                rejectReasonCode: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <button type="button" data-testid="remove-rule" @click="removeArrayItem('rules', index)">
            删除
          </button>
        </div>
        <ConditionBuilder
          :model-value="exprSource(rule)"
          :label="`规则 ${String(rule.ruleCode ?? index)} 条件`"
          @update:model-value="setItemExpression('rules', index, 'when', $event)"
        />
      </article>
      <button type="button" data-testid="add-rule" @click="addRule">添加规则</button>
    </div>

    <div v-else-if="assetType === 'NOTIFICATION'" data-testid="notification-structure-editor">
      <label>
        defaultChannel
        <select
          data-testid="notification-default-channel"
          :value="String(draft.defaultChannel ?? 'IN_APP')"
          @change="setRoot('defaultChannel', ($event.target as HTMLSelectElement).value)"
        >
          <option v-for="c in CHANNELS" :key="c" :value="c">{{ c }}</option>
        </select>
      </label>
      <article
        v-for="(trigger, index) in triggers"
        :key="index"
        class="card"
        :data-testid="`notification-trigger-${index}`"
      >
        <div class="row">
          <input
            data-testid="trigger-key"
            :value="String(trigger.triggerKey ?? '')"
            placeholder="triggerKey"
            @change="
              updateArrayItem('triggers', index, {
                triggerKey: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <input
            :value="String(trigger.eventType ?? '')"
            placeholder="eventType"
            @change="
              updateArrayItem('triggers', index, {
                eventType: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <select
            :value="String(trigger.channel ?? 'IN_APP')"
            @change="
              updateArrayItem('triggers', index, {
                channel: ($event.target as HTMLSelectElement).value,
              })
            "
          >
            <option v-for="c in CHANNELS" :key="c" :value="c">{{ c }}</option>
          </select>
          <input
            :value="String(trigger.recipientRole ?? '')"
            placeholder="recipientRole"
            @change="
              updateArrayItem('triggers', index, {
                recipientRole: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <button type="button" @click="removeArrayItem('triggers', index)">删除</button>
        </div>
        <ConditionBuilder
          :model-value="exprSource(trigger)"
          label="触发条件"
          @update:model-value="setItemExpression('triggers', index, 'when', $event)"
        />
      </article>
      <button type="button" data-testid="add-trigger" @click="addTrigger">添加触发器</button>
    </div>

    <div v-else-if="assetType === 'ASSIGNEE_POLICY'" data-testid="assignee-structure-editor">
      <article
        v-for="(strategy, index) in strategies"
        :key="index"
        class="card"
        :data-testid="`assignee-strategy-${index}`"
      >
        <div class="row">
          <input
            data-testid="strategy-key"
            :value="String(strategy.strategyKey ?? '')"
            placeholder="strategyKey"
            @change="
              updateArrayItem('strategies', index, {
                strategyKey: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <input
            type="number"
            :value="Number(strategy.priority ?? 10)"
            placeholder="priority"
            @change="
              updateArrayItem('strategies', index, {
                priority: Number(($event.target as HTMLInputElement).value) || 10,
              })
            "
          />
          <input
            :value="String(strategy.roleCode ?? '')"
            placeholder="roleCode"
            @change="
              updateArrayItem('strategies', index, {
                roleCode: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <button type="button" @click="removeArrayItem('strategies', index)">删除</button>
        </div>
        <ConditionBuilder
          :model-value="exprSource(strategy)"
          label="策略条件"
          @update:model-value="setItemExpression('strategies', index, 'when', $event)"
        />
      </article>
      <button type="button" data-testid="add-strategy" @click="addStrategy">添加策略</button>
    </div>

    <div v-else-if="assetType === 'DISPATCH'" data-testid="dispatch-structure-editor">
      <h4>硬过滤</h4>
      <article
        v-for="(filter, index) in hardFilters"
        :key="`f-${index}`"
        class="card"
        :data-testid="`dispatch-filter-${index}`"
      >
        <div class="row">
          <input
            data-testid="filter-key"
            :value="String(filter.filterKey ?? '')"
            placeholder="filterKey"
            @change="
              updateArrayItem('hardFilters', index, {
                filterKey: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <input
            :value="String(filter.failureCode ?? '')"
            placeholder="failureCode"
            @change="
              updateArrayItem('hardFilters', index, {
                failureCode: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <button type="button" @click="removeArrayItem('hardFilters', index)">删除</button>
        </div>
        <ConditionBuilder
          :model-value="exprSource(filter, 'expression')"
          label="过滤表达式"
          @update:model-value="setItemExpression('hardFilters', index, 'expression', $event)"
        />
      </article>
      <button type="button" data-testid="add-hard-filter" @click="addHardFilter">添加硬过滤</button>

      <h4>评分因子</h4>
      <article
        v-for="(factor, index) in scoring"
        :key="`s-${index}`"
        class="card"
        :data-testid="`dispatch-score-${index}`"
      >
        <div class="row">
          <input
            :value="String(factor.factorKey ?? '')"
            placeholder="factorKey"
            @change="
              updateArrayItem('scoring', index, {
                factorKey: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <input
            type="number"
            step="0.1"
            data-testid="score-weight"
            :value="Number(factor.weight ?? 1)"
            @change="
              updateArrayItem('scoring', index, {
                weight: Number(($event.target as HTMLInputElement).value) || 0,
              })
            "
          />
          <button type="button" @click="removeArrayItem('scoring', index)">删除</button>
        </div>
      </article>
      <button type="button" data-testid="add-scoring" @click="addScoring">添加评分因子</button>
    </div>

    <div v-else-if="assetType === 'INTEGRATION'" data-testid="integration-structure-editor">
      <div class="row">
        <label>
          connectorCode
          <input
            data-testid="integration-connector"
            :value="String(draft.connectorCode ?? '')"
            @change="setRoot('connectorCode', ($event.target as HTMLInputElement).value)"
          />
        </label>
        <label>
          direction
          <select
            data-testid="integration-direction"
            :value="String(draft.direction ?? 'INBOUND')"
            @change="setDirection(($event.target as HTMLSelectElement).value)"
          >
            <option value="INBOUND">INBOUND</option>
            <option value="OUTBOUND">OUTBOUND</option>
          </select>
        </label>
        <label v-if="String(draft.direction ?? 'INBOUND') === 'INBOUND'">
          messageType
          <select
            data-testid="integration-message-type"
            :value="String(draft.messageType ?? 'CREATE_WORK_ORDER')"
            @change="setRoot('messageType', ($event.target as HTMLSelectElement).value)"
          >
            <option v-for="mt in MESSAGE_TYPES" :key="mt" :value="mt">{{ mt }}</option>
          </select>
        </label>
      </div>
      <p class="hint">
        M347：支持 constantValue / defaultValue / enumMap / condition；constantValue 与
        defaultValue/enumMap 互斥。INBOUND 必须选择 messageType。
      </p>
      <article
        v-for="(mapping, index) in fieldMappings"
        :key="index"
        class="card"
        :data-testid="`integration-mapping-${index}`"
      >
        <div class="row">
          <input
            data-testid="mapping-id"
            :value="String(mapping.mappingId ?? '')"
            placeholder="mappingId"
            @change="
              updateArrayItem('fieldMappings', index, {
                mappingId: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <input
            data-testid="mapping-external-path"
            :value="String(mapping.externalPath ?? '')"
            placeholder="externalPath"
            @change="
              updateArrayItem('fieldMappings', index, {
                externalPath: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <input
            data-testid="mapping-internal-path"
            :value="String(mapping.internalPath ?? '')"
            placeholder="internalPath"
            @change="
              updateArrayItem('fieldMappings', index, {
                internalPath: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <select
            data-testid="mapping-transform"
            :value="String(mapping.transform ?? 'TRIM')"
            @change="
              updateArrayItem('fieldMappings', index, {
                transform: ($event.target as HTMLSelectElement).value,
              })
            "
          >
            <option v-for="t in TRANSFORMS" :key="t" :value="t">{{ t }}</option>
          </select>
          <label class="check">
            <input
              type="checkbox"
              data-testid="mapping-required"
              :checked="Boolean(mapping.required)"
              @change="
                updateArrayItem('fieldMappings', index, {
                  required: ($event.target as HTMLInputElement).checked,
                })
              "
            />
            required
          </label>
          <button type="button" @click="removeArrayItem('fieldMappings', index)">删除</button>
        </div>
        <div class="row dsl-row">
          <label>
            constantValue
            <input
              data-testid="mapping-constant-value"
              :value="scalarToInput(mapping.constantValue)"
              placeholder="空=清除；true/false/数字/字符串"
              @change="
                setMappingOptionalScalar(
                  index,
                  'constantValue',
                  ($event.target as HTMLInputElement).value,
                )
              "
            />
          </label>
          <label>
            defaultValue
            <input
              data-testid="mapping-default-value"
              :value="scalarToInput(mapping.defaultValue)"
              placeholder="空=清除"
              @change="
                setMappingOptionalScalar(
                  index,
                  'defaultValue',
                  ($event.target as HTMLInputElement).value,
                )
              "
            />
          </label>
        </div>
        <label>
          enumMap（每行 from=to）
          <textarea
            data-testid="mapping-enum-map"
            rows="2"
            :value="enumMapToText(mapping)"
            spellcheck="false"
            @change="setMappingEnumMap(index, ($event.target as HTMLTextAreaElement).value)"
          />
        </label>
        <div class="condition-box" data-testid="mapping-condition">
          <div class="row">
            <strong>condition</strong>
            <button
              v-if="!mappingCondition(mapping)"
              type="button"
              data-testid="add-mapping-condition"
              @click="
                setMappingCondition(index, {
                  sourcePath: String(mapping.externalPath ?? 'external.field'),
                  operator: 'PRESENT',
                })
              "
            >
              添加条件
            </button>
            <button
              v-else
              type="button"
              data-testid="clear-mapping-condition"
              @click="setMappingCondition(index, null)"
            >
              清除条件
            </button>
          </div>
          <div v-if="mappingCondition(mapping)" class="row">
            <input
              data-testid="condition-source-path"
              :value="String(mappingCondition(mapping)!.sourcePath ?? '')"
              placeholder="sourcePath"
              @change="
                updateMappingCondition(index, {
                  sourcePath: ($event.target as HTMLInputElement).value,
                })
              "
            />
            <select
              data-testid="condition-operator"
              :value="String(mappingCondition(mapping)!.operator ?? 'PRESENT')"
              @change="
                updateMappingCondition(index, {
                  operator: ($event.target as HTMLSelectElement).value,
                })
              "
            >
              <option v-for="op in CONDITION_OPS" :key="op" :value="op">{{ op }}</option>
            </select>
            <input
              v-if="['EQUALS', 'NOT_EQUALS'].includes(String(mappingCondition(mapping)!.operator))"
              data-testid="condition-value"
              :value="scalarToInput(mappingCondition(mapping)!.value)"
              placeholder="value"
              @change="
                updateMappingCondition(index, {
                  value: parseScalar(($event.target as HTMLInputElement).value),
                })
              "
            />
            <input
              v-if="['IN', 'NOT_IN'].includes(String(mappingCondition(mapping)!.operator))"
              data-testid="condition-values"
              :value="
                Array.isArray(mappingCondition(mapping)!.values)
                  ? (mappingCondition(mapping)!.values as unknown[]).join(',')
                  : ''
              "
              placeholder="values 逗号分隔"
              @change="
                updateMappingCondition(index, {
                  values: ($event.target as HTMLInputElement).value
                    .split(',')
                    .map((s) => s.trim())
                    .filter(Boolean)
                    .map(parseScalar),
                })
              "
            />
          </div>
        </div>
      </article>
      <button type="button" data-testid="add-field-mapping" @click="addFieldMapping">
        添加字段映射
      </button>
    </div>

    <div v-else-if="assetType === 'PRICING'" data-testid="pricing-structure-editor">
      <label>
        currency
        <input
          data-testid="pricing-currency"
          :value="String(draft.currency ?? 'CNY')"
          maxlength="3"
          @change="setRoot('currency', ($event.target as HTMLInputElement).value.toUpperCase())"
        />
      </label>
      <article
        v-for="(line, index) in lines"
        :key="index"
        class="card"
        :data-testid="`pricing-line-${index}`"
      >
        <div class="row">
          <input
            data-testid="pricing-line-key"
            :value="String(line.lineKey ?? '')"
            placeholder="lineKey"
            @change="
              updateArrayItem('lines', index, {
                lineKey: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <input
            :value="String(line.chargeCode ?? '')"
            placeholder="chargeCode"
            @change="
              updateArrayItem('lines', index, {
                chargeCode: ($event.target as HTMLInputElement).value,
              })
            "
          />
          <input
            type="number"
            min="0"
            data-testid="pricing-amount"
            :value="Number(line.amountMinor ?? 0)"
            @change="
              updateArrayItem('lines', index, {
                amountMinor: Number(($event.target as HTMLInputElement).value) || 0,
              })
            "
          />
          <select
            :value="String(line.billableTo ?? 'OEM')"
            @change="
              updateArrayItem('lines', index, {
                billableTo: ($event.target as HTMLSelectElement).value,
              })
            "
          >
            <option v-for="b in BILLABLE" :key="b" :value="b">{{ b }}</option>
          </select>
          <button type="button" @click="removeArrayItem('lines', index)">删除</button>
        </div>
        <ConditionBuilder
          :model-value="exprSource(line)"
          label="计费条件"
          @update:model-value="setItemExpression('lines', index, 'when', $event)"
        />
      </article>
      <button type="button" data-testid="add-pricing-line" @click="addPricingLine">
        添加计费行
      </button>
    </div>
  </section>
</template>

<style scoped>
.policy-editor {
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
.row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  align-items: center;
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
h4 {
  margin: 0.25rem 0 0;
  font-size: 0.95rem;
}
input,
select,
button,
textarea {
  font: inherit;
}
.dsl-row label {
  flex: 1;
  min-width: 12rem;
}
.condition-box {
  border-top: 1px dashed #d0d7de;
  padding-top: 0.4rem;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
textarea {
  width: 100%;
  box-sizing: border-box;
}
</style>
