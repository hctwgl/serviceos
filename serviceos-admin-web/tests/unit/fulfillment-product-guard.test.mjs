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
  assert.match(text, /FulfillmentRunbookTable/)
})

test('运行说明页面不得展示原始技术摘要', async () => {
  const text = await source('src/pages/FulfillmentPreviewPage.vue')
  assert.doesNotMatch(text, /contentDigest/)
  assert.doesNotMatch(text, /Manifest 摘要/)
  assert.match(text, /工单运行说明书/)
})
