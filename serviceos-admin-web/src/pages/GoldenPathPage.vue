<script setup lang="ts">
/**
 * 工单全流程演练：以步骤条展示当前环节、责任角色与跨门户入口。
 * 状态以真实后端查询为准，不在前端伪造成功流转。
 */
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import PageState from '../components/PageState.vue'
import { listAuthorizedWorkOrders } from '../api/workOrders'
import { toUserFacingError } from '../product/errorMessages'
import { formatDateTime } from '../product/formatTime'
import { statusLabel } from '../product/statusLabels'

type StepStatus = 'done' | 'current' | 'todo' | 'blocked' | 'optional'

type GoldenStep = {
  id: number
  name: string
  portal: string
  role: string
  nextAction: string
  entry: string
  status: StepStatus
  note?: string
}

const DEMO_ORDER_PREFIX = 'WO-DEMO-'
const PILOT_ORDER = 'ADMIN-PILOT-001'

const loading = ref(true)
const error = ref<string | null>(null)
const errorCode = ref<string | null>(null)
const demoOrders = ref<Array<{ id: string; code: string; status: string }>>([])
const selectedCode = ref(PILOT_ORDER)

const steps = computed<GoldenStep[]>(() => {
  const selected = demoOrders.value.find((o) => o.code === selectedCode.value)
  const woStatus = selected?.status ?? ''
  // 基于工单粗状态推断当前主环节；细粒度阶段依赖任务/审核，页面下方说明能力边界。
  const fulfilled = woStatus === 'FULFILLED'
  const active = woStatus === 'ACTIVE' || woStatus === 'RECEIVED'
  return [
    {
      id: 1,
      name: '工单导入',
      portal: '管理端',
      role: '平台运营 / 车企入站',
      nextAction: '确认入站工单已生成',
      entry: '管理端 → 工单中心',
      status: selected ? 'done' : 'todo',
    },
    {
      id: 2,
      name: '平台初审 / 资料审核',
      portal: '管理端',
      role: '平台运营人员',
      nextAction: '审核工单基础信息与资料',
      entry: '管理端 → 审核中心',
      status: fulfilled ? 'done' : active ? 'current' : 'todo',
    },
    {
      id: 3,
      name: '分配网点',
      portal: '管理端',
      role: '平台调度',
      nextAction: '将任务分配给服务网点',
      entry: '管理端 → 工单详情 → 分配',
      status: fulfilled ? 'done' : 'todo',
      note: '对应接口：任务人工分配网点（manual-assign）',
    },
    {
      id: 4,
      name: '网点接单',
      portal: '网点端',
      role: '网点调度',
      nextAction: '网点确认接单',
      entry: '网点端 → 任务 → 确认接单',
      status: fulfilled ? 'done' : 'todo',
      note: '接口：POST /network-portal/tasks/{taskId}:accept-assignment（仅激活 ACTIVE NETWORK）。',
    },
    {
      id: 5,
      name: '指派师傅',
      portal: '网点端',
      role: '网点调度',
      nextAction: '指派本网点服务师傅',
      entry: '网点端 → 任务 → 指派师傅',
      status: fulfilled ? 'done' : 'todo',
    },
    {
      id: 6,
      name: '客户预约',
      portal: '网点端 / 师傅端',
      role: '网点或师傅',
      nextAction: '联系客户并确认上门时间',
      entry: '网点端预约 / 师傅端待预约',
      status: fulfilled ? 'done' : 'todo',
    },
    {
      id: 7,
      name: '上门勘测',
      portal: '师傅端',
      role: '服务师傅',
      nextAction: '签到、勘测并提交资料',
      entry: '师傅端 → 当前任务',
      status: fulfilled ? 'done' : 'todo',
    },
    {
      id: 8,
      name: '勘测审核',
      portal: '管理端',
      role: '平台审核',
      nextAction: '审核勘测资料，不通过则创建整改',
      entry: '管理端 → 审核中心',
      status: fulfilled ? 'done' : 'todo',
    },
    {
      id: 9,
      name: '安装施工',
      portal: '师傅端',
      role: '服务师傅',
      nextAction: '现场安装并上传完工资料',
      entry: '师傅端 → 当前任务',
      status: fulfilled ? 'done' : 'optional',
      note: '完整家充模板需项目配置 FORM/EVIDENCE；试点流为单阶段现场履约。',
    },
    {
      id: 10,
      name: '资料复核',
      portal: '网点端',
      role: '网点质检',
      nextAction: '复核师傅提交资料',
      entry: '网点端 → 整改/工作区',
      status: 'blocked',
      note: '阻塞：尚无独立「网点复核」写命令；网点可只读查看并代补资料。',
    },
    {
      id: 11,
      name: '平台审核',
      portal: '管理端',
      role: '平台审核',
      nextAction: '审核通过或驳回并生成整改',
      entry: '管理端 → 审核中心',
      status: fulfilled ? 'done' : 'todo',
    },
    {
      id: 12,
      name: '整改处理',
      portal: '师傅端',
      role: '服务师傅',
      nextAction: '查看整改要求并重新提交',
      entry: '师傅端 → 整改任务',
      status: 'optional',
    },
    {
      id: 13,
      name: '完工确认',
      portal: '管理端',
      role: '平台运营',
      nextAction: '任务完成后工单进入已完成',
      entry: '管理端 → 工单详情',
      status: fulfilled ? 'done' : 'todo',
    },
    {
      id: 14,
      name: '结算归档',
      portal: '管理端',
      role: '结算',
      nextAction: '进入结算',
      entry: '结算管理（设计中）',
      status: 'blocked',
      note: '阻塞：正式结算域仍为 PROPOSED；当前仅有定价影子快照。',
    },
  ]
})

const currentStep = computed(() => steps.value.find((s) => s.status === 'current') ?? steps.value.find((s) => s.status === 'todo'))

async function load() {
  loading.value = true
  error.value = null
  errorCode.value = null
  try {
    const page = await listAuthorizedWorkOrders({ limit: '50' })
    demoOrders.value = page.items
      .filter(
        (item) =>
          item.externalOrderCode?.startsWith(DEMO_ORDER_PREFIX) ||
          item.externalOrderCode === PILOT_ORDER ||
          item.externalOrderCode?.includes('PILOT') ||
          item.externalOrderCode?.includes('DEMO'),
      )
      .map((item) => ({
        id: item.id,
        code: item.externalOrderCode || item.id,
        status: item.status,
      }))
    if (demoOrders.value.length && !demoOrders.value.some((o) => o.code === selectedCode.value)) {
      selectedCode.value = demoOrders.value[0]!.code
    }
  } catch (err) {
    const facing = toUserFacingError(err)
    error.value = facing.message
    errorCode.value = facing.errorCode
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="golden" data-testid="golden-path-page">
    <header>
      <h1>工单全流程演练</h1>
      <p class="subtitle">
        用一张演示工单贯穿管理端、网点端与师傅端。每一步标明责任角色、所属门户与下一步动作。
      </p>
    </header>

    <PageState v-if="loading" kind="loading" />
    <PageState
      v-else-if="error"
      kind="error"
      :description="error"
      :error-code="errorCode ?? undefined"
      @reload="load"
    />
    <template v-else>
      <section class="current-banner" data-testid="golden-current-action">
        <h2>当前动作</h2>
        <template v-if="currentStep">
          <p><strong>当前环节：</strong>{{ currentStep.name }}</p>
          <p><strong>当前责任角色：</strong>{{ currentStep.role }}</p>
          <p><strong>下一步操作：</strong>{{ currentStep.nextAction }}</p>
          <p><strong>操作入口：</strong>{{ currentStep.entry }}</p>
          <p><strong>所属门户：</strong>{{ currentStep.portal }}</p>
        </template>
        <PageState
          v-else
          kind="empty"
          guide="未找到演示工单。请先到「演示数据管理」初始化，或导入试点工单 ADMIN-PILOT-001。"
        />
      </section>

      <section class="selector">
        <label>
          演示工单
          <select v-model="selectedCode" data-testid="golden-order-select">
            <option v-if="!demoOrders.length" value="ADMIN-PILOT-001">ADMIN-PILOT-001（待初始化）</option>
            <option v-for="order in demoOrders" :key="order.id" :value="order.code">
              {{ order.code }}（{{ statusLabel(order.status) }}）
            </option>
          </select>
        </label>
        <RouterLink
          v-if="demoOrders.find((o) => o.code === selectedCode)"
          class="link-btn"
          :to="{
            name: 'ADMIN.WORKORDER.WORKSPACE',
            params: { id: demoOrders.find((o) => o.code === selectedCode)!.id },
          }"
        >
          打开工单详情
        </RouterLink>
        <RouterLink class="link-btn" to="/system/demo-data">演示数据管理</RouterLink>
      </section>

      <ol class="steps" data-testid="golden-steps">
        <li
          v-for="step in steps"
          :key="step.id"
          :data-status="step.status"
          :data-testid="`golden-step-${step.id}`"
        >
          <div class="step-head">
            <span class="num">{{ step.id }}</span>
            <strong>{{ step.name }}</strong>
            <span class="pill">{{
              step.status === 'done'
                ? '已完成'
                : step.status === 'current'
                  ? '进行中'
                  : step.status === 'blocked'
                    ? '能力阻塞'
                    : step.status === 'optional'
                      ? '按需'
                      : '未开始'
            }}</span>
          </div>
          <p>责任角色：{{ step.role }} · 门户：{{ step.portal }}</p>
          <p>下一步：{{ step.nextAction }}</p>
          <p class="entry">入口：{{ step.entry }}</p>
          <p v-if="step.note" class="note">{{ step.note }}</p>
        </li>
      </ol>

      <section class="legend">
        <h2>能力边界（如实说明）</h2>
        <ul>
          <li>网点接单：分配激活后即可操作，无独立 accept API。</li>
          <li>网点复核写命令：尚未实现，网点可只读与代补资料。</li>
          <li>结算归档：正式结算域未实现，工单完成后仅触发定价影子快照。</li>
          <li>完整家充 20 状态矩阵依赖演示种子；本地可先用 ADMIN-PILOT 单阶段流验证。</li>
        </ul>
        <p class="muted">页面生成时间：{{ formatDateTime(new Date()) }}</p>
      </section>
    </template>
  </section>
</template>

<style scoped>
.golden {
  display: grid;
  gap: 1rem;
}
.subtitle,
.muted,
.note,
.entry {
  color: #627d98;
  font-size: 0.92rem;
}
.current-banner,
.selector,
.legend,
.steps li {
  background: #fff;
  border-radius: 12px;
  padding: 1rem 1.25rem;
  box-shadow: 0 1px 3px rgb(16 42 67 / 8%);
}
.selector {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: end;
}
label {
  display: grid;
  gap: 0.25rem;
  font-size: 0.85rem;
}
select,
.link-btn {
  border: 1px solid #bcccdc;
  border-radius: 6px;
  padding: 0.4rem 0.75rem;
  background: #f0f4f8;
  text-decoration: none;
  color: inherit;
}
.steps {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 0.65rem;
}
.step-head {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
  margin-bottom: 0.35rem;
}
.num {
  width: 1.6rem;
  height: 1.6rem;
  border-radius: 999px;
  background: #d9e2ec;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.85rem;
  font-weight: 700;
}
.pill {
  font-size: 0.75rem;
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  background: #e2e8f0;
}
li[data-status='current'] {
  border-left: 4px solid #0b69a3;
}
li[data-status='done'] {
  border-left: 4px solid #2b8a3e;
}
li[data-status='blocked'] {
  border-left: 4px solid #c92a2a;
}
li[data-status='optional'] {
  border-left: 4px solid #e67700;
}
</style>
