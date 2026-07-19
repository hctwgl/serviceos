/**
 * SERVICEOS_EXPR_V1 条件积木：字段/运算符/值/AND-OR 组 → 合法表达式源码。
 * 路径白名单与后端 ServiceOsExprV1Evaluator 对齐；M340 起支持 formValues["fieldKey"]。
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

export type ConditionNode = ConditionAtom | ConditionGroup

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
 * 尽力解析简单比较与顶层 AND/OR（不含嵌套括号混合）。
 * 无法解析时返回 null，调用方保留高级源码编辑。
 */
export function tryParseCondition(source: string): ConditionGroup | null {
  const trimmed = source.trim()
  if (!trimmed) {
    return emptyGroup()
  }
  // 仅支持不含括号的平面 AND/OR 或单个比较
  if (trimmed.includes('(') || trimmed.includes(')')) {
    return null
  }
  let join: JoinOp = 'AND'
  let parts: string[]
  if (trimmed.includes('||')) {
    join = 'OR'
    parts = trimmed.split('||').map((p) => p.trim())
  } else if (trimmed.includes('&&')) {
    join = 'AND'
    parts = trimmed.split('&&').map((p) => p.trim())
  } else {
    parts = [trimmed]
  }
  const children: ConditionAtom[] = []
  for (const part of parts) {
    const atom = parseAtom(part)
    if (!atom) {
      return null
    }
    children.push(atom)
  }
  return { kind: 'group', join, children }
}

function parseAtom(source: string): ConditionAtom | null {
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
  // 带引号字符串：捕获组 5 为去引号内容
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
    }
  } catch (err) {
    errors.push(err instanceof Error ? err.message : '表达式无效')
  }
  return errors
}
