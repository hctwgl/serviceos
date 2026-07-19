/**
 * Node 原生冒烟：验证条件积木编译/解析（不依赖 vitest）。
 * 运行：node serviceos-admin-web/src/expression/serviceosExprV1Blocks.test.mjs
 */
import assert from 'node:assert/strict'

const EXPR_PATHS = [
  'workOrder.clientCode',
  'workOrder.brandCode',
  'workOrder.serviceProductCode',
  'region.provinceCode',
  'region.cityCode',
  'region.districtCode',
  'task.stageCode',
  'task.taskType',
]

const FORM_VALUE_PATH_RE = /^formValues\["([^"\\]+)"\]$/
const CONTEXT_PATH_RE =
  /^(workOrder\.(?:clientCode|brandCode|serviceProductCode)|region\.(?:provinceCode|cityCode|districtCode)|task\.(?:stageCode|taskType))$/
const ATOM_RE =
  /^(formValues\["([^"\\]+)"\]|workOrder\.(?:clientCode|brandCode|serviceProductCode)|region\.(?:provinceCode|cityCode|districtCode)|task\.(?:stageCode|taskType))\s*(==|!=)\s*(true|false|-?\d+(?:\.\d+)?|"([^"\\]*)")$/

function isAllowedPath(path) {
  return CONTEXT_PATH_RE.test(path) || FORM_VALUE_PATH_RE.test(path)
}

function compileLiteral(atom) {
  const kind = atom.valueKind ?? 'string'
  if (kind === 'boolean') {
    if (atom.value !== 'true' && atom.value !== 'false') {
      throw new Error('布尔字面量必须为 true 或 false')
    }
    return atom.value
  }
  if (kind === 'number') {
    if (!/^-?\d+(\.\d+)?$/.test(atom.value)) {
      throw new Error('数值字面量格式无效')
    }
    return atom.value
  }
  if (atom.value.includes('"') || atom.value.includes('\\')) {
    throw new Error('字面量不得包含引号或反斜杠')
  }
  return `"${atom.value}"`
}

function compileAtom(atom) {
  if (!isAllowedPath(atom.path)) throw new Error(`路径不在白名单: ${atom.path}`)
  return `${atom.path} ${atom.op} ${compileLiteral(atom)}`
}

function compileCondition(node) {
  if (node.kind === 'atom') return compileAtom(node)
  if (!node.children.length) throw new Error('条件组不能为空')
  const parts = node.children.map((child) => {
    const compiled = compileCondition(child)
    return child.kind === 'group' ? `(${compiled})` : compiled
  })
  return parts.join(node.join === 'AND' ? ' && ' : ' || ')
}

function parseAtom(source) {
  const match = source.match(ATOM_RE)
  if (!match) return null
  const path = match[1]
  const op = match[3]
  const rawRight = match[4]
  if (rawRight === 'true' || rawRight === 'false') {
    return { kind: 'atom', path, op, value: rawRight, valueKind: 'boolean' }
  }
  if (/^-?\d+(?:\.\d+)?$/.test(rawRight)) {
    return { kind: 'atom', path, op, value: rawRight, valueKind: 'number' }
  }
  if (match[5] !== undefined) {
    return { kind: 'atom', path, op, value: match[5], valueKind: 'string' }
  }
  return null
}

function tryParseCondition(source) {
  const trimmed = source.trim()
  if (!trimmed) {
    return {
      kind: 'group',
      join: 'AND',
      children: [{ kind: 'atom', path: 'workOrder.brandCode', op: '==', value: '', valueKind: 'string' }],
    }
  }
  if (trimmed.includes('(') || trimmed.includes(')')) return null
  let join = 'AND'
  let parts
  if (trimmed.includes('||')) {
    join = 'OR'
    parts = trimmed.split('||').map((p) => p.trim())
  } else if (trimmed.includes('&&')) {
    join = 'AND'
    parts = trimmed.split('&&').map((p) => p.trim())
  } else {
    parts = [trimmed]
  }
  const children = []
  for (const part of parts) {
    const atom = parseAtom(part)
    if (!atom) return null
    children.push(atom)
  }
  return { kind: 'group', join, children }
}

function formValuesPath(fieldKey) {
  return `formValues["${fieldKey}"]`
}

function availablePaths(formFieldKeys = []) {
  return [...EXPR_PATHS, ...formFieldKeys.map(formValuesPath)]
}

const compiled = compileCondition({
  kind: 'group',
  join: 'AND',
  children: [
    { kind: 'atom', path: 'workOrder.brandCode', op: '==', value: 'BYD_OCEAN', valueKind: 'string' },
    { kind: 'atom', path: 'region.provinceCode', op: '!=', value: '110000', valueKind: 'string' },
  ],
})
assert.equal(
  compiled,
  'workOrder.brandCode == "BYD_OCEAN" && region.provinceCode != "110000"',
)

const parsed = tryParseCondition(compiled)
assert.ok(parsed)
assert.equal(parsed.join, 'AND')
assert.equal(parsed.children.length, 2)
assert.equal(compileCondition(parsed), compiled)

assert.equal(tryParseCondition('(workOrder.brandCode == "X")'), null)

assert.throws(() =>
  compileCondition({
    kind: 'atom',
    path: 'workOrder.hack',
    op: '==',
    value: 'x',
    valueKind: 'string',
  }),
)

// M340: formValues + 布尔/数值字面量
const formExpr = compileCondition({
  kind: 'group',
  join: 'AND',
  children: [
    {
      kind: 'atom',
      path: formValuesPath('needs-photo'),
      op: '==',
      value: 'true',
      valueKind: 'boolean',
    },
    {
      kind: 'atom',
      path: formValuesPath('pole.height-mm'),
      op: '==',
      value: '1800',
      valueKind: 'number',
    },
  ],
})
assert.equal(
  formExpr,
  'formValues["needs-photo"] == true && formValues["pole.height-mm"] == 1800',
)
const formParsed = tryParseCondition(formExpr)
assert.ok(formParsed)
assert.equal(compileCondition(formParsed), formExpr)
assert.equal(formParsed.children[0].valueKind, 'boolean')
assert.equal(formParsed.children[1].valueKind, 'number')

const mixed = tryParseCondition(
  'task.stageCode == "SURVEY" || formValues["site.has-parking-space"] == true',
)
assert.ok(mixed)
assert.equal(mixed.join, 'OR')
assert.equal(mixed.children[1].path, 'formValues["site.has-parking-space"]')

const paths = availablePaths(['needs-photo', 'site.has-parking-space'])
assert.ok(paths.includes('formValues["needs-photo"]'))
assert.ok(paths.includes('workOrder.brandCode'))

assert.throws(() =>
  compileCondition({
    kind: 'atom',
    path: 'formValues["x"]',
    op: '==',
    value: 'maybe',
    valueKind: 'boolean',
  }),
)

console.log('serviceosExprV1Blocks.test.mjs OK')
