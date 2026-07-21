import { apiGet } from './client'

export type TaskTemplateCategory =
  | 'INTAKE'
  | 'DISPATCH'
  | 'APPOINTMENT'
  | 'SURVEY'
  | 'INSTALL'
  | 'REVIEW'
  | 'CORRECTION'
  | 'FOLLOW_UP'
  | 'SYSTEM'

export type ConfigurationTaskTemplateItem = {
  templateKey: string
  templateName: string
  taskTypeCode: string
  category: TaskTemplateCategory
  categoryLabel: string
  executionRoleLabel: string
  assignmentStrategyLabel?: string | null
  formSummary?: string | null
  evidenceSummary?: string | null
  slaSummary?: string | null
  status: 'DRAFT' | 'PUBLISHED'
  statusLabel: string
  referencedWorkflowCount: number
  referencedWorkflowNames: string[]
  lastUpdatedAt?: string | null
  gaps: string[]
}

export function listConfigurationTaskTemplates() {
  return apiGet<ConfigurationTaskTemplateItem[]>('/configuration/task-templates')
}
