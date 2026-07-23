import { describe, expect, it } from 'vitest'
import { presentProblem } from './http'

describe('presentProblem', () => {
  it('保留后端已经提供的中文业务说明', () => {
    expect(presentProblem(400, { detail: '当前工单缺少责任网点。' })).toBe('当前工单缺少责任网点。')
  })

  it('不把英文框架异常直接暴露给业务用户', () => {
    expect(presentProblem(403, { title: 'The action is not allowed' })).toBe('当前账号无权查看或执行此操作。')
  })

  it('为并发冲突提供明确恢复动作', () => {
    expect(presentProblem(412, {})).toBe('数据已被其他操作更新，请刷新后重新确认。')
  })
})
