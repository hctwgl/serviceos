<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  getNetworkPortalAppointmentCalendar,
  type NetworkPortalAppointmentCalendar,
  type NetworkPortalAppointmentCalendarDay,
} from '../api/networkPortal'
import { formatDateTime, safeProblemMessage } from '@serviceos/web-core'
import PageState from '../components/PageState.vue'
import SummaryStrip, { type SummaryStripItem } from '../components/SummaryStrip.vue'
import { statusLabel } from '../product/labels'

const props = defineProps<{ networkContextId: string | null }>()
const calendar = ref<NetworkPortalAppointmentCalendar | null>(null)
const selectedDate = ref<string | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const selectedDay = computed<NetworkPortalAppointmentCalendarDay | null>(() => {
  if (!calendar.value || !selectedDate.value) return null
  return calendar.value.days.find((day) => day.date === selectedDate.value) ?? null
})

const summaryItems = computed<SummaryStripItem[]>(() => {
  if (!calendar.value) return []
  const today = calendar.value.days[0]?.date
  const todayCount =
    calendar.value.days.find((day) => day.date === today)?.appointmentCount ?? 0
  const busyDays = calendar.value.days.filter((day) => day.appointmentCount > 0).length
  return [
    {
      key: 'total',
      label: '范围内预约',
      value: calendar.value.totalAppointmentCount,
      hint: calendar.value.truncated ? '已截断展示' : `${calendar.value.rangeStart} ~ ${calendar.value.rangeEnd}`,
      testId: 'appointment-calendar-total',
      tone: calendar.value.totalAppointmentCount > 0 ? 'warning' : 'default',
    },
    {
      key: 'today',
      label: '首日预约',
      value: todayCount,
      testId: 'appointment-calendar-today-count',
    },
    {
      key: 'busy-days',
      label: '有预约天数',
      value: busyDays,
      testId: 'appointment-calendar-busy-days',
    },
  ]
})

async function load() {
  if (!props.networkContextId) {
    calendar.value = null
    selectedDate.value = null
    error.value = '请选择网点上下文'
    loading.value = false
    return
  }
  loading.value = true
  try {
    const data = await getNetworkPortalAppointmentCalendar(props.networkContextId)
    calendar.value = data
    selectedDate.value =
      data.days.find((day) => day.appointmentCount > 0)?.date ?? data.days[0]?.date ?? null
    error.value = null
  } catch (err) {
    calendar.value = null
    selectedDate.value = null
    error.value = safeProblemMessage(err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
watch(
  () => props.networkContextId,
  () => {
    void load()
  },
)
</script>

<template>
  <section
    data-testid="network-portal-appointments"
    data-page-id="NETWORK.APPOINTMENT"
    class="appointments-page"
  >
    <header class="hero">
      <div>
        <p class="eyebrow">预约协同</p>
        <h2>预约日历</h2>
        <p class="subtitle">
          按 Asia/Shanghai 运营日查看本网点今日与未来预约；不含客户地址等敏感字段。
        </p>
      </div>
      <button type="button" data-testid="appointment-calendar-reload" @click="load">刷新</button>
    </header>

    <PageState v-if="loading" kind="loading" />
    <PageState v-else-if="error" kind="error" :description="error" @reload="load" />
    <template v-else-if="calendar">
      <SummaryStrip :items="summaryItems" :as-of="formatDateTime(calendar.asOf)" />
      <p v-if="calendar.truncated" class="warn" data-testid="appointment-calendar-truncated">
        预约较多，仅展示前 200 条；请缩小日期范围后重试。
      </p>

      <section class="panel" data-testid="appointment-calendar-days">
        <header class="panel__head">
          <h3>运营日</h3>
          <span class="muted">{{ calendar.timezone }} · {{ calendar.rangeStart }} ~ {{ calendar.rangeEnd }}</span>
        </header>
        <ol class="day-strip">
          <li v-for="day in calendar.days" :key="day.date">
            <button
              type="button"
              class="day-chip"
              :class="{
                active: selectedDate === day.date,
                busy: day.appointmentCount > 0,
              }"
              :data-testid="`appointment-calendar-day-${day.date}`"
              @click="selectedDate = day.date"
            >
              <strong>{{ day.date.slice(5) }}</strong>
              <span class="count">{{ day.appointmentCount }}</span>
            </button>
          </li>
        </ol>
      </section>

      <section class="panel" data-testid="appointment-calendar-detail">
        <header class="panel__head">
          <h3>{{ selectedDate || '选择日期' }} 预约</h3>
          <RouterLink to="/network-portal/workbench">返回工作台</RouterLink>
        </header>
        <table v-if="selectedDay?.items?.length">
          <thead>
            <tr>
              <th>时间窗口</th>
              <th>状态</th>
              <th>类型</th>
              <th>师傅</th>
              <th>工单</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in selectedDay.items"
              :key="item.appointmentId"
              :data-testid="`appointment-calendar-item-${item.appointmentId}`"
            >
              <td>
                {{ item.windowStart ? formatDateTime(item.windowStart) : '—' }}
                <span class="muted">～</span>
                {{ item.windowEnd ? formatDateTime(item.windowEnd) : '—' }}
              </td>
              <td>{{ statusLabel(item.status) || item.status }}</td>
              <td>{{ statusLabel(item.type) || item.type }}</td>
              <td>{{ item.technicianDisplayName || (item.technicianId ? '已指派' : '待指派') }}</td>
              <td>
                <RouterLink :to="`/network-portal/work-orders/${item.workOrderId}`">
                  打开工作区
                </RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
        <PageState
          v-else
          kind="empty"
          guide="该运营日暂无 PROPOSED/CONFIRMED 预约窗口。"
        />
      </section>
    </template>
  </section>
</template>

<style scoped>
.appointments-page {
  display: grid;
  gap: 16px;
}
.hero {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
}
.eyebrow {
  margin: 0 0 4px;
  color: var(--sos-primary-600);
  font-size: 12px;
  letter-spacing: 0.08em;
}
.hero h2 {
  margin: 0 0 6px;
  font-size: 22px;
}
.subtitle,
.muted {
  color: var(--sos-color-text-tertiary);
  font-size: 13px;
}
.panel {
  border: 1px solid var(--sos-color-border-default);
  border-radius: var(--sos-radius-md);
  background: var(--sos-color-surface-card);
  padding: 14px 16px;
}
.panel__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.panel__head h3 {
  margin: 0;
  font-size: 15px;
}
.day-strip {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.day-chip {
  min-width: 72px;
  border: 1px solid var(--sos-color-border-light);
  border-radius: var(--sos-radius-md);
  background: var(--sos-color-surface-subtle);
  padding: 8px 10px;
  display: grid;
  gap: 2px;
  cursor: pointer;
  text-align: left;
}
.day-chip.busy {
  border-color: var(--sos-primary-600);
}
.day-chip.active {
  background: var(--sos-primary-100);
  border-color: var(--sos-primary-600);
}
.day-chip .count {
  font-size: 18px;
  font-weight: 650;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
th,
td {
  text-align: left;
  padding: 8px 6px;
  border-bottom: 1px solid var(--sos-color-border-light);
}
.warn {
  color: var(--sos-color-status-warning-fg, #ad6800);
  font-size: 13px;
  margin: 0;
}
button {
  border: 1px solid var(--sos-color-border-default);
  background: var(--sos-color-surface-subtle);
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  cursor: pointer;
}
</style>
