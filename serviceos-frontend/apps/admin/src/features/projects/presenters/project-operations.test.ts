import type {
  AdminProjectWorkspaceView,
  AdminWorkOrderDirectoryItem,
  AdminWorkOrderDirectoryView,
} from '@serviceos/api-client'
import { describe, expect, it } from 'vitest'
import {
  buildProjectActivities,
  buildProjectRiskItems,
  buildProjectStageBar,
} from './project-operations'

const project = {
  projectId: 'project-1',
  projectCode: 'PRJ-001',
  projectName: '华东家充网络',
  clientName: '新能源客户',
  status: 'ACTIVE',
  startsOn: '2026-07-01',
  endsOn: null,
  regionNames: ['上海'],
  networkNames: ['浦东服务中心'],
  configurationReadable: true,
  fulfillmentProfiles: [{
    profileId: 'profile-1',
    profileName: '家充勘测安装标准方案',
    serviceProductName: '家充勘测安装服务',
    status: 'ACTIVE',
    stageCount: 4,
    formCount: 1,
    evidenceCount: 2,
    activeVersion: '3',
    effectiveFrom: '2026-07-01T00:00:00Z',
    workflowSummary: '客户联系 → 预约 → 勘测 → 安装',
    slaSummary: '48 小时完成预约',
    updatedAt: '2026-07-24T08:00:00Z',
    dataComplete: true,
    dataProblem: null,
  }],
  activeWorkOrderCount: 1,
  activeWorkOrderCountTruncated: false,
  dataComplete: true,
  dataProblem: null,
  asOf: '2026-07-24T12:00:00Z',
} satisfies AdminProjectWorkspaceView

function directory(items: AdminWorkOrderDirectoryItem[], overrides: Partial<AdminWorkOrderDirectoryView['queueSummary']> = {}): AdminWorkOrderDirectoryView {
  return {
    items,
    projectOptions: [],
    queueSummary: {
      priorityCount: 0,
      reviewCount: 0,
      correctionCount: 0,
      dispatchCount: 0,
      slaRiskCount: 0,
      exceptionCount: 0,
      waitingExternalCount: 0,
      unassignedCount: 0,
      generatedAt: '2026-07-24T12:00:00Z',
      ...overrides,
    },
    nextCursor: null,
    totalCount: items.length,
    generatedAt: '2026-07-24T12:00:00Z',
  }
}

function order(overrides: Partial<AdminWorkOrderDirectoryItem> = {}): AdminWorkOrderDirectoryItem {
  return {
    id: 'work-order-1',
    orderCode: 'WO-001',
    customerName: null,
    customerPhone: null,
    projectId: 'project-1',
    projectName: '华东家充网络',
    clientName: '新能源客户',
    serviceName: '家充安装',
    stageName: '勘测',
    networkName: '浦东服务中心',
    technicianName: null,
    slaLevel: 'NORMAL',
    slaLabel: '正常',
    statusName: '进行中',
    updatedAt: '2026-07-24T11:20:00Z',
    dataComplete: true,
    dataProblem: null,
    ...overrides,
  }
}

describe('项目运营工作区 Presenter', () => {
  it('从方案摘要和工单投影生成可读的阶段进度', () => {
    const stages = buildProjectStageBar(project, project.fulfillmentProfiles, directory([order()]))

    expect(stages.map((stage) => stage.label)).toEqual(['客户联系', '预约', '勘测', '安装'])
    expect(stages.map((stage) => stage.status)).toEqual(['completed', 'completed', 'current', 'pending'])
  })

  it('只把服务端队列风险映射为风险入口', () => {
    const risks = buildProjectRiskItems(directory([], {
      slaRiskCount: 2,
      exceptionCount: 1,
      unassignedCount: 3,
    }))

    expect(risks.map((risk) => risk.label)).toEqual(['SLA 风险工单', '质量与履约异常', '责任待分配'])
    expect(risks.every((risk) => risk.to?.startsWith('/work-orders?'))).toBe(true)
  })

  it('按更新时间展示真实工单动态，并保留风险色调', () => {
    const activities = buildProjectActivities([
      order({ id: 'old', orderCode: 'WO-OLD', updatedAt: '2026-07-24T09:00:00Z' }),
      order({ id: 'new', orderCode: 'WO-NEW', updatedAt: '2026-07-24T11:30:00Z', slaLevel: 'BREACHED' }),
    ])

    expect(activities[0]).toMatchObject({ id: 'new', tone: 'orange' })
    expect(activities[1]).toMatchObject({ id: 'old', tone: 'blue' })
  })
})
