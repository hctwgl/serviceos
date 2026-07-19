import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')

function loadTsExportMap(relativePath, exportName) {
  const source = readFileSync(join(root, relativePath), 'utf8')
  // 轻量断言：源码中必须包含目标中文映射，避免散落硬编码回退。
  return { source, exportName }
}

test('statusLabels maps operator-facing Chinese values', () => {
  const { source } = loadTsExportMap('src/product/statusLabels.ts', 'statusLabel')
  assert.match(source, /IN_PROGRESS:\s*'处理中'/)
  assert.match(source, /WAITING_REVIEW:\s*'待审核'/)
  assert.match(source, /BREACHED:\s*'已超时'/)
  assert.match(source, /RESUBMITTED:\s*'已重新提交'/)
})

test('terms map technical field names to Chinese business labels', () => {
  const { source } = loadTsExportMap('src/product/terms.ts', 'FIELD_LABELS')
  assert.match(source, /projectId:\s*'所属项目'/)
  assert.match(source, /sourceReviewCaseId:\s*'来源审核单'/)
  assert.match(source, /correctionCaseId:\s*'整改单号'/)
  assert.match(source, /createdAt:\s*'创建时间'/)
})

test('router registers workbench and catch-all notfound', () => {
  const source = readFileSync(join(root, 'src/router.ts'), 'utf8')
  assert.match(source, /path:\s*'workbench'/)
  assert.match(source, /name:\s*'ADMIN\.WORKBENCH'/)
  assert.match(source, /path:\s*':pathMatch\(\.\*\)\*'/)
  assert.match(source, /golden-path/)
})
