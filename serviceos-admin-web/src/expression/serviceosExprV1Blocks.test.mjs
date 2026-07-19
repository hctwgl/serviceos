/**
 * Node 原生冒烟：验证条件积木编译/解析（不依赖 vitest）。
 * 运行：node serviceos-admin-web/src/expression/serviceosExprV1Blocks.test.mjs
 */
import assert from 'node:assert/strict'
import { pathToFileURL } from 'node:url'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))

// 优先动态加载 TS（Node strip-types）；失败则使用内联同源实现。
let api
try {
  api = await import(pathToFileURL(join(__dirname, 'serviceosExprV1Blocks.ts')).href)
} catch {
  api = null
}

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

function parseAtomSource(source) {
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

class ExprParser {
  constructor(input) {
    this.input = input
    this.index = 0
  }
  parseOr() {
    const children = [this.parseAnd()]
    while (this.match('||')) children.push(this.parseAnd())
    return children.length === 1 ? children[0] : { kind: 'group', join: 'OR', children }
  }
  parseAnd() {
    const children = [this.parsePrimary()]
    while (this.match('&&')) children.push(this.parsePrimary())
    return children.length === 1 ? children[0] : { kind: 'group', join: 'AND', children }
  }
  parsePrimary() {
    this.skipWhitespace()
    if (this.match('(')) {
      const inner = this.parseOr()
      if (!this.match(')')) throw new Error('缺少右括号')
      return inner.kind === 'group' ? inner : { kind: 'group', join: 'AND', children: [inner] }
    }
    const atom = this.parseAtom()
    if (!atom) throw new Error('期望比较原子或括号组')
    return atom
  }
  parseAtom() {
    this.skipWhitespace()
    const start = this.index
    while (this.index < this.input.length) {
      const ch = this.input[this.index]
      if (ch === '"') {
        this.index++
        while (this.index < this.input.length && this.input[this.index] !== '"') this.index++
        if (this.index < this.input.length) this.index++
        continue
      }
      if (ch === ')') break
      if (this.input.startsWith('&&', this.index) || this.input.startsWith('||', this.index)) break
      this.index++
    }
    const raw = this.input.slice(start, this.index).trim()
    return raw ? parseAtomSource(raw) : null
  }
  expectEnd() {
    this.skipWhitespace()
    if (this.index < this.input.length) throw new Error('多余内容')
  }
  match(token) {
    this.skipWhitespace()
    if (this.input.startsWith(token, this.index)) {
      this.index += token.length
      return true
    }
    return false
  }
  skipWhitespace() {
    while (this.index < this.input.length && /\s/.test(this.input[this.index])) this.index++
  }
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
  try {
    const parser = new ExprParser(trimmed)
    const node = parser.parseOr()
    parser.expectEnd()
    return node.kind === 'group' ? node : { kind: 'group', join: 'AND', children: [node] }
  } catch {
    return null
  }
}

function formValuesPath(fieldKey) {
  return `formValues["${fieldKey}"]`
}

function availablePaths(formFieldKeys = []) {
  return [...EXPR_PATHS, ...formFieldKeys.map(formValuesPath)]
}

const impl = api ?? {
  compileCondition,
  tryParseCondition,
  formValuesPath,
  availablePaths,
}

const compiled = impl.compileCondition({
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

const parsed = impl.tryParseCondition(compiled)
assert.ok(parsed)
assert.equal(parsed.join, 'AND')
assert.equal(parsed.children.length, 2)
assert.equal(impl.compileCondition(parsed), compiled)

// M342: 嵌套括号 round-trip
const nestedSource =
  '(workOrder.brandCode == "BYD_OCEAN" && region.provinceCode == "110000") || task.stageCode == "SURVEY"'
const nestedParsed = impl.tryParseCondition(nestedSource)
assert.ok(nestedParsed, 'nested paren must parse')
assert.equal(nestedParsed.join, 'OR')
assert.equal(nestedParsed.children.length, 2)
assert.equal(nestedParsed.children[0].kind, 'group')
assert.equal(nestedParsed.children[0].join, 'AND')
assert.equal(impl.compileCondition(nestedParsed), nestedSource)

const deep =
  '((formValues["needs-photo"] == true) || formValues["pole.height-mm"] == 1800) && task.taskType == "SURVEY"'
const deepParsed = impl.tryParseCondition(deep)
assert.ok(deepParsed)
assert.equal(impl.compileCondition(deepParsed), deep)

// 一元 ! 仍失败关闭到高级源码
assert.equal(impl.tryParseCondition('!(workOrder.brandCode == "X")'), null)

assert.throws(() =>
  impl.compileCondition({
    kind: 'atom',
    path: 'workOrder.hack',
    op: '==',
    value: 'x',
    valueKind: 'string',
  }),
)

const formExpr = impl.compileCondition({
  kind: 'group',
  join: 'AND',
  children: [
    {
      kind: 'atom',
      path: impl.formValuesPath('needs-photo'),
      op: '==',
      value: 'true',
      valueKind: 'boolean',
    },
    {
      kind: 'atom',
      path: impl.formValuesPath('pole.height-mm'),
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
assert.equal(impl.compileCondition(impl.tryParseCondition(formExpr)), formExpr)

const mixed = impl.tryParseCondition(
  'task.stageCode == "SURVEY" || formValues["site.has-parking-space"] == true',
)
assert.ok(mixed)
assert.equal(mixed.join, 'OR')

const paths = impl.availablePaths(['needs-photo', 'site.has-parking-space'])
assert.ok(paths.includes('formValues["needs-photo"]'))

console.log('serviceosExprV1Blocks.test.mjs OK')
