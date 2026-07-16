---
title: 应用投影、队列、保存视图与偏好逻辑数据模型
version: 0.1.0
status: Proposed
---

# 应用投影、队列、保存视图与偏好逻辑数据模型

## 1. 原则

- 业务投影可重建，不是第二事实源；
- SavedView/UIPreference 是用户拥有的真实偏好，不是业务事实；
- 投影保存来源版本/checkpoint；
- 查询时仍使用当前 DataScope/FieldPolicy；
- 投影不得保存无必要的明文敏感字段；
- 命令不能更新投影来代替领域聚合。

实现所有权属于 `readmodel` 模块。各领域模块只发布事件/快照导出，不直接写 readmodel 表；authorization 在查询入口编译当前 ScopePredicate/FieldPolicy。

## 2. 投影运行时

### projection_definition

保存 projectionCode、schemaVersion、sourceEventTypes、partitionStrategy、rebuildPolicy、freshnessTarget 和 ownerModule。

### projection_checkpoint

保存 projectionCode、partitionKey、lastEventPosition、lastOccurredAt、processedAt、status（RUNNING/LAGGING/FAILED/REBUILDING）、error 和 rebuildGeneration。

`projection_code + partition_key + rebuild_generation` 唯一。重建使用新 generation，完成验证后原子切换读取 generation；不能先删除当前可用投影。

### projection_dead_letter

保存 eventId、projection、payloadDigest、errorCode、attempt、first/lastFailedAt、handlingTaskId 和 resolution。修复后按 eventId 幂等 replay。

## 3. 工单与工作区投影

### work_order_list_projection

包含列表所需非敏感/脱敏字段：工单/外部号、项目品牌、服务产品、区域、生命周期、当前 Stage/Task、当前网点/师傅引用、SLA 风险、资料/审核/集成/试算摘要、异常严重度、最近事件和 sourceVersions。

索引由真实过滤/排序验证。投影不保存可被直接修改的“总状态”。

### work_order_workspace_projection

按 workOrderId 保存 Header、当前 Task、责任、SLA、异常和 section 可用性摘要。大区块使用独立 projection/document，避免一个巨大 JSON 每次重写。

### work_order_section_projection

键：`work_order_id + section_code + section_version`。保存对象 refs/versions、摘要、asOf 和敏感分类；查询层按 FieldPolicy 解析。

### timeline_projection

保存 normalized timeline item：event/type、occurred/received time、actorRef、resourceRef/version、correlation、display template version 和敏感字段 refs。技术 attempt 可折叠关联。

M73 以 `rdm_work_order_timeline_entry` 实现 WorkOrder/Workflow/Stage/Task 核心事件子集；M74 在同一投影
合并 Appointment/Visit/ContactAttempt；M75 合并 SLA started/breached/met，显式保留 occurred/received
双时间且不保存自由文本、payload 或 PII。Evidence/Review、Delivery、异常、试算/结算、checkpoint 和重建作业
仍是未实现边界。

## 4. 队列投影

### work_queue_definition_version

保存 queueCode、Portal、用途、受控 filter AST、默认排序、显示 Schema、所需 capability、freshness target 和发布版本。发布不可变。

### work_queue_item_projection

保存 queueCode、principalScopeKey/organization/network partition、resourceRef/version、workOrderRef、title template data、severity、dueAt、badges、updatedAt 和 sourceEventPosition。

Queue item 可重建；最终查询仍叠加当前 ScopePredicate。候选角色队列不能把潜在候选人展开为大量重复永久行，按组织/范围分区或查询计算。

### work_queue_count_projection

保存 queueCode/scopeKey/window/count/countType/asOf/checkpoint。计数落后列表时以前者为导航提示，不阻止列表查询。

### workbench_projection

保存主体/角色模板可见的卡片引用、排序和指标摘要。用户个性化只调整允许布局，不改变指标口径。

## 5. 专项投影

| 投影 | 来源 | 内容 |
|---|---|---|
| review_queue_projection | ReviewCase/Task/SLA | target 数、轮次、assignee、due/risk |
| correction_queue_projection | CorrectionCase/Task | 驳回项、最新补传、轮次、责任 |
| dispatch_queue_projection | DispatchRequest/Task | 失败原因、候选数、容量、处理 Task |
| exception_queue_projection | OperationalException/Task | severity、occurrences、owner/due、验证 |
| delivery_queue_projection | Delivery/Attempt/Ack/Task | 状态、UNKNOWN、retry、connector |
| calculation_queue_projection | CalculationRun/Impact | direction/mode/status/difference/guard |
| sla_queue_projection | SlaInstance/Milestone | deadline、remaining/breach、pause、owner Task |
| network_work_projection | ServiceAssignment/Task | 当前网点工单与动作摘要 |
| technician_task_feed_projection | TaskAssignment/Appointment/Sync | 师傅 Feed/tombstone/版本 |

这些投影由拥有领域事件更新；不能由页面直接写入。

## 6. SavedView

### saved_view

```text
saved_view_id
owner_principal_id
portal / page_id
name
visibility（PRIVATE/ROLE/ORGANIZATION）
filter_schema_version / filter_ast
columns / sort / density
shared_scope_ref
version
created_at / updated_at
```

个人 view 只能由 owner 修改。共享 view 需要 capability；它只共享查询定义，不授予字段/数据访问权。

Schema 升级保存 migration result：MIGRATED/PARTIAL/INVALID。无效字段不静默忽略，提示用户修复或重置。

## 7. UserUiPreference

键：`principal + portal + preferenceKey`，保存受控 JSON value、schemaVersion、version 和时间。

允许：主题、密度、语言、列宽、默认 saved view、减少动画。禁止：关闭安全确认、隐藏强制字段、绕过脱敏或禁用事务通知。

## 8. RecentResource

保存 principal、resourceRef、pageId、lastVisitedAt 和非敏感 displayRef。读取时重新鉴权；资源失权后删除/隐藏。不得保存完整用户/地址/价格快照。

## 9. Search 投影

### search_document

保存 tenant、resourceType/id/version、规范化可搜索 token、masked display fields、scope dimensions、sensitivity 和 checkpoint。

- 手机号/VIN 等高敏 token 使用受控哈希/前缀策略；
- 搜索先 tenant/scope 过滤；
- 删除/失权事件及时更新；
- 搜索索引不可用时回退受控精确查询；
- 不能从索引直接执行命令。

## 10. 移动同步投影

### technician_feed_item

保存 technicianId、task/assignment/authority versions、appointment summary、nextActionCodes、workPackageRef、syncState、updatedAt。敏感字段最小化。

### technician_feed_tombstone

保存 technicianId、taskId、invalidatedAt、reasonCode 和 cursorPosition。客户端消费后清除敏感本地工作包；tombstone 保留覆盖最大离线窗口。

### mobile_sync_summary_projection

按 technician/device 保存 pending/processing/conflict/failed 数量与 batch refs，仅用于导航；OfflineCommand 权威结果仍在同步域实体。

## 11. 运营指标投影

### metric_definition_version

保存 metricCode、名称、业务定义、numerator/denominator、来源事件/事实、时间语义、允许维度/筛选、排除规则、数据质量规则、敏感级别、owner 和发布版本。发布后不可变。

### metric_aggregate_projection

按 metricVersion、tenant/project/brand/region/network/业务日期等允许维度、window/granularity 保存 numerator、denominator、value、sampleSize、qualityFlags、asOf 和 checkpoint。金额使用 Decimal/币种/方向/mode，不混合 SHADOW 与正式。

### analytics_query_audit

保存 principal、metricVersions、维度、filter 摘要、范围、返回行数、导出引用、purpose 和时间。敏感筛选值不进入普通日志。

指标 Aggregate 可重建；定义版本和查询/导出审计不可由重建覆盖。

## 12. 安全与保留

- 投影数据库账号只由 projector 写，Portal 查询只读；
- 原始敏感字段尽量通过资源引用按需获取；
- 数据范围变化不能只等待 projector，查询层实时复核；
- saved view/filter 的敏感值按字段策略加密或禁止保存；
- 最近访问和 UI 偏好按账号生命周期清理；
- tombstone、checkpoint 和 dead letter 有明确保留策略；
- 投影导出仍走导出授权和审计。

## 13. 重建与切换

```text
创建 rebuildGeneration
→ 从指定事件/快照重建
→ 数量/抽样/版本/权限验证
→ 原子切换 activeGeneration
→ 观察
→ 清理旧 generation
```

重建期间响应标记 REBUILDING/LAGGING；不能把空表当“没有业务数据”。

## 14. 查询规模验证

- 工单多维筛选与稳定 cursor；
- 审核/异常/SLA dueAt 队列；
- 30 项资料、多轮 revision 的工作区按需加载；
- 9 万+/月基线及增长场景的 timeline/search；
- 改派/授权撤销到查询拒绝的时间；
- Feed 增量/tombstone 覆盖最长离线窗口；
- projection backlog/rebuild 对 API 的降级行为。
