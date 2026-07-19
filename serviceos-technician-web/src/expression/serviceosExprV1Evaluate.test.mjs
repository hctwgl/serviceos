/**
 * Node 原生冒烟：SERVICEOS_EXPR_V1 运行时求值（不依赖 vitest）。
 * 运行：node serviceos-technician-web/src/expression/serviceosExprV1Evaluate.test.mjs
 */
import assert from 'node:assert/strict'
import { pathToFileURL } from 'node:url'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const api = await import(
  pathToFileURL(join(__dirname, 'serviceosExprV1Evaluate.ts')).href,
  { with: { type: 'typescript' } },
).catch(async () => {
  // Node 22+: --experimental-strip-types 由调用方启用；此处再尝试默认加载。
  return import(pathToFileURL(join(__dirname, 'serviceosExprV1Evaluate.ts')).href)
})
const {
  evaluateServiceOsExprV1,
  expressionSource,
  coerceFormValuesForExpr,
  ExpressionEvaluationError,
} = api

assert.equal(
  evaluateServiceOsExprV1('formValues["needs-photo"] == true', {
    formValues: { 'needs-photo': true },
    paths: {},
  }),
  true,
)

assert.equal(
  evaluateServiceOsExprV1(
    'formValues["needs-photo"] == true && task.taskType == "SURVEY"',
    {
      formValues: { 'needs-photo': true },
      paths: { 'task.taskType': 'SURVEY', 'task.stageCode': 'SITE' },
    },
  ),
  true,
)

assert.equal(
  evaluateServiceOsExprV1('!formValues["needs-photo"] == true', {
    formValues: { 'needs-photo': false },
    paths: {},
  }),
  true,
)

assert.equal(
  evaluateServiceOsExprV1(
    '(formValues["a"] == true || formValues["b"] == true) && task.stageCode == "SURVEY"',
    {
      formValues: { a: false, b: true },
      paths: { 'task.stageCode': 'SURVEY' },
    },
  ),
  true,
)

assert.throws(
  () =>
    evaluateServiceOsExprV1('workOrder.brandCode == "BYD"', {
      formValues: {},
      paths: {},
    }),
  (err) => err instanceof ExpressionEvaluationError,
)

assert.equal(
  expressionSource({ language: 'SERVICEOS_EXPR_V1', source: 'true' }),
  'true',
)
assert.equal(expressionSource({ language: 'OTHER', source: 'true' }), null)

const coerced = coerceFormValuesForExpr(
  [
    { fieldKey: 'flag', dataType: 'BOOLEAN' },
    { fieldKey: 'height', dataType: 'INTEGER' },
    { fieldKey: 'note', dataType: 'STRING' },
  ],
  { flag: true, height: '', note: 'ok' },
)
assert.equal(coerced.values.flag, true)
assert.equal(coerced.values.note, 'ok')
assert.deepEqual(coerced.missingKeys, ['height'])

console.log('serviceosExprV1Evaluate.test.mjs: ok')
