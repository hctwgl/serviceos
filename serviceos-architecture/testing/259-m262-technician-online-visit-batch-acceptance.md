---
title: M262 Technician 在线 Visit 交付批次验收矩阵
status: Implemented
milestone: M262
lastUpdated: 2026-07-18
---

# M262 Technician 在线 Visit 交付批次验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M262-01 | Technician 命令边界 | 不接受 tenant/network/technician/receivedAt，签到固定在线 | OpenAPI + MVC |
| M262-02 | Context | ACTIVE Profile/网点关系与资源网点一致，伪造 Context 失败关闭 | PostgreSQL IT + MVC |
| M262-03 | 当前责任 | 改派、撤权或失效关系后不能写 Visit | 复用 VisitService responsibility/capability tests |
| M262-04 | 幂等 | check-in header key 等于 deviceCommandId，重复请求复用结果 | VisitService PostgreSQL IT + contract |
| M262-05 | 并发 | check-out/interrupt 使用带引号的正整数 If-Match | MVC + H5/iOS request assertions |
| M262-06 | H5 签到 | 用户点击后只采集一次浏览器位置，并明确非原生可信度 | Playwright |
| M262-07 | H5 中断 | 仅发送已接受异常码和显式空 evidenceRefs | Playwright |
| M262-08 | iOS 当前任务 | 原生 Feed/详情消费当前 Technician Context | Foundation smoke + App build |
| M262-09 | iOS 定位签到 | 用户动作触发一次性 CoreLocation；无持续/后台定位 | source gate + Simulator build |
| M262-10 | iOS 中断 | quoted If-Match、幂等键与空 evidenceRefs 正确 | Foundation smoke |
| M262-11 | 隐私 | 精确位置与 Device ID 声明为关联用户的 App Functionality，不 tracking | PrivacyInfo + distribution gate |
| M262-12 | 签退边界 | 无真实 operationRefs 时 H5/iOS 不展示伪签退 | UI source/E2E assertions |
| M262-13 | 契约客户端 | OpenAPI 1.0.22 可校验、兼容并重复生成 TS/Swift 客户端 | contract/client gates |
| M262-14 | 模块/全量 | Fieldwork 依赖合法且最终 L3 通过 | ArchitectureTest + `verify-local.sh` |

## 明确未验收

联系人/预约写、动态表单、Evidence、真实 operationRefs 签退、整改、离线工作包和真机定位不在已完成范围。
真实 IdP、签名真机、VoiceOver 与 TestFlight 仍需外部材料和真实设备证据。
