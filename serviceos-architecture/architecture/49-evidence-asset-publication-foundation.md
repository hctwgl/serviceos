---
title: M36 Evidence 资产发布基础
version: 0.1.0
status: Implemented
---

# M36 Evidence 资产发布基础

M36 实现 E3-04 的发布期前置切片，使资料模板能够以不可变版本进入工单锁定的
ConfigurationBundle；本切片不实现 EvidenceSlot、EvidenceRevision、EvidenceSetSnapshot 或审核状态机。

## 1. 已实现边界

- EVIDENCE `schemaVersion=1.0.0` 按架构事实源
  `configuration/schemas/evidence.schema.json` 执行 Draft 2020-12 校验；
- 未登记的 EVIDENCE schemaVersion 失败关闭，非法定义在持久化前拒绝；
- 发布命令的 `assetKey/semanticVersion` 必须与定义中的 `templateKey/version` 完全一致；
- 同一模板内 `evidenceKey` 必须唯一；`capture.minCount` 不得大于 `capture.maxCount`；
- 运行时内嵌 Schema 与架构 Schema 由逐字节漂移测试约束；
- Bundle 可以锁定多个 EVIDENCE 版本，列表解析稳定排序，误用单例读取时报告歧义；
- 已发布资产继续使用现有租户外键、内容摘要和不可变触发器，不新增第二套配置表。

## 2. 可靠性与边界

发布门禁位于 configuration 基础设施适配层，Application/Domain 不依赖 JSON Schema 库。
EVIDENCE 与 FORM 共用既有发布事务、BundleItem 租户约束和摘要校验，因此不存在“校验通过但资产未锁定”
的旁路。M36 不增加 HTTP API、事件、数据库迁移或跨模块依赖。

## 3. 明确未实现

ADR-008 与 ADR-018 仍为 Proposed。M36 只验证表达式对象的结构，不解释或执行
`SERVICEOS_EXPR_V1`，也不宣称质量检查、OCR、审核策略已经可以运行。

后续里程碑至少还需：

1. M37 已按 Task 冻结 Bundle 和 Stage 解析固定 EvidenceSlot；权威字段版本与条件重解析仍未实现；
2. 对接安全文件生命周期，创建不可变 EvidenceItem/EvidenceRevision；
3. 实现 finalize 幂等、机器校验、隔离与可恢复 Worker；
4. 创建并校验不可变 EvidenceSetSnapshot；
5. 在 ADR-008 获批后实现 ReviewDecision、CorrectionCase 和多轮补传闭环。

因此 M36 不关闭 M3 EVD-001～EVD-009，只为这些运行时场景提供可锁定的配置事实。
