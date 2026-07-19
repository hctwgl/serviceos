/**
 * SERVICEOS_EXPR_V1 条件积木：字段/运算符/值/AND-OR 组 → 合法表达式源码。
 * 路径白名单与后端 ServiceOsExprV1Evaluator 对齐。
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

export type ExprPath = (typeof EXPR_PATHS)[number]
export type CompareOp = '==' | '!='
export type JoinOp = 'AND' | 'OR'

export interface ConditionAtom {
  kind: 'atom'
  path: ExprPath
  op: CompareOp
  /** 字面量字符串值（不含引号） */
  value: string
}

export interface ConditionGroup {
  kind: 'group'
  join: JoinOp
  children: ConditionNode[]
}

export type ConditionNode = ConditionAtom | ConditionGroup

export function emptyAtom(path: ExprPath = 'workOrder.brandCode'): ConditionAtom {
  return { kind: 'atom', path, op: '==', value: '' }
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
  if (!EXPR_PATHS.includes(atom.path)) {
    throw new Error(`路径不在白名单: ${atom.path}`)
  }
  if (atom.op !== '==' && atom.op !== '!=') {
    throw new Error(`不支持的运算符: ${atom.op}`)
  }
  if (atom.value.includes('"') || atom.value.includes('\\')) {
    throw new Error('字面量不得包含引号或反斜杠')
  }
  return `${atom.path} ${atom.op} "${atom.value}"`
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
  const match = source.match(
    /^(workOrder\.(?:clientCode|brandCode|serviceProductCode)|region\.(?:provinceCode|cityCode|districtCode)|task\.(?:stageCode|taskType))\s*(==|!=)\s*"([^"\\]*)"$/,
  )
  if (!match) {
    return null
  }
  return {
    kind: 'atom',
    path: match[1] as ExprPath,
    op: match[2] as CompareOp,
    value: match[3],
  }
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
