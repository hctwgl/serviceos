export type ProjectStageBarItem = {
  code: string
  label: string
  status: 'completed' | 'current' | 'pending' | 'blocked' | 'skipped'
  detail?: string
}

export type WorkflowCanvasStage = {
  code: string
  name: string
  sequence: number
  ownerLabel: string
  taskLabel: string
  typeLabel: string
  slaLabel: string
  formCount: number
  evidenceCount: number
  description?: string | null
  terminal?: boolean
  status?: 'completed' | 'current' | 'pending' | 'blocked'
}

export type RiskPanelItem = {
  key: string
  label: string
  count: number | null
  description: string
  tone: 'danger' | 'warning' | 'neutral'
  to?: string
}
