<script setup lang="ts">
import { Drawer, Descriptions, Empty, Button } from 'ant-design-vue'
import { useDeveloperDiagnostics } from '../composables/useDeveloperDiagnostics'
import { formatDateTimeDisplay } from '../presentation/date-time.presenter'

const diagnostics = useDeveloperDiagnostics()
</script>

<template>
  <Drawer
    :open="diagnostics.open.value"
    title="开发诊断"
    placement="right"
    width="420"
    :destroy-on-close="false"
    data-testid="developer-diagnostics-drawer"
    @close="diagnostics.closeDrawer()"
  >
    <p class="hint">
      仅开发模式或具备诊断能力时可见。正式运营界面不会展示这些技术字段。
    </p>
    <div v-if="diagnostics.lastCorrelationId.value" class="corr">
      最近 correlationId：
      <code>{{ diagnostics.lastCorrelationId.value }}</code>
    </div>
    <Empty
      v-if="diagnostics.entries.value.length === 0"
      description="暂无诊断条目"
    />
    <article
      v-for="entry in diagnostics.entries.value"
      :key="entry.id"
      class="entry"
      :data-testid="`diagnostic-entry-${entry.id}`"
    >
      <header>
        <strong>{{ entry.title }}</strong>
        <small>{{ formatDateTimeDisplay(entry.at) }}</small>
      </header>
      <Descriptions :column="1" size="small" bordered>
        <Descriptions.Item
          v-for="(value, key) in entry.fields"
          :key="String(key)"
          :label="String(key)"
        >
          {{ value == null || value === '' ? '（空）' : String(value) }}
        </Descriptions.Item>
      </Descriptions>
    </article>
    <Button
      v-if="diagnostics.entries.value.length"
      danger
      type="link"
      @click="diagnostics.clearDiagnostics()"
    >
      清空诊断
    </Button>
  </Drawer>
</template>

<style scoped>
.hint {
  margin: 0 0 12px;
  color: var(--sos-color-text-tertiary, var(--sos-color-text-tertiary, #5f6b7a));
  font-size: 12px;
}
.corr {
  margin-bottom: 12px;
  font-size: 12px;
  word-break: break-all;
}
.entry {
  margin-bottom: 16px;
}
.entry header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.entry small {
  color: var(--sos-color-text-tertiary, var(--sos-color-text-tertiary, #5f6b7a));
}
</style>
