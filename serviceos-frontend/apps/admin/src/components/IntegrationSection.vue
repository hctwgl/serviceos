<script setup lang="ts">
import type { WorkOrderWorkspaceOutboundDeliverySummary, WorkOrderWorkspaceIntegrationSectionData } from '@serviceos/api-client'
import { Empty } from '@serviceos/design-system'
import { computed } from 'vue'
import { deliveryStatusLabel, formatDateTime } from '../presenters/work-order'
import StatusPill from './StatusPill.vue'

/** 工单工作区「外部回传」区块：入站报文与出站回传。 */
const props = defineProps<{ data: WorkOrderWorkspaceIntegrationSectionData | null }>()

const inbound = computed(() => props.data?.inboundEnvelopes ?? [])
const outbound = computed(() => props.data?.outboundDeliveries ?? [])
const isEmpty = computed(() => !inbound.value.length && !outbound.value.length)

function deliveryTone(status: WorkOrderWorkspaceOutboundDeliverySummary['status']) {
  if (status === 'ACKNOWLEDGED' || status === 'DELIVERED') return 'green'
  if (status === 'REJECTED' || status === 'FAILED_FINAL') return 'red'
  if (status === 'PENDING' || status === 'SENDING') return 'blue'
  return 'gray'
}

function latestAttemptAt(delivery: WorkOrderWorkspaceOutboundDeliverySummary): string | null {
  const latest = delivery.attempts.at(-1)
  return latest?.startedAt ?? null
}
</script>

<template>
  <div class="integration-section">
    <Empty
      v-if="isEmpty"
      description="暂无外部集成报文"
    />
    <template v-else>
      <section class="it-block">
        <h3>入站报文</h3>
        <Empty
          v-if="!inbound.length"
          description="暂无入站报文"
        />
        <article
          v-for="envelope in inbound"
          :key="envelope.inboundEnvelopeId"
          class="it-card"
        >
          <header>
            <strong>{{ envelope.messageType }}</strong>
            <small>{{ envelope.externalMessageId }}</small>
          </header>
          <dl class="it-facts">
            <div>
              <dt>处理结果</dt>
              <dd>{{ envelope.resultCode }}</dd>
            </div>
            <div>
              <dt>接收时间</dt>
              <dd>{{ formatDateTime(envelope.receivedAt) }}</dd>
            </div>
          </dl>
        </article>
      </section>

      <section class="it-block">
        <h3>出站回传</h3>
        <Empty
          v-if="!outbound.length"
          description="暂无出站回传"
        />
        <article
          v-for="delivery in outbound"
          :key="delivery.deliveryId"
          class="it-card"
        >
          <header>
            <strong>{{ delivery.businessMessageType }}</strong>
            <StatusPill
              :tone="deliveryTone(delivery.status)"
              :label="deliveryStatusLabel(delivery.status)"
            />
          </header>
          <dl class="it-facts">
            <div>
              <dt>外部单号</dt>
              <dd>{{ delivery.externalOrderCode }}</dd>
            </div>
            <div>
              <dt>投递次数</dt>
              <dd>{{ delivery.attempts.length }} 次</dd>
            </div>
            <div>
              <dt>最近投递</dt>
              <dd>{{ formatDateTime(latestAttemptAt(delivery)) }}</dd>
            </div>
          </dl>
        </article>
      </section>
    </template>
  </div>
</template>

<style scoped>
.integration-section {
  display: grid;
  gap: 16px;
}

.it-block {
  display: grid;
  gap: 10px;
  align-content: start;
}

.it-block > h3 {
  margin: 0 0 2px;
  color: var(--sos-text-strong);
  font-size: 14px;
}

.it-card {
  display: grid;
  gap: 8px;
  padding: 13px 15px;
  border: 1px solid var(--sos-border-soft);
  border-radius: 7px;
}

.it-card > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.it-card > header strong {
  color: var(--sos-text-strong);
  font-size: 13px;
}

.it-card > header small {
  overflow: hidden;
  color: var(--sos-text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.it-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 18px;
  margin: 0;
}

.it-facts dt {
  color: var(--sos-text-muted);
  font-size: 11px;
}

.it-facts dd {
  margin: 3px 0 0;
  color: var(--sos-text-strong);
  font-size: 12px;
}
</style>
