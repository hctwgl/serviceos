import { describe, expect, it } from 'vitest'
import { stageLabel } from './work-order'

describe('工单 Presenter', () => {
  it('返回业务阶段中文名称', () => {
    expect(stageLabel('PILOT_INSTALLATION')).toBe('上门安装')
  })

  it('不使用技术码作为缺失中文名称的兜底', () => {
    expect(() => stageLabel('UNREGISTERED_STAGE')).toThrow('页面数据不完整')
  })
})
