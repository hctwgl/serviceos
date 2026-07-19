/**
 * SERVICEOS_EXPR_V1 运行时布尔子集（Technician Web）。
 * 与后端 ServiceOsExprV1Evaluator 语法对齐：==/!=/&&/||/!/()、白名单路径、formValues["…"]。
 * 失败关闭：解析错误、类型不匹配、缺少权威上下文均抛错，由调用方阻断提交。
 */

const MAX_SOURCE_LENGTH = 2_000
const MAX_NESTING_DEPTH = 64
const MAX_OPERATOR_COUNT = 256

const ALLOWED_PATHS = new Set([
  'workOrder.clientCode',
  'workOrder.brandCode',
  'workOrder.serviceProductCode',
  'region.provinceCode',
  'region.cityCode',
  'region.districtCode',
  'task.stageCode',
  'task.taskType',
])

export type ExprContext = {
  formValues: Record<string, unknown>
  paths: Partial<Record<string, string>>
}

type ValueType = 'BOOLEAN' | 'STRING' | 'INTEGER' | 'DECIMAL'

type ExprValue = { value: unknown; type: ValueType }

export class ExpressionEvaluationError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'ExpressionEvaluationError'
  }
}

export function expressionSource(raw: unknown): string | null {
  if (!raw || typeof raw !== 'object') return null
  const language = (raw as { language?: unknown }).language
  const source = (raw as { source?: unknown }).source
  if (language !== 'SERVICEOS_EXPR_V1' || typeof source !== 'string') {
    return null
  }
  return source
}

export function evaluateServiceOsExprV1(source: string, context: ExprContext): boolean {
  if (source.length > MAX_SOURCE_LENGTH) {
    throw new ExpressionEvaluationError(
      `表达式长度超过 SERVICEOS_EXPR_V1 上限: ${source.length}`,
    )
  }
  const parser = new Parser(source, context)
  const result = parser.parseOr()
  parser.expectEnd()
  return result
}

/** 将草稿输入转为可比较标量；空数字字段视为缺失（失败关闭）。 */
export function coerceFormValuesForExpr(
  fields: Array<{ fieldKey: string; dataType: string }>,
  draft: Record<string, string | boolean>,
): { values: Record<string, unknown>; missingKeys: string[] } {
  const values: Record<string, unknown> = {}
  const missingKeys: string[] = []
  for (const field of fields) {
    const raw = draft[field.fieldKey]
    if (field.dataType === 'BOOLEAN') {
      values[field.fieldKey] = Boolean(raw)
      continue
    }
    const text = typeof raw === 'string' ? raw.trim() : ''
    if (field.dataType === 'INTEGER') {
      if (!text) {
        missingKeys.push(field.fieldKey)
        continue
      }
      if (!/^-?\d+$/.test(text)) {
        throw new ExpressionEvaluationError(`表单字段不是整数: ${field.fieldKey}`)
      }
      values[field.fieldKey] = Number(text)
      continue
    }
    if (field.dataType === 'DECIMAL') {
      if (!text) {
        missingKeys.push(field.fieldKey)
        continue
      }
      const number = Number(text)
      if (!Number.isFinite(number)) {
        throw new ExpressionEvaluationError(`表单字段不是数字: ${field.fieldKey}`)
      }
      values[field.fieldKey] = number
      continue
    }
    values[field.fieldKey] = text
  }
  return { values, missingKeys }
}

class Parser {
  private index = 0
  private nestingDepth = 0
  private operatorCount = 0
  private readonly input: string
  private readonly context: ExprContext

  constructor(input: string, context: ExprContext) {
    this.input = input
    this.context = context
  }

  parseOr(): boolean {
    let value = this.parseAnd()
    while (this.match('||')) {
      this.countOperator()
      const right = this.parseAnd()
      value = value || right
    }
    return value
  }

  private parseAnd(): boolean {
    let value = this.parseNot()
    while (this.match('&&')) {
      this.countOperator()
      const right = this.parseNot()
      value = value && right
    }
    return value
  }

  private parseNot(): boolean {
    if (this.match('!')) {
      this.countOperator()
      this.enterNesting()
      try {
        return !this.parseNot()
      } finally {
        this.leaveNesting()
      }
    }
    return this.parsePrimary()
  }

  private parsePrimary(): boolean {
    this.skipWhitespace()
    if (this.match('(')) {
      this.enterNesting()
      try {
        const value = this.parseOr()
        if (!this.match(')')) {
          throw this.error("缺少右括号 ')'")
        }
        return value
      } finally {
        this.leaveNesting()
      }
    }
    return this.parseComparison()
  }

  private parseComparison(): boolean {
    const left = this.parseValue()
    this.skipWhitespace()
    if (this.match('==')) {
      this.countOperator()
      const right = this.parseValue()
      return this.compareEquals(left, right)
    }
    if (this.match('!=')) {
      this.countOperator()
      const right = this.parseValue()
      return !this.compareEquals(left, right)
    }
    if (left.type === 'BOOLEAN') {
      return Boolean(left.value)
    }
    throw this.error('独立值必须是布尔字面量')
  }

  private compareEquals(left: ExprValue, right: ExprValue): boolean {
    if (left.type !== right.type) {
      throw new ExpressionEvaluationError('表达式比较两侧必须是相同类型')
    }
    if (left.type === 'INTEGER' || left.type === 'DECIMAL') {
      return Number(left.value) === Number(right.value)
    }
    return left.value === right.value
  }

  private parseValue(): ExprValue {
    this.skipWhitespace()
    if (this.matchKeyword('true')) return { value: true, type: 'BOOLEAN' }
    if (this.matchKeyword('false')) return { value: false, type: 'BOOLEAN' }
    if (this.peek() === '"') return { value: this.readString(), type: 'STRING' }
    if (this.peek() === '-' || this.isDigit(this.peek())) return this.readNumber()
    if (this.input.startsWith('formValues', this.index)) return this.readFormValue()
    return this.readPath()
  }

  private readPath(): ExprValue {
    this.skipWhitespace()
    const start = this.index
    if (!this.isIdentifierStart(this.peek())) {
      throw this.error('此处需要白名单路径、字符串或布尔字面量')
    }
    this.index++
    while (this.isIdentifierPart(this.peek()) || this.peek() === '.') {
      this.index++
    }
    const path = this.input.slice(start, this.index).trim()
    if (!ALLOWED_PATHS.has(path)) {
      throw new ExpressionEvaluationError(`表达式路径不在白名单中: ${path}`)
    }
    const value = this.context.paths[path]
    if (value == null || value === '') {
      throw new ExpressionEvaluationError(`表达式上下文缺少权威值: ${path}`)
    }
    return { value, type: 'STRING' }
  }

  private readFormValue(): ExprValue {
    this.match('formValues')
    if (!this.match('[')) {
      throw this.error('formValues 后必须使用方括号字段访问')
    }
    this.skipWhitespace()
    const fieldKey = this.readString()
    if (!this.match(']')) {
      throw this.error("表单字段访问缺少右方括号 ']'")
    }
    if (!Object.prototype.hasOwnProperty.call(this.context.formValues, fieldKey)) {
      throw new ExpressionEvaluationError(`表达式上下文缺少权威表单值: ${fieldKey}`)
    }
    const raw = this.context.formValues[fieldKey]
    return runtimeValue(fieldKey, raw)
  }

  private readNumber(): ExprValue {
    const start = this.index
    if (this.peek() === '-') this.index++
    if (!this.isDigit(this.peek())) throw this.error('数值字面量格式无效')
    while (this.isDigit(this.peek())) this.index++
    let decimal = false
    if (this.peek() === '.') {
      decimal = true
      this.index++
      if (!this.isDigit(this.peek())) throw this.error('小数点后必须包含数字')
      while (this.isDigit(this.peek())) this.index++
    }
    const text = this.input.slice(start, this.index)
    const number = Number(text)
    if (!Number.isFinite(number)) throw this.error('数值字面量格式无效')
    return { value: number, type: decimal ? 'DECIMAL' : 'INTEGER' }
  }

  expectEnd() {
    this.skipWhitespace()
    if (this.index < this.input.length) {
      throw this.error('存在未解析的尾部输入')
    }
  }

  private enterNesting() {
    this.nestingDepth++
    if (this.nestingDepth > MAX_NESTING_DEPTH) {
      throw new ExpressionEvaluationError(`表达式嵌套深度超过上限: ${MAX_NESTING_DEPTH}`)
    }
  }

  private leaveNesting() {
    this.nestingDepth--
  }

  private countOperator() {
    this.operatorCount++
    if (this.operatorCount > MAX_OPERATOR_COUNT) {
      throw new ExpressionEvaluationError(`表达式操作符数量超过上限: ${MAX_OPERATOR_COUNT}`)
    }
  }

  private match(token: string): boolean {
    this.skipWhitespace()
    if (!this.input.startsWith(token, this.index)) return false
    // '!=' 不得被一元 '!' 吞掉
    if (token === '!' && this.input.startsWith('!=', this.index)) return false
    this.index += token.length
    return true
  }

  private matchKeyword(keyword: string): boolean {
    this.skipWhitespace()
    if (!this.input.startsWith(keyword, this.index)) return false
    const next = this.input.charAt(this.index + keyword.length)
    if (next && this.isIdentifierPart(next)) return false
    this.index += keyword.length
    return true
  }

  private skipWhitespace() {
    while (this.index < this.input.length && /\s/.test(this.input.charAt(this.index))) {
      this.index++
    }
  }

  private peek(): string {
    return this.index < this.input.length ? this.input.charAt(this.index) : '\0'
  }

  private readString(): string {
    if (this.peek() !== '"') throw this.error('此处需要字符串字面量')
    this.index++
    let out = ''
    while (this.index < this.input.length && this.peek() !== '"') {
      const ch = this.input.charAt(this.index++)
      if (ch === '\\') {
        if (this.index >= this.input.length) throw this.error('字符串转义未结束')
        const escaped = this.input.charAt(this.index++)
        if (escaped !== '"' && escaped !== '\\') {
          throw this.error(`不支持的字符串转义: \\${escaped}`)
        }
        out += escaped
      } else {
        out += ch
      }
    }
    if (this.peek() !== '"') throw this.error('字符串字面量未结束')
    this.index++
    return out
  }

  private isDigit(ch: string): boolean {
    return ch >= '0' && ch <= '9'
  }

  private isIdentifierStart(ch: string): boolean {
    return /[A-Za-z_]/.test(ch)
  }

  private isIdentifierPart(ch: string): boolean {
    return /[A-Za-z0-9_]/.test(ch)
  }

  private error(message: string): ExpressionEvaluationError {
    return new ExpressionEvaluationError(`${message}，位置 ${this.index}`)
  }
}

function runtimeValue(fieldKey: string, raw: unknown): ExprValue {
  if (raw === null || raw === undefined) {
    throw new ExpressionEvaluationError(`表达式上下文表单值不能为 null: ${fieldKey}`)
  }
  if (typeof raw === 'boolean') return { value: raw, type: 'BOOLEAN' }
  if (typeof raw === 'number') {
    if (!Number.isFinite(raw)) {
      throw new ExpressionEvaluationError(`表单字段不是可比较的标量值: ${fieldKey}`)
    }
    return {
      value: raw,
      type: Number.isInteger(raw) ? 'INTEGER' : 'DECIMAL',
    }
  }
  if (typeof raw === 'string') return { value: raw, type: 'STRING' }
  throw new ExpressionEvaluationError(`表单字段不是可比较的标量值: ${fieldKey}`)
}
