---
title: M265 Technician 资料快照与任务完成交付批次验收矩阵
status: Implemented
milestone: M265
lastUpdated: 2026-07-18
---

# M265 Technician 资料快照与任务完成交付批次验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M265-01 | Snapshot 命令边界 | 只接受 Revision UUID，purpose/摘要/成员事实由服务端生成 | OpenAPI + MVC + clients |
| M265-02 | Context 与责任 | ACTIVE Profile/关系、资源网点和当前责任均实时成立 | service unit + MVC |
| M265-03 | Snapshot 资格 | 只冻结同 Task、VALIDATED、未失效且满足当前槽位/数量/解析代次的成员 | Snapshot PostgreSQL IT |
| M265-04 | 完成命令边界 | 只接受 Snapshot UUID、可选 FormSubmission UUID 和 quoted If-Match | contract + MVC + E2E |
| M265-05 | 服务端规范引用 | resultRef、digest 和 inputVersionRefs 均由服务端重读权威对象构造 | service unit |
| M265-06 | 无表单任务 | 只用 TASK_SUBMISSION Snapshot 完成，额外 FormSubmission 被拒绝 | Task/Evidence IT + service |
| M265-07 | 双输入任务 | FormSubmission + Snapshot 同 Task、精确冻结版本且同事务完成 | DualInputTaskCompletionPostgresIT |
| M265-08 | 状态与并发 | RUNNING/guard/责任/资源版本在 Task 内核再次复核 | Task IT + MVC |
| M265-09 | H5 | 最新 VALIDATED 选择、两段提交与可信字段负向断言 | build + 13 Playwright |
| M265-10 | iOS | Swift 只提交对象 ID，并使用 quoted If-Match 与幂等键 | Foundation smoke + App gates |
| M265-11 | 安全收据 | 不回显 resultRef、digest、inputVersionRefs 或操作者 | MVC + OpenAPI |
| M265-12 | 契约客户端 | OpenAPI 1.0.25 兼容并生成 TS/Swift 客户端 | contract/client gates |
| M265-13 | 模块/全量 | Evidence→公开 Task/Form API 依赖合法且最终 L3 通过 | ArchitectureTest + `verify-local.sh` |

## 明确未验收

Visit checkout 与真实 FieldOperation `operationRefs` 映射、在线整改、完整表单草稿/条件控件、物理设备、弱网重试、
断点/后台/离线上传、生产对象存储/扫描、签名真机、真实 IdP、VoiceOver 和 TestFlight 仍未验收。
