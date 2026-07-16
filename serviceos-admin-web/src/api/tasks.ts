import { apiGet } from './client'

export type TaskAllowedAction = {
  code: 'task.claim' | 'task.start' | 'task.complete' | 'task.release'
  label: string
  inputSchemaRef: string | null
  obligations: Array<'REQUIRE_REASON' | 'REQUIRE_RESULT'>
}

export type TaskAllowedActions = {
  resourceVersion: number
  asOf: string
  actions: TaskAllowedAction[]
}

/** 只读取服务端投影；前端不得本地推导可执行动作。 */
export function getTaskAllowedActions(taskId: string) {
  return apiGet<TaskAllowedActions>(`/tasks/${taskId}/allowed-actions`)
}
