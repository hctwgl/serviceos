---
title: M53 表单条件与 EvidenceSlot 重解析验收矩阵
version: 1.0.0
status: Implemented
---

# M53 表单条件与 EvidenceSlot 重解析验收矩阵

ADR-022 已接受。下列 P0 场景由 M53 测试与既有不可变历史/作废链路回归共同证明；P1 仍是后续容量与故障注入增强项。

| ID | 优先级 | 场景 | 必须证明的结果 |
|---|---|---|---|
| M53-CFG-001 | P0 | EVIDENCE 引用不存在的 FORM fieldKey | Bundle 发布失败关闭，不产生可运行配置 |
| M53-CFG-002 | P0 | 表达式字段类型与比较字面量不匹配 | 发布期拒绝并定位 rule/fieldKey |
| M53-FRM-001 | P0 | requiredWhen true/false | 服务端使用锁定 FormVersion 得出确定性 VALIDATED/INVALID |
| M53-FRM-002 | P0 | 条件字段缺失 | 明确字段错误，不把缺失解释为 false |
| M53-EVT-001 | P0 | INVALID form.submitted 重复投递 | Inbox 幂等完成，不改变 Evidence generation |
| M53-EVT-002 | P0 | 同一 VALIDATED 事件重复投递 | 只追加一个 generation、一次审计和一个 Outbox 事实 |
| M53-ORD-001 | P0 | submissionVersion 2 先于 1 到达 | revision 2 生效，迟到 revision 1 为 STALE_NO_CHANGE |
| M53-ORD-002 | P0 | form.submitted 先于 task.created 消费 | 不回退到基础事实 revision 0，最终投影确定 |
| M53-CON-001 | P0 | 两个 VALIDATED submission 并发消费 | PostgreSQL 锁与唯一约束保证 generation 单调且无重复 |
| M53-SLOT-001 | P0 | false→true | 创建新槽位世代并保存 lineage，不恢复历史槽位 |
| M53-SLOT-002 | P0 | true→true | 沿用活动槽位及其 EvidenceItem，不要求无意义重传 |
| M53-SLOT-003 | P0 | true→false 且无资料 | 最新 generation 不再把该槽位视为必需 |
| M53-SLOT-004 | P0 | true→false 且已有资料 | 历史不删除、不自动作废文件，进入 REVIEW_REQUIRED |
| M53-GATE-001 | P0 | 存在 REVIEW_REQUIRED | Snapshot 创建和 CompleteTask 均失败关闭 |
| M53-DSP-001 | P0 | 授权处置为保留 | 精确 generation/slot 审计后解除门禁，历史不变 |
| M53-DSP-002 | P0 | 授权处置为作废 | 复用 Evidence/File 作废链路，审计与副作用同事务 |
| M53-HIS-001 | P0 | 重解析发生在旧 Snapshot/Review 后 | 旧 Snapshot、ReviewDecision 和回执逐字节不变 |
| M53-MOD-001 | P0 | 模块边界 | evidence 仅依赖 forms 公开 API，不访问 `frm_*` 或内部 Repository |
| M53-SEC-001 | P0 | 事件、日志和 Trace 检查 | 不包含完整 values、个人信息或文件 URL |
| M53-DB-001 | P0 | V052→V053 与空库迁移 | generation 回填正确，临时默认移除，不保留双写 |
| M53-REC-001 | P1 | Consumer 在提交后崩溃并恢复 | Inbox/Outbox 重试后只形成一次权威结果 |
| M53-PERF-001 | P1 | 单 Task 多次提交与批量 Task | 索引命中，串行范围仅限单 Task resolution stream |

## 自动化证据映射

| 场景 | 证据入口 |
|---|---|
| CFG-001/002 | `ConfigurationPublicationPostgresIT`、`ServiceOsExprV1EvaluatorTest` |
| FRM-001/002 | `FormValueValidatorTest` |
| EVT-001/002、ORD-001/002 | `EvidenceSlotPostgresIT` 的 INVALID 重放、迟到 submission 与 task.created 乱序场景 |
| CON-001、SLOT-001～004 | `EvidenceSlotPostgresIT` + V053 Task stream advisory lock、generation/fact/slot 唯一约束 |
| GATE-001、DSP-001 | `EvidenceSlotPostgresIT` 的未决完成门禁与 KEEP 精确代次处置 |
| DSP-002 | `DefaultEvidenceConditionDispositionService` 复用 `EvidenceRevisionInvalidationPostgresIT` 已证明的 Evidence/File 同事务作废链路；未终态资料失败关闭 |
| HIS-001 | V053 不可变触发器 + `EvidenceSetSnapshotPostgresIT`、Review/ExternalReceipt 回归测试 |
| MOD-001 | `ApplicationModules.verify()`、`ArchitectureTest`；evidence 仅声明 `forms::api` |
| SEC-001 | `evidence.slots-reresolved@v1`、`evidence.condition-disposition-recorded@v1` Schema/fixture 契约测试；事件不含 values 或 URL |
| DB-001 | PostgreSQL 18 Testcontainers 空库连续迁移到 V053（55 migrations）及 V053 回填 DDL |

P1 的 REC/PERF 不属于 M53 Implemented 声明范围；进入批量生产准备前必须补充故障注入与容量基准。
