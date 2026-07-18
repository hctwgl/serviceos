export type ServiceContext = Readonly<{
  contextId: string
  contextVersion: string
  scopeRef?: string
  [key: string]: unknown
}>

export type ContextSelectionStore<T extends ServiceContext> = Readonly<{
  current(): T | null
  select(next: T): void
  clear(): void
}>

/**
 * Context 选择只缓存服务端签发的 opaque contextId/version。版本变化时必须清理宿主查询缓存，
 * 不能把本地选择当成授权，也不接受客户端自报 tenant/project/network 等范围。
 */
export function createContextSelectionStore<T extends ServiceContext>(
  onContextBoundaryChanged: () => void,
): ContextSelectionStore<T> {
  let selected: T | null = null

  return {
    current: () => selected,
    select(next) {
      if (!next.contextId || !next.contextVersion) throw new Error('服务上下文缺少稳定标识或版本')
      if (
        selected &&
        (selected.contextId !== next.contextId || selected.contextVersion !== next.contextVersion)
      ) {
        onContextBoundaryChanged()
      }
      selected = Object.freeze({ ...next }) as T
    },
    clear() {
      if (selected) onContextBoundaryChanged()
      selected = null
    },
  }
}
