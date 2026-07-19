/**
 * SERVICEOS_EXPR_V1 条件积木：字段/运算符/值/AND-OR 组 → 合法表达式源码。
 * 路径白名单与后端 ServiceOsExprV1Evaluator 对齐；M340 formValues；M342 嵌套括号；M345 一元 !。
 */

export const EXPR_PATHS = [
  'workOrder.clientCode',
  'workOrder.brandCode',
  'workOrder.serviceProductCode',
  'region.provinceCode',
  'region.cityCode',
  'region.districtCode',
  'task.stageCode',
  'task.taskType',
] as const

export type ContextPath = (typeof EXPR_PATHS)[number]
/** 兼容旧调用：上下文白名单路径。 */
export type ExprPath = ContextPath
export type CompareOp = '==' | '!='
export type JoinOp = 'AND' | 'OR'
export type LiteralKind = 'string' | 'boolean' | 'number'

const FORM_VALUE_PATH_RE = /^formValues\["([^"\\]+)"\]$/
const CONTEXT_PATH_RE =
  /^(workOrder\.(?:clientCode|brandCode|serviceProductCode)|region\.(?:provinceCode|cityCode|districtCode)|task\.(?:stageCode|taskType))$/
const ATOM_RE =
  /^(formValues\["([^"\\]+)"\]|workOrder\.(?:clientCode|brandCode|serviceProductCode)|region\.(?:provinceCode|cityCode|districtCode)|task\.(?:stageCode|taskType))\s*(==|!=)\s*(true|false|-?\d+(?:\.\d+)?|"([^"\\]*)")$/

export interface ConditionAtom {
  kind: 'atom'
  /** 上下文白名单路径，或 formValues["fieldKey"] */
  path: string
  op: CompareOp
  /** 字面量原始文本（布尔/数字不含引号；字符串不含引号） */
  value: string
  valueKind: LiteralKind
}

export interface ConditionGroup {
  kind: 'group'
  join: JoinOp
  children: ConditionNode[]
}

/** 一元取反；对齐后端 parseNot。 */
export interface ConditionNot {
  kind: 'not'
  child: ConditionNode
}

export type ConditionNode = ConditionAtom | ConditionGroup | ConditionNot

export function formValuesPath(fieldKey: string): string {
  if (!fieldKey || fieldKey.includes('"') || fieldKey.includes('\\')) {
    throw new Error('formValues 字段键不得为空或包含引号/反斜杠')
  }
  return `formValues["${fieldKey}"]`
}

export function isFormValuesPath(path: string): boolean {
  return FORM_VALUE_PATH_RE.test(path)
}

export function formFieldKeyFromPath(path: string): string | null {
  const match = path.match(FORM_VALUE_PATH_RE)
  return match ? match[1] : null
}

export function isAllowedPath(path: string): boolean {
  return CONTEXT_PATH_RE.test(path) || FORM_VALUE_PATH_RE.test(path)
}

/** 供 UI 下拉：上下文路径 + 当前表单字段对应的 formValues 路径。 */
export function availablePaths(formFieldKeys: readonly string[] = []): string[] {
  const formPaths: string[] = []
  for (const key of formFieldKeys) {
    if (!key || key.includes('"') || key.includes('\\')) {
      continue
    }
    formPaths.push(formValuesPath(key))
  }
  return [...EXPR_PATHS, ...formPaths]
}

export function emptyAtom(path: string = 'workOrder.brandCode'): ConditionAtom {
  return {
    kind: 'atom',
    path,
    op: '==',
    value: '',
    valueKind: isFormValuesPath(path) ? 'boolean' : 'string',
  }
}

export function emptyGroup(join: JoinOp = 'AND'): ConditionGroup {
  return { kind: 'group', join, children: [emptyAtom()] }
}

/** 将积木树编译为 SERVICEOS_EXPR_V1 源码；失败关闭。 */
export function compileCondition(node: ConditionNode): string {
  const source = compileNode(node)
  if (source.length > 2000) {
    throw new Error('表达式超过 2000 字符上限')
  }
  return source
}

function compileNode(node: ConditionNode): string {
  if (node.kind === 'atom') {
    return compileAtom(node)
  }
  if (node.kind === 'not') {
    const inner = compileNode(node.child)
    // 原子比较可写成 !path == "v"（与后端 parseNot→parseComparison 一致）；组/嵌套取反需括号。
    if (node.child.kind === 'atom') {
      return `!${inner}`
    }
    return `!(${inner})`
  }
  if (node.children.length === 0) {
    throw new Error('条件组不能为空')
  }
  const parts = node.children.map((child) => {
    const compiled = compileNode(child)
    return child.kind === 'group' ? `(${compiled})` : compiled
  })
  const joiner = node.join === 'AND' ? ' && ' : ' || '
  return parts.join(joiner)
}

function compileAtom(atom: ConditionAtom): string {
  if (!isAllowedPath(atom.path)) {
    throw new Error(`路径不在白名单: ${atom.path}`)
  }
  if (atom.op !== '==' && atom.op !== '!=') {
    throw new Error(`不支持的运算符: ${atom.op}`)
  }
  return `${atom.path} ${atom.op} ${compileLiteral(atom)}`
}

function compileLiteral(atom: ConditionAtom): string {
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

/**
 * 尽力解析比较、AND/OR、嵌套括号与一元 !。
 * 无法解析时返回 null，调用方保留高级源码编辑。
 */
export function tryParseCondition(source: string): ConditionGroup | null {
  const trimmed = source.trim()
  if (!trimmed) {
    return emptyGroup()
  }
  try {
    const parser = new ExprParser(trimmed)
    const node = parser.parseOr()
    parser.expectEnd()
    return asRootGroup(node)
  } catch {
    return null
  }
}

function asRootGroup(node: ConditionNode): ConditionGroup {
  if (node.kind === 'group') {
    return node
  }
  return { kind: 'group', join: 'AND', children: [node] }
}

/**
 * 递归下降：or → and → not → primary；primary 为原子或括号组。
 * formValues["…"] 中的方括号不参与分组；字符串内括号忽略。
 */
class ExprParser {
  private readonly input: string
  private index = 0

  constructor(input: string) {
    this.input = input
  }

  parseOr(): ConditionNode {
    const children: ConditionNode[] = [this.parseAnd()]
    while (this.match('||')) {
      children.push(this.parseAnd())
    }
    if (children.length === 1) {
      return children[0]
    }
    return { kind: 'group', join: 'OR', children }
  }

  parseAnd(): ConditionNode {
    const children: ConditionNode[] = [this.parseNot()]
    while (this.match('&&')) {
      children.push(this.parseNot())
    }
    if (children.length === 1) {
      return children[0]
    }
    return { kind: 'group', join: 'AND', children }
  }

  parseNot(): ConditionNode {
    this.skipWhitespace()
    // 不得把比较运算符 != 的前缀 ! 当成一元取反
    if (this.input.startsWith('!=', this.index)) {
      return this.parsePrimary()
    }
    if (this.match('!')) {
      return { kind: 'not', child: this.parseNot() }
    }
    return this.parsePrimary()
  }

  parsePrimary(): ConditionNode {
    this.skipWhitespace()
    if (this.match('(')) {
      const inner = this.parseOr()
      if (!this.match(')')) {
        throw new Error('缺少右括号')
      }
      // 括号强制成组，便于 UI 与编译对称保留嵌套
      if (inner.kind === 'group') {
        return inner
      }
      return { kind: 'group', join: 'AND', children: [inner] }
    }
    const atom = this.parseAtom()
    if (!atom) {
      throw new Error('期望比较原子或括号组')
    }
    return atom
  }

  parseAtom(): ConditionAtom | null {
    this.skipWhitespace()
    const start = this.index
    // 扫描到 && / || / ) / 末尾，但不拆开 formValues["…"] 与字符串
    while (this.index < this.input.length) {
      const ch = this.input[this.index]
      if (ch === '"' ) {
        this.index++
        while (this.index < this.input.length && this.input[this.index] !== '"') {
          this.index++
        }
        if (this.index < this.input.length) {
          this.index++
        }
        continue
      }
      if (ch === ')') {
        break
      }
      if (this.input.startsWith('&&', this.index) || this.input.startsWith('||', this.index)) {
        break
      }
      this.index++
    }
    const raw = this.input.slice(start, this.index).trim()
    if (!raw) {
      return null
    }
    return parseAtomSource(raw)
  }

  expectEnd() {
    this.skipWhitespace()
    if (this.index < this.input.length) {
      throw new Error('表达式末尾存在多余内容')
    }
  }

  private match(token: string): boolean {
    this.skipWhitespace()
    if (this.input.startsWith(token, this.index)) {
      this.index += token.length
      return true
    }
    return false
  }

  private skipWhitespace() {
    while (this.index < this.input.length && /\s/.test(this.input[this.index])) {
      this.index++
    }
  }
}

function parseAtomSource(source: string): ConditionAtom | null {
  const match = source.match(ATOM_RE)
  if (!match) {
    return null
  }
  const path = match[1]
  const op = match[3] as CompareOp
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

/** 与后端校验器对齐的轻量静态检查（前端即时反馈）。 */
export function validateCompiledSource(source: string): string[] {
  const errors: string[] = []
  if (!source.trim()) {
    errors.push('表达式不能为空')
    return errors
  }
  if (source.length > 2000) {
    errors.push('表达式超过 2000 字符上限')
  }
  try {
    const parsed = tryParseCondition(source)
    if (parsed) {
      compileCondition(parsed)
    } else {
      errors.push('表达式无法解析为条件积木')
    }
  } catch (err) {
    errors.push(err instanceof Error ? err.message : '表达式无效')
  }
  return errors
}
