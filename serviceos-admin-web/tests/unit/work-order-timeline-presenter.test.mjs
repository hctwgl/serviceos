import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const presenter = readFileSync(
  new URL('../../src/presentation/work-order-timeline.presenter.ts', import.meta.url),
  'utf8',
)

test('工单时间线使用中文业务事件，不把未知技术事件原样展示', () => {
  assert.match(presenter, /工单已接收/)
  assert.match(presenter, /服务责任已生效/)
  assert.match(presenter, /任务已完成/)
  assert.match(presenter, /业务动态暂不可识别/)
  assert.doesNotMatch(presenter, /label: normalized/)
})
