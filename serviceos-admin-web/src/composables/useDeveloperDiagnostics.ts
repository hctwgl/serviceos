import { computed, ref } from 'vue'

export type DiagnosticEntry = {
  id: string
  title: string
  at: string
  fields: Record<string, string | number | boolean | null | undefined>
}

const open = ref(false)
const entries = ref<DiagnosticEntry[]>([])
const lastCorrelationId = ref<string | null>(null)

export function useDeveloperDiagnostics() {
  const canOpen = computed(
    () => import.meta.env.DEV || entries.value.some((e) => e.fields.capability === 'diagnostics.read'),
  )

  function pushDiagnostic(entry: Omit<DiagnosticEntry, 'id' | 'at'> & { id?: string }) {
    const item: DiagnosticEntry = {
      id: entry.id ?? crypto.randomUUID(),
      title: entry.title,
      at: new Date().toISOString(),
      fields: entry.fields,
    }
    entries.value = [item, ...entries.value].slice(0, 40)
    if (typeof entry.fields.correlationId === 'string') {
      lastCorrelationId.value = entry.fields.correlationId
    }
  }

  function clearDiagnostics() {
    entries.value = []
  }

  function openDrawer() {
    if (canOpen.value) open.value = true
  }

  function closeDrawer() {
    open.value = false
  }

  return {
    open,
    entries,
    lastCorrelationId,
    canOpen,
    pushDiagnostic,
    clearDiagnostics,
    openDrawer,
    closeDrawer,
  }
}
