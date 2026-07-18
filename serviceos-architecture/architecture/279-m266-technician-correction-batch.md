---
title: M266 Technician 在线资料整改交付批次
status: Implemented
milestone: M266
lastUpdated: 2026-07-18
relatedMilestones: [M45, M47, M50, M51, M53, M135, M257, M264, M265]
---

# M266 Technician 在线资料整改交付批次

## 1. 交付范围

M266 在 H5 与 iOS 交付审核驳回后的在线资料整改闭环，并保持源业务 Task 的终态事实不变：

- Core OpenAPI 1.0.26 新增 Technician 本人整改列表、领取、开始、资料槽位/资料项、Begin/Finalize、
  `CORRECTION_RESUBMISSION` Snapshot 与重新提交端点；
- Review 拒绝仍由既有 CorrectionCase 事务创建独立 `evidence.correction` HUMAN Task。源业务 Task 保持
  `COMPLETED`，不重开、不恢复其历史分派；整改候选人从 `TASK_COMPLETED` 关闭的最后责任链精确派生；
- 专用整改上传与 Snapshot 边界只在 CorrectionCase、整改 Task、源 Task、当前主体和 RESPONSIBLE 分派全部
  一致且整改 Task 为 RUNNING 时放行；普通 Evidence 上传仍严格要求源 Task 为 RUNNING；
- H5/iOS 支持领取、开始、相机/相册/文件选择、SHA-256、Begin、无凭证 PUT、Finalize、等待 VALIDATED、
  Snapshot 和 resubmit，客户端不提交 uploader、offline、源 Task 或整改 Task 等可信字段；
- 同一整改 Task 可承载多轮重新提交。resubmit 不完成整改 Task；只有审核人权威 `CLOSED` 才同事务完成
  整改 Task，并写 `task.handling-completed@v1`；既有 `WAIVED` 继续取消整改 Task。

本批不新增 Flyway，也不新增 Page Registry ID；整改入口属于既有 Technician Feed/Task Detail 产品边界。

## 2. 安全、状态与事务语义

- ACTIVE TechnicianProfile、ACTIVE 网点关系、`task.readAssigned`、资源网点和整改 Task 当前责任实时复核；
- 整改写入只接受 CorrectionCase/Slot/Item/UploadSession/Revision/Snapshot 等资源 ID，服务端重新派生源 Task、
  整改 Task、采集主体、收到时间和 Snapshot purpose；
- 已完成源 Task 的历史责任仅用于派生整改候选人，不恢复为 ACTIVE 权限；REVOKED 或非
  `TASK_COMPLETED` 的失效分派不能成为候选；
- CorrectionCase resubmit/close/waive、Task 状态、分派回收、审计、幂等结果与 Outbox 保持既有事务原子性；
- `task.handling-completed@v1` 记录整改 Task、业务键、结果引用/摘要、完成人和完成时间，不包含文件正文或凭据。

## 3. 工程证据

- `CorrectionCasePostgresIT` 覆盖源 Task 先完成、历史责任回派、多轮整改、关闭完成 Task、WAIVED 取消；
- `TechnicianCorrectionControllerSecurityTest` 与 `ArchitectureTest`；
- Core OpenAPI 40 tests、事件 Schema/样例、兼容正负门禁、TS/Swift 客户端生成与消费者编译；
- Technician H5 build 与 Playwright 整改用例；
- iOS Foundation smoke、SwiftUI/Xcode App 与分发门禁；
- 本里程碑唯一一次最终 L3 `bash scripts/verify-local.sh`。

## 4. 明确未实现

本批不交付审核人移动端关闭/豁免、自动 Evidence target 映射、多候选人/转派策略、通知、缩略图/下载、
生产对象存储/专业扫描、弱网重试、断点/后台/离线队列或冲突合并。

联系/预约写、完整条件/选项/高级表单与草稿、真实 FieldOperation `operationRefs` 签退仍未实现。物理设备、
开发团队签名真机、真实 IdP、VoiceOver 人工听读、崩溃采集和 TestFlight 仍需外部环境验收。下一推进顺序为
Track F 离线工作包与命令队列；FieldOperation checkout 需先接受权威 operationRef 映射事实。
