<script setup lang="ts">
import type { WorkOrderWorkspaceAppointmentsVisitsSectionData } from '@serviceos/api-client'
import { Empty } from '@serviceos/design-system'
import { computed } from 'vue'
import {
  appointmentStatusLabel,
  appointmentTypeLabel,
  formatDateTime,
  visitStatusLabel,
} from '../presenters/work-order'
import StatusPill from './StatusPill.vue'

/** 工单工作区「预约与上门」区块：预约时间窗 + 上门签到记录。 */
const props = defineProps<{ data: WorkOrderWorkspaceAppointmentsVisitsSectionData | null }>()

const appointments = computed(() => props.data?.appointments ?? [])
const visits = computed(() => props.data?.visits ?? [])
const isEmpty = computed(() => !appointments.value.length && !visits.value.length)
</script>

<template>
  <div class="appointments-visits-section">
    <Empty
      v-if="isEmpty"
      description="暂无预约与上门记录"
    />
    <template v-else>
      <section class="av-block">
        <h3>预约</h3>
        <Empty
          v-if="!appointments.length"
          description="暂无预约"
        />
        <article
          v-for="appointment in appointments"
          :key="appointment.appointmentId"
          class="av-card"
        >
          <header>
            <strong>{{ appointmentTypeLabel(appointment.type) }}</strong>
            <StatusPill
              :tone="appointment.status === 'CONFIRMED' ? 'green' : appointment.status === 'CANCELLED' ? 'red' : 'blue'"
              :label="appointmentStatusLabel(appointment.status)"
            />
          </header>
          <dl class="av-facts">
            <div>
              <dt>时间窗</dt>
              <dd>
                {{ formatDateTime(appointment.windowStart) }} ~ {{ formatDateTime(appointment.windowEnd) }}
              </dd>
            </div>
            <div>
              <dt>预计时长</dt>
              <dd>{{ appointment.estimatedDurationMinutes ? `${appointment.estimatedDurationMinutes} 分钟` : '—' }}</dd>
            </div>
            <div>
              <dt>创建时间</dt>
              <dd>{{ formatDateTime(appointment.createdAt) }}</dd>
            </div>
          </dl>
        </article>
      </section>

      <section class="av-block">
        <h3>上门签到</h3>
        <Empty
          v-if="!visits.length"
          description="暂无上门签到"
        />
        <article
          v-for="visit in visits"
          :key="visit.visitId"
          class="av-card"
        >
          <header>
            <strong>第 {{ visit.visitSequence }} 次上门</strong>
            <StatusPill
              :tone="visit.status === 'COMPLETED' ? 'green' : visit.status === 'INTERRUPTED' ? 'red' : 'blue'"
              :label="visitStatusLabel(visit.status)"
            />
          </header>
          <dl class="av-facts">
            <div>
              <dt>签到时间</dt>
              <dd>{{ formatDateTime(visit.checkInCapturedAt) }}</dd>
            </div>
            <div>
              <dt>签退时间</dt>
              <dd>{{ formatDateTime(visit.checkOutCapturedAt) }}</dd>
            </div>
            <div>
              <dt>围栏结果</dt>
              <dd>{{ visit.geofenceResult }}</dd>
            </div>
            <div v-if="visit.resultCode">
              <dt>结果码</dt>
              <dd>{{ visit.resultCode }}</dd>
            </div>
          </dl>
        </article>
      </section>
    </template>
  </div>
</template>

<style scoped>
.appointments-visits-section {
  display: grid;
  gap: 16px;
}

.av-block {
  display: grid;
  gap: 10px;
  align-content: start;
}

.av-block > h3 {
  margin: 0 0 2px;
  color: var(--sos-text-strong);
  font-size: 14px;
}

.av-card {
  display: grid;
  gap: 8px;
  padding: 13px 15px;
  border: 1px solid var(--sos-border-soft);
  border-radius: 7px;
}

.av-card > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.av-card > header strong {
  color: var(--sos-text-strong);
  font-size: 13px;
}

.av-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 18px;
  margin: 0;
}

.av-facts dt {
  color: var(--sos-text-muted);
  font-size: 11px;
}

.av-facts dd {
  margin: 3px 0 0;
  color: var(--sos-text-strong);
  font-size: 12px;
}
</style>
