<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert, Button, Tabs, TabPane } from 'ant-design-vue'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import {
  disableSecurityPrincipal,
  enableSecurityPrincipal,
  getSecurityPrincipal,
  listPrincipalIdentityLinks,
  listPrincipalRecentLogins,
  updateSecurityPrincipalProfile,
  type IdentityLink,
  type PrincipalLoginEvent,
  type SecurityPrincipalDetail,
} from '../api/securityPrincipals'
import { listRoleGrants, type RoleGrant } from '../api/authorizationGovernance'
import { listOpenReassignmentWorkItems, type ReassignmentWorkItem } from '../api/organizations'
import { isConflictError, safeAccessDeniedMessage } from '../api/client'
import ImpactPanel from '../components/ImpactPanel.vue'
import VersionedCommandForm from '../components/VersionedCommandForm.vue'
import StatusBadge from '../components/StatusBadge.vue'
import DetailPageLayout from '../patterns/templates/DetailPageLayout.vue'
import { statusLabel } from '../product/statusLabels'
import { formatDateTimeDisplay } from '../presentation/date-time.presenter'

const route = useRoute()
const router = useRouter()
const principalId = computed(() => String(route.params.id ?? ''))

const loading = ref(false)
const busy = ref(false)
const denied = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const detail = ref<SecurityPrincipalDetail | null>(null)
const etag = ref<string | null>(null)
const identities = ref<IdentityLink[] | null>(null)
const recentLogins = ref<PrincipalLoginEvent[] | null>(null)
const recentLoginsError = ref<string | null>(null)
const grants = ref<RoleGrant[]>([])
const reassignments = ref<ReassignmentWorkItem[]>([])
const displayName = ref('')
const employeeNumber = ref('')
const lifecycleReason = ref('ADMIN_USER_CENTER')
const staleVersion = ref<number | null>(null)
const activeTab = ref('basic')

const impactItems = computed(() => {
  if (!detail.value) return []
  const p = detail.value.principal
  return [
    `主体状态将变为 ${statusLabel(p.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE')}`,
    '停用后已签发 JWT 在下次请求立即失败关闭',
    `当前有效 Persona ${detail.value.personas.filter((x) => x.status === 'ACTIVE').length} 条`,
    `相关授权记录 ${grants.value.length} 条（启停不替代撤权）`,
  ]
})

async function load() {
  loading.value = true
  denied.value = false
  error.value = null
  try {
    const result = await getSecurityPrincipal(principalId.value)
    detail.value = result.data
    etag.value = result.etag
    displayName.value = result.data.principal.displayName
    employeeNumber.value = result.data.principal.employeeNumber ?? ''
    try {
      identities.value = await listPrincipalIdentityLinks(principalId.value)
    } catch {
      identities.value = null
    }
    try {
      const loginPage = await listPrincipalRecentLogins(principalId.value, 20)
      recentLogins.value = loginPage.items
      recentLoginsError.value = null
    } catch (err) {
      recentLogins.value = null
      recentLoginsError.value = safeAccessDeniedMessage(err)
    }
    try {
      const grantPage = await listRoleGrants({ principalId: principalId.value })
      grants.value = grantPage.items
    } catch {
      grants.value = []
    }
    try {
      const work = await listOpenReassignmentWorkItems()
      reassignments.value = work.items.filter((item) => item.principalId === principalId.value)
    } catch {
      reassignments.value = []
    }
  } catch (err) {
    detail.value = null
    denied.value = true
    error.value = safeAccessDeniedMessage(err)
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  if (!detail.value) return
  busy.value = true
  message.value = null
  error.value = null
  try {
    const version = staleVersion.value ?? detail.value.principal.version
    await updateSecurityPrincipalProfile(principalId.value, version, {
      displayName: displayName.value.trim(),
      employeeNumber: employeeNumber.value.trim() || null,
    })
    staleVersion.value = null
    message.value = '档案已更新，已重读权威状态'
    await load()
  } catch (err) {
    if (isConflictError(err)) {
      staleVersion.value = null
      await load()
      // load() 会清空 error；冲突提示必须在重读后写回，供运营恢复。
      error.value = '版本冲突（409），请刷新后重试'
    } else {
      error.value = safeAccessDeniedMessage(err)
    }
  } finally {
    busy.value = false
  }
}

async function toggleLifecycle() {
  if (!detail.value) return
  busy.value = true
  message.value = null
  error.value = null
  try {
    const version = detail.value.principal.version
    const body = { reason: lifecycleReason.value.trim() || 'ADMIN_USER_CENTER' }
    if (detail.value.principal.status === 'ACTIVE') {
      await disableSecurityPrincipal(principalId.value, version, body)
    } else {
      await enableSecurityPrincipal(principalId.value, version, body)
    }
    message.value = '生命周期命令已提交，已重读权威状态'
    await load()
  } catch (err) {
    if (isConflictError(err)) {
      error.value = '版本冲突（409），请刷新后重试'
      await load()
    } else {
      error.value = safeAccessDeniedMessage(err)
    }
  } finally {
    busy.value = false
  }
}

async function useStaleVersionForTest() {
  if (!detail.value) return
  busy.value = true
  error.value = null
  try {
    // 确保至少有一次版本推进，才能构造严格小于当前的过期 If-Match。
    if (detail.value.principal.version <= 1) {
      await updateSecurityPrincipalProfile(principalId.value, detail.value.principal.version, {
        displayName: detail.value.principal.displayName,
        employeeNumber: detail.value.principal.employeeNumber,
      })
      await load()
    }
    if (!detail.value) return
    staleVersion.value = detail.value.principal.version - 1
    message.value = `已准备过期 If-Match=v${staleVersion.value}`
  } catch (err) {
    error.value = safeAccessDeniedMessage(err)
  } finally {
    busy.value = false
  }
}

watch(principalId, () => {
  if (principalId.value) void load()
})
onMounted(() => {
  if (principalId.value) void load()
})
</script>

<template>
  <div data-testid="user-detail-page">
    <DetailPageLayout
      :title="detail?.principal.displayName || '用户详情'"
      description="基本信息、组织归属、角色权限、登录与安全及变更相关分区。"
      :eyebrow="detail ? statusLabel(detail.principal.type) : undefined"
    >
      <template #back>
        <Button type="text" aria-label="返回用户目录" @click="router.push({ name: 'ADMIN.USER.DIRECTORY' })">
          <template #icon><ArrowLeftOutlined /></template>
          返回用户目录
        </Button>
      </template>
      <template #status>
        <StatusBadge v-if="detail" :status="detail.principal.status" />
      </template>
      <template #secondary-actions>
        <Button :loading="loading" @click="load">刷新</Button>
      </template>
      <template #feedback>
        <Alert v-if="denied" type="error" show-icon :message="error" data-testid="access-denied" />
        <Alert v-else-if="loading && !detail" type="info" show-icon message="正在加载用户…" />
        <Alert v-else-if="!detail && error" type="error" show-icon :message="error" />
        <Alert v-if="message" type="success" show-icon :message="message" data-testid="command-message" />
        <Alert
          v-if="detail && error && !denied"
          type="error"
          show-icon
          :message="error"
          style="margin-top: 8px"
        />
      </template>

      <template v-if="detail" #summary>
        <p>
          工号 {{ detail.principal.employeeNumber || '—' }} · Persona
          {{ detail.personas.filter((x) => x.status === 'ACTIVE').length }} · 授权
          {{ grants.length }} · 聚合版本 {{ detail.principal.version }}
        </p>
      </template>

      <Tabs v-if="detail" v-model:activeKey="activeTab">
        <TabPane key="basic" tab="基本信息">
          <article class="card" data-testid="section-identity">
            <h3>基本信息</h3>
            <dl>
              <div>
                <dt>姓名</dt>
                <dd data-testid="principal-display-name">{{ detail.principal.displayName }}</dd>
              </div>
              <div>
                <dt>工号</dt>
                <dd>{{ detail.principal.employeeNumber || '—' }}</dd>
              </div>
              <div>
                <dt>状态</dt>
                <dd data-testid="principal-status"><StatusBadge :status="detail.principal.status" /></dd>
              </div>
              <div>
                <dt>类型</dt>
                <dd>{{ statusLabel(detail.principal.type) }}</dd>
              </div>
              <div>
                <dt>版本</dt>
                <dd data-testid="principal-version">{{ detail.principal.version }}</dd>
              </div>
            </dl>
          </article>

          <VersionedCommandForm
            title="编辑档案"
            :version="detail.principal.version"
            :busy="busy"
            submit-label="保存档案"
            hint="成功后重读权威 API；并发冲突返回 409。"
            @submit="saveProfile"
          >
            <label>姓名<input v-model="displayName" aria-label="profile displayName" /></label>
            <label>工号<input v-model="employeeNumber" aria-label="profile employeeNumber" /></label>
            <button
              type="button"
              data-testid="prepare-stale-if-match"
              :disabled="busy"
              @click="() => void useStaleVersionForTest()"
            >
              准备过期 If-Match（E2E）
            </button>
          </VersionedCommandForm>
        </TabPane>

        <TabPane key="org" tab="组织归属">
          <article class="card" data-testid="section-personas">
            <h3>Persona / 归属</h3>
            <ul v-if="detail.personas.length">
              <li v-for="persona in detail.personas" :key="persona.id">
                {{ statusLabel(persona.personaType) }} ·
                <StatusBadge :status="persona.status" /> ·
                {{ formatDateTimeDisplay(persona.validFrom) }}
                <span v-if="persona.validTo"> → {{ formatDateTimeDisplay(persona.validTo) }}</span>
              </li>
            </ul>
            <p v-else class="muted">无 Persona</p>
            <p class="muted">UI_DATA_GAP：组织树任职读模型尚未产品化接入本页。</p>
          </article>
        </TabPane>

        <TabPane key="roles" tab="角色与权限">
          <article class="card" data-testid="section-grants">
            <h3>角色与权限</h3>
            <ul v-if="grants.length">
              <li v-for="grant in grants" :key="grant.grantId">
                {{ grant.roleCode }} · {{ statusLabel(grant.grantStatus) }} /
                {{ statusLabel(grant.grantEffect) }} ·
                {{ statusLabel(grant.scopeType) }}={{ grant.scopeRef }}
              </li>
            </ul>
            <p v-else class="muted">无授权记录或无权读取</p>
          </article>
        </TabPane>

        <TabPane key="security" tab="登录与安全">
          <article class="card" data-testid="section-recent-logins">
            <h3>最近登录</h3>
            <p v-if="recentLoginsError" class="muted">{{ recentLoginsError }}</p>
            <ul v-else-if="recentLogins && recentLogins.length" data-testid="user-recent-login-list">
              <li v-for="item in recentLogins" :key="item.loginEventId">
                {{ formatDateTimeDisplay(item.occurredAt) }}
                · 客户端 {{ item.clientId }}
                · {{ item.authChannel }}
                · {{ statusLabel(item.outcome) }}
                <span class="muted"> · 发行方 {{ item.issuer }}</span>
              </li>
            </ul>
            <p v-else class="muted">尚无成功登录记录（仅记录 OIDC 成功解析）</p>
          </article>

          <article class="card" data-testid="section-identity-links">
            <h3>身份绑定</h3>
            <p v-if="identities === null" class="muted">无 identity.readSensitive，不展示敏感绑定</p>
            <ul v-else-if="identities.length">
              <li v-for="link in identities" :key="link.id">
                发行方 {{ link.issuer }} · 绑定于 {{ formatDateTimeDisplay(link.linkedAt) }}
              </li>
            </ul>
            <p v-else class="muted">无绑定</p>
          </article>

          <VersionedCommandForm
            :title="detail.principal.status === 'ACTIVE' ? '停用用户' : '启用用户'"
            :version="detail.principal.version"
            :busy="busy"
            :submit-label="detail.principal.status === 'ACTIVE' ? '确认停用' : '确认启用'"
            @submit="toggleLifecycle"
          >
            <ImpactPanel :items="impactItems" :obligations="['记录审计原因', '确认未完成工作已交接']" />
            <label>原因<input v-model="lifecycleReason" aria-label="lifecycle reason" /></label>
          </VersionedCommandForm>
        </TabPane>

        <TabPane key="changes" tab="变更记录">
          <article class="card" data-testid="section-reassignment">
            <h3>待重分配</h3>
            <ul v-if="reassignments.length">
              <li v-for="item in reassignments" :key="item.id">
                {{ statusLabel(item.workItemStatus) }} · {{ item.reason }} ·
                {{ formatDateTimeDisplay(item.createdAt) }}
              </li>
            </ul>
            <p v-else class="muted">无 OPEN 待重分配项</p>
            <p class="muted">UI_DATA_GAP：完整变更审计时间线读模型尚未产品化接入本页。</p>
          </article>
        </TabPane>
      </Tabs>
    </DetailPageLayout>
  </div>
</template>

<style scoped>
.card {
  background: #fff;
  border: 1px solid var(--sos-color-border-default, #e5e7eb);
  border-radius: 8px;
  padding: 1rem 1.15rem;
  margin-bottom: 12px;
}
.card h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
dl {
  margin: 0;
  display: grid;
  gap: 0.35rem;
}
dl div {
  display: grid;
  grid-template-columns: 9rem 1fr;
  gap: 0.5rem;
}
dt {
  color: var(--sos-color-text-tertiary, #6b7280);
}
dd {
  margin: 0;
}
ul {
  margin: 0;
  padding-left: 1.1rem;
}
label {
  display: grid;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: #486581;
}
input {
  border: 1px solid #bcccdc;
  border-radius: 6px;
  padding: 0.4rem 0.65rem;
}
.muted {
  color: var(--sos-color-text-tertiary, #6b7280);
}
</style>
