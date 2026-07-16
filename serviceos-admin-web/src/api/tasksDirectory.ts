import { apiGet } from './client'

export type TaskDirectoryItem = {
  id: string
  projectId: string | null
  workOrderId: string | null
  taskType: string
  taskKind: 'HUMAN' | 'AUTOMATED'
  stageCode: string | null
  priority: number
  status: string
  nextRunAt: string
  claimedBy: string | null
  attemptCount: number
  maxAttempts: number
  version: number
  createdAt: string
  updatedAt: string
}

export type TaskDirectoryPage = {
  items: TaskDirectoryItem[]
  nextCursor: string | null
  asOf: string
}

export function listAuthorizedTasks(query: Record<string, string | undefined> = {}) {
  return apiGet<TaskDirectoryPage>('/tasks', query)
}
