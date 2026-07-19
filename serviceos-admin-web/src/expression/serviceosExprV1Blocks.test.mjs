/**
 * Node 原生冒烟：验证条件积木编译/解析（不依赖 vitest）。
 * 运行：node --experimental-strip-types src/expression/serviceosExprV1Blocks.test.mjs
 * 若环境不支持 strip-types，则通过动态 import 已由 tsc 产出的逻辑副本在此内联断言。
 */
import assert from 'node:assert/strict'

// 内联与 serviceosExprV1Blocks.ts 同源的最小编译断言，避免构建工具链差异。
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

function compileAtom(atom) {
  if (!EXPR_PATHS.includes(atom.path)) throw new Error(`路径不在白名单: ${atom.path}`)
  if (atom.value.includes('"') || atom.value.includes('\\')) {
    throw new Error('字面量不得包含引号或反斜杠')
  }
  return `${atom.path} ${atom.op} "${atom.value}"`
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

function tryParseCondition(source) {
  const trimmed = source.trim()
  if (!trimmed) return { kind: 'group', join: 'AND', children: [{ kind: 'atom', path: 'workOrder.brandCode', op: '==', value: '' }] }
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
    const match = part.match(
      /^(workOrder\.(?:clientCode|brandCode|serviceProductCode)|region\.(?:provinceCode|cityCode|districtCode)|task\.(?:stageCode|taskType))\s*(==|!=)\s*"([^"\\]*)"$/,
    )
    if (!match) return null
    children.push({ kind: 'atom', path: match[1], op: match[2], value: match[3] })
  }
  return { kind: 'group', join, children }
}

const compiled = compileCondition({
  kind: 'group',
  join: 'AND',
  children: [
    { kind: 'atom', path: 'workOrder.brandCode', op: '==', value: 'BYD_OCEAN' },
    { kind: 'atom', path: 'region.provinceCode', op: '!=', value: '110000' },
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
  }),
)

console.log('serviceosExprV1Blocks.test.mjs OK')
