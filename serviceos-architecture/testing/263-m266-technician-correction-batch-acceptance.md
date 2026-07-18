---
title: M266 Technician 在线资料整改交付批次验收矩阵
status: Implemented
milestone: M266
lastUpdated: 2026-07-18
---

# M266 Technician 在线资料整改交付批次验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M266-01 | 源 Task 终态 | Review 拒绝不重开 `COMPLETED` 源 Task | PostgreSQL IT |
| M266-02 | 历史责任回派 | 只从最新 ACTIVE 或因 `TASK_COMPLETED` EXPIRED 的责任链派生候选 | PostgreSQL IT + SQL |
| M266-03 | 独立整改 Task | 创建 `evidence.correction` HUMAN Task 并链接 CorrectionCase | PostgreSQL IT |
| M266-04 | Context 与责任 | ACTIVE Profile/关系、资源网点、能力和整改 Task 当前责任实时成立 | service + MVC |
| M266-05 | 专用上传边界 | 仅 RUNNING 整改 Task 可绕过源 Task RUNNING 门禁，普通上传不放宽 | PostgreSQL IT + service |
| M266-06 | 可信字段 | 源/整改 Task、主体、收到时间、offline 与 purpose 均由服务端派生 | OpenAPI + MVC + E2E |
| M266-07 | Snapshot/重新提交 | 只接受 VALIDATED Revision，并冻结整改 purpose 后 resubmit | PostgreSQL IT + clients |
| M266-08 | 多轮整改 | resubmit 后整改 Task 保持 RUNNING，可继续追加下一轮 | PostgreSQL IT + H5/iOS |
| M266-09 | 权威关闭 | 仅 reviewer close 完成整改 Task、回收分派并发出 handling-completed | PostgreSQL IT + event schema |
| M266-10 | 豁免 | WAIVED 继续取消整改 Task，不伪装为正常完成 | PostgreSQL IT |
| M266-11 | H5 | Feed 整改卡、领取/开始/上传/Snapshot/resubmit 与负向可信字段断言 | build + Playwright |
| M266-12 | iOS | SwiftUI 同闭环，Foundation 请求路径/头/body 受控 | Foundation smoke + Xcode gates |
| M266-13 | 契约客户端 | OpenAPI 1.0.26、事件 Schema 兼容并生成 TS/Swift 客户端 | contract/client gates |
| M266-14 | 模块/全量 | 公开 Task/Evidence API 依赖合法且最终 L3 通过 | ArchitectureTest + `verify-local.sh` |

## 明确未验收

审核人移动端动作、自动整改目标、多候选/转派、通知、生产对象存储/扫描、弱网/后台/离线、完整表单草稿、
真实 FieldOperation `operationRefs` checkout、签名真机、真实 IdP、VoiceOver、崩溃采集和 TestFlight 未验收。
