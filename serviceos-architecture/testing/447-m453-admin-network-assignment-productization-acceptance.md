---
title: M453 Admin 责任网点候选与分配产品化验收矩阵
status: Implemented
milestone: M453
---

| 编号 | 场景 | 预期 |
|---|---|---|
| M453-01 | 有 `dispatch.read` 且 Task 属于可见项目 | 返回经过项目、启用状态、覆盖、业务类型、容量和冻结策略过滤排序的网点候选 |
| M453-02 | 用户缺少 `dispatch.read` 或 Project Scope 不匹配 | 查询拒绝并记录拒绝审计，不泄露候选和网点名称 |
| M453-03 | 网点不属于项目、已停用、无覆盖或无可用容量 | 网点不进入可分配候选，不以默认网点或自动扩容兜底 |
| M453-04 | 冻结派单配置要求人工处理或没有合格候选 | 返回中文可恢复空态，不伪造推荐结果 |
| M453-05 | 用户提交当前合格候选 | 服务端重新评估后预占现有容量并激活 NETWORK 责任，保持 saga、审计和幂等语义 |
| M453-06 | 查询后候选状态、覆盖或容量变化 | 命令重新评估并拒绝过期候选，不产生 ServiceAssignment 或容量变更 |
| M453-07 | 请求 businessType 与工单权威服务产品不一致 | 失败关闭并返回中文业务错误，不使用客户端值参与责任激活 |
| M453-08 | Admin 打开责任网点分配区 | 展示中文网点名称、覆盖、容量与推荐理由，不展示演示 ID、自由业务类型或跳过网点接单操作 |
| M453-09 | 分配成功 | 页面刷新权威责任信息并以中文反馈，不在普通提示中暴露 UUID |
| M453-10 | Network Portal 既有接单与派师傅流程 | 行为保持不变，仍消费同一权威 NETWORK/TECHNICIAN 责任状态 |
| M453-11 | Admin 或 API 调用方尝试同时指派网点与师傅 | 历史双责任 HTTP 路径与请求 Schema 不再存在；只能先派网点，再由可信网点上下文指派师傅 |
| M453-12 | 责任记录、Task 与现场操作使用师傅标识 | TECHNICIAN 责任只保存师傅档案 ID；登录主体经权威目录解析后执行本人校验，不双读旧主体责任标识 |
| M453-13 | 师傅档案暂未绑定有效登录主体 | 工单摘要等只读页面仍可展示责任；预约和现场作业命令失败关闭，不伪造主体 |

## 自动化证据

| 验收范围 | 证据 |
|---|---|
| M453-01～04、06～07 | `DispatchPolicyServiceAssignmentPostgresIT` |
| M453-02 | `NetworkAssignmentCandidateControllerSecurityTest` |
| M453-05、10～11 | `ManualServiceAssignmentPostgresIT`、`NetworkPortalAcceptAssignmentPostgresIT`、`ManualServiceAssignmentControllerSecurityTest`、OpenAPI 契约门禁 |
| M453-08～09 | `network-assignment-productization.test.mjs`、`admin-productization-smoke.spec.ts`、Admin 生产构建 |
| M453-10、12～13 | `AppointmentPostgresIT`、`VisitPostgresIT`、`TechnicianPortalFeedPostgresIT`、`WorkOrderWorkspacePostgresIT`、Network Portal 整改/异常/工作台 PostgreSQL IT |
| 契约与模块边界 | Core OpenAPI 兼容门禁、TS/Swift 6 客户端门禁、`ArchitectureTest` |
| 真实产品链路 | Admin Pilot 入站工单完整链路、预约与上门链路（真实 OIDC + Backend + PostgreSQL + Playwright） |

完整 L3 `bash scripts/verify-local.sh` 已通过，Backend 与 Contracts Reactor 均为 `SUCCESS`。
