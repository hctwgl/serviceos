import { describe, expect, it } from 'vitest'
import { stageLabel, timelineEventLabel } from './work-order'

describe('工单 Presenter', () => {
  it('返回业务阶段中文名称', () => {
    expect(stageLabel('PILOT_INSTALLATION')).toBe('上门安装')
  })

  it('同时兼容新版 SURVEY/INSTALLATION 阶段码', () => {
    expect(stageLabel('SURVEY')).toBe('上门勘测')
    expect(stageLabel('INSTALLATION')).toBe('上门安装')
  })

  it('不使用技术码作为缺失中文名称的兜底', () => {
    expect(() => stageLabel('UNREGISTERED_STAGE')).toThrow('页面数据不完整')
  })

  it('时间线已知事件返回中文名称', () => {
    expect(timelineEventLabel('task.completed')).toBe('任务已完成')
  })

  it('时间线未知事件原样显示英文码，不失败关闭', () => {
    expect(timelineEventLabel('future.new-event')).toBe('future.new-event')
  })
})
