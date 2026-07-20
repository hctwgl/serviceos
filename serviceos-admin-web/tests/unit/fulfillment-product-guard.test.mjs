import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

async function source(path) {
  return readFile(new URL(`../../${path}`, import.meta.url), 'utf8')
}

test('履约列表不得点击后直接硬编码创建固定方案', async () => {
  const text = await source('src/pages/FulfillmentProfileListPage.vue')
  assert.doesNotMatch(text, /createStandard/)
  assert.doesNotMatch(text, /createProjectFulfillmentProfile/)
  assert.match(text, /ADMIN\.PROJECT\.FULFILLMENT\.NEW/)
})

test('发布页面不得向普通用户展示 Manifest JSON 或 Digest', async () => {
  const text = await source('src/pages/FulfillmentPublishFlowPage.vue')
  assert.doesNotMatch(text, /<pre/i)
  assert.doesNotMatch(text, /contentDigest/)
  assert.doesNotMatch(text, /Manifest 摘要/)
  assert.doesNotMatch(text, /JSON\.parse\s*\(/)
  assert.match(text, /FulfillmentRunbookTable/)
})

test('运行说明页面不得展示原始技术摘要或自行解释 Manifest', async () => {
  const text = await source('src/pages/FulfillmentPreviewPage.vue')
  assert.doesNotMatch(text, /contentDigest/)
  assert.doesNotMatch(text, /Manifest 摘要/)
  assert.doesNotMatch(text, /JSON\.parse\s*\(/)
  assert.match(text, /工单运行说明书/)
})

test('临时 Manifest 解析只能集中在有明确删除计划的适配器', async () => {
  const adapter = await source('src/components/fulfillment/FulfillmentRunbookTable.vue')
  assert.match(adapter, /M385 过渡展示适配器/)
  assert.match(adapter, /JSON\.parse\s*\(/)
  assert.match(adapter, /必须删除/)
})

test('履约编辑器使用业务选择器而不是原生按钮和资产键文本框', async () => {
  const text = await source('src/pages/FulfillmentProfileEditorPage.vue')
  assert.doesNotMatch(text, /<button\b/i)
  assert.doesNotMatch(text, /表单引用（每行一个资产键）/)
  assert.doesNotMatch(text, /资料模板引用（每行一个资产键）/)
  assert.doesNotMatch(text, /SLA 策略资产键/)
  assert.doesNotMatch(text, /聚合版本/)
  assert.match(text, /FulfillmentStageNavigation/)
  assert.match(text, /listConfigurationDrafts/)
  assert.match(text, /选择已发布的表单模板/)
})
