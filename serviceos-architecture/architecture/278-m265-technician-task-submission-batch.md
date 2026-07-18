---
title: M265 Technician 资料快照与任务完成交付批次
status: Implemented
milestone: M265
lastUpdated: 2026-07-18
relatedMilestones: [M35, M40, M41, M43, M52, M53, M119, M243, M263, M264]
---

# M265 Technician 资料快照与任务完成交付批次

## 1. 交付范围

M265 在当前责任任务详情上交付 `TASK_SUBMISSION` EvidenceSetSnapshot 和人工任务完成在线闭环：

- Core OpenAPI 1.0.25 新增 Technician 专属 Snapshot 创建和 Task 完成端点；客户端只能提交
  VALIDATED Revision UUID、Snapshot UUID 和可选 FormSubmission UUID；
- 后端先复核 ACTIVE TechnicianProfile、网点关系、`task.readAssigned`、资源网点和当前责任，再固定
  `purpose=TASK_SUBMISSION` 委托既有 Evidence 内核冻结成员、资格和摘要；
- 完成时服务端重新读取 Snapshot/FormSubmission，构造规范 URI、digest 和 `inputVersionRefs`，再由 Task 内核
  复核 RUNNING/guard、乐观版本、当前责任、冻结 FormVersion、Snapshot 最新槽位解析代次和双输入完整性；
- H5 与 iOS 均只选择每个 ACTIVE EvidenceItem 的最新 VALIDATED Revision；若存在冻结表单，则同时选择最新
  VALIDATED FormSubmission。缺少任一必需输入时客户端失败关闭，服务端仍独立重验；
- 完成收据只返回 Task ID、COMPLETED、资源版本和发生时间，不回显内部 resultRef、digest 或输入版本引用。

本批不新增 Flyway。Snapshot、Task 状态、幂等结果、审计和 Outbox 继续由既有事务边界原子提交。

## 2. 安全与事务语义

- 客户端不能指定 Snapshot purpose、member digest、resultRef、resultDigest 或 `inputVersionRefs`；
- Snapshot 与 FormSubmission 必须属于同一当前 Task；无表单任务拒绝额外 FormSubmission，有表单任务拒绝缺失提交；
- `If-Match` 必须是带引号的正资源版本；重复 Idempotency-Key 由内核返回相同结果；
- Snapshot 只接受 VALIDATED 且仍满足当前槽位数量、必填和解析代次的成员，跨任务、重复、失效和隔离成员失败关闭；
- 适配层与 Task/Evidence/Form 内核在同一 Spring 事务中执行，不能在提交后临时拼装成功。

## 3. 工程证据

- `DefaultTechnicianEvidenceServiceTest`、Technician Snapshot/Complete MVC 安全测试；
- `EvidenceSetSnapshotPostgresIT`、`DualInputTaskCompletionPostgresIT`；
- Core OpenAPI 40 tests、兼容正负门禁、TypeScript/Swift 可复现生成与消费者编译；
- Technician H5 build 与 13 条 Playwright；
- iOS Foundation smoke、Simulator/device/Test/Production build、XCTest/XCUITest；
- `ArchitectureTest` 与本里程碑唯一一次最终 L3 `bash scripts/verify-local.sh`。

## 4. 明确未实现

MCP-ONLINE-08 的 Snapshot/Form 双输入完成门禁已交付，但不外推为完整现场提交。Visit checkout 仍未开放：
当前 Accepted 契约没有从 Task/Snapshot 到已完成 FieldOperation `operationRefs` 的权威映射，客户端不得用 Task、
Snapshot 或 Evidence Revision URI 冒充现场操作引用。

本批不交付联系/预约写、完整动态表单草稿、资料整改 UI、真实专业扫描/对象存储、弱网/断点/后台/离线上传、
物理设备、签名真机、真实 IdP、VoiceOver 或 TestFlight。下一 Track E 批次优先实现既有 Review/Correction 事实的
Technician 在线整改；operationRef 映射需先有 Accepted 事实源再实施 checkout。
