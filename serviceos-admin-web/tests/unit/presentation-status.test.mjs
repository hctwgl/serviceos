import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '../..')

function src(rel) {
  return readFileSync(join(root, rel), 'utf8')
}

test('semantic status model forbids single cross-domain color map', () => {
  const semantic = src('src/presentation/semantic-status.ts')
  assert.match(semantic, /export type SemanticStatus/)
  assert.match(semantic, /'stale'/)
  assert.match(semantic, /'offline'/)
  assert.match(semantic, /'shadow'/)
  assert.match(semantic, /SemanticStatusPresentation/)
})

test('domain presenters exist and keep work-order / review / sla separate', () => {
  assert.match(src('src/presentation/work-order-status.presenter.ts'), /presentWorkOrderStatus/)
  assert.match(src('src/presentation/work-order-status.presenter.ts'), /FULFILLED/)
  assert.match(src('src/presentation/review-status.presenter.ts'), /presentReviewStatus/)
  assert.match(src('src/presentation/review-status.presenter.ts'), /FORCE_APPROVED/)
  assert.match(src('src/presentation/sla-status.presenter.ts'), /presentSlaStatus/)
  assert.match(src('src/presentation/sla-status.presenter.ts'), /BREACHED/)
  assert.match(src('src/presentation/sla-status.presenter.ts'), /presentFreshnessStatus/)
  assert.match(src('src/presentation/pricing-status.presenter.ts'), /semantic: 'shadow'/)
})

test('enum labels map OEM and review origin to Chinese', () => {
  const enums = src('src/presentation/enum-labels.ts')
  assert.match(enums, /GEELY:\s*'吉利汽车'/)
  assert.match(enums, /BYD:\s*'比亚迪'/)
  assert.match(enums, /INSTALLATION:\s*'安装服务'/)
  assert.match(enums, /INTERNAL:\s*'平台审核'/)
  assert.match(enums, /CLIENT:\s*'车企审核'/)
})

test('entity name presenter never returns full UUID as label', () => {
  const entity = src('src/presentation/entity-name.presenter.ts')
  assert.match(entity, /名称不可用/)
  assert.match(entity, /暂未加载/)
  assert.match(entity, /presentEntityName/)
  assert.doesNotMatch(entity, /label:\s*input\.id/)
})

test('empty value kinds are distinct', () => {
  const empty = src('src/presentation/empty-value.presenter.ts')
  for (const label of ['未提供', '不适用', '未知', '尚未生成', '无相关记录', '无权限查看', '暂未加载']) {
    assert.match(empty, new RegExp(label))
  }
})

test('statusTone cross-domain map removed from product/statusLabels', () => {
  const labels = src('src/product/statusLabels.ts')
  assert.match(labels, /@deprecated M370/)
  assert.doesNotMatch(labels, /const STATUS_TONES/)
  assert.match(labels, /return 'neutral'/)
})

test('token version and docs exist', () => {
  assert.match(src('src/app/token-version.ts'), /1\.1\.0/)
  assert.match(src('docs/design-tokens.md'), /1\.1\.0/)
  assert.match(src('docs/components/SemanticStatusTag.md'), /SemanticStatusTag/)
})
