import { apiGet } from './client'
import type { TaskDirectoryItem } from './tasksDirectory'

export type TaskDetail = {
  task: TaskDirectoryItem
  workflowInstanceId: string | null
  stageInstanceId: string | null
  formRef: string | null
  responsibleUserId: string | null
  candidateUserIds: string[]
  claimedAt: string | null
  startedAt: string | null
  completedAt: string | null
  resultRef: string | null
  resultDigest: string | null
  asOf: string
}

export function getAuthorizedTask(taskId: string) {
  return apiGet<TaskDetail>(`/tasks/${taskId}`)
}
