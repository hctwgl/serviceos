<script setup lang="ts">
import type { NetworkAssignmentCandidate } from '@serviceos/api-client'
import { assignNetwork, loadNetworkCandidates } from '@serviceos/api-client'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { Button, Drawer, Radio } from '@serviceos/design-system'
import { computed, ref, watch } from 'vue'
import PageError from './PageError.vue'

const props = defineProps<{ open: boolean; taskId: string | null }>()
const emit = defineEmits<{ close: []; assigned: [] }>()
const selected = ref<string>()
const queryClient = useQueryClient()

const candidates = useQuery({
  queryKey: computed(() => ['network-candidates', props.taskId]),
  queryFn: () => loadNetworkCandidates(props.taskId!),
  enabled: computed(() => props.open && Boolean(props.taskId)),
})

const command = useMutation({
  mutationFn: async () => {
    const view = candidates.data.value
    if (!props.taskId || !selected.value || !view) throw new Error('请选择责任网点')
    return assignNetwork(props.taskId, {
      networkAssigneeId: selected.value,
      businessType: view.businessType,
    })
  },
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['work-order-workspace'] })
    emit('assigned')
    emit('close')
  },
})

const selectedCandidate = computed<NetworkAssignmentCandidate | undefined>(() =>
  candidates.data.value?.candidates.find((item) => item.networkId === selected.value),
)

watch(() => props.open, (open) => {
  if (!open) selected.value = undefined
})
</script>

<template>
  <Drawer :open="open" width="520" title="分配责任网点" placement="right" @close="emit('close')">
    <div class="assignment-intro">
      系统只展示满足当前项目、服务区域、业务类型和容量要求的网点。
    </div>
    <PageError v-if="candidates.isError.value" :detail="candidates.error.value?.message ?? '候选网点加载失败'" />
    <template v-else-if="candidates.data.value">
      <p class="drawer-explanation">{{ candidates.data.value.rankingExplanation }}</p>
      <div v-if="candidates.data.value.emptyReason" class="empty-candidates">
        <strong>暂无符合条件的网点</strong>
        <p>{{ candidates.data.value.emptyReason }}</p>
      </div>
      <Radio.Group v-else v-model:value="selected" class="candidate-list">
        <Radio v-for="item in candidates.data.value.candidates" :key="item.networkId" :value="item.networkId" class="candidate-card">
          <span class="candidate-rank">推荐 {{ item.rank }}</span>
          <strong>{{ item.networkName }}</strong>
          <span>{{ item.coverageSummary }}</span>
          <span>剩余容量 {{ item.remainingCapacity }} · {{ item.recommendationSummary }}</span>
        </Radio>
      </Radio.Group>
      <div v-if="selectedCandidate" class="candidate-reason">
        <strong>推荐依据</strong>
        <p>{{ selectedCandidate.recommendationSummary }}</p>
      </div>
      <PageError v-if="command.isError.value" :detail="command.error.value?.message ?? '分配责任网点失败'" />
    </template>
    <template #footer>
      <div class="drawer-footer">
        <Button @click="emit('close')">取消</Button>
        <Button type="primary" :disabled="!selected" :loading="command.isPending.value" @click="command.mutate()">确认分配</Button>
      </div>
    </template>
  </Drawer>
</template>
