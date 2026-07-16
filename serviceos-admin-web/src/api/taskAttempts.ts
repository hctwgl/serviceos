import { apiGet } from './client'

export type TaskExecutionAttempt = {
  attemptId: string
  attemptNo: number
  resultCode: string
  errorCode: string | null
  resultRef: string | null
  nextRetryAt: string | null
  startedAt: string
  finishedAt: string | null
}

export type TaskExecutionAttemptPage = {
  resourceVersion: number
  items: TaskExecutionAttempt[]
  nextCursor: string | null
  asOf: string
}

export function listTaskExecutionAttempts(
  taskId: string,
  query: Record<string, string | undefined> = {},
) {
  return apiGet<TaskExecutionAttemptPage>(`/tasks/${taskId}/execution-attempts`, query)
}
