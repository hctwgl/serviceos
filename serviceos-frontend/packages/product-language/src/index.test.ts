import { describe, expect, it } from 'vitest'
import { statusLabel } from './index'

describe('statusLabel', () => {
  it('将稳定状态码转换为中文产品语言', () => {
    expect(statusLabel('RUNNING')).toBe('进行中')
  })

  it('遇到未知状态时明确失败', () => {
    expect(() => statusLabel('UNKNOWN_VALUE')).toThrow('缺少状态中文定义')
  })
})
