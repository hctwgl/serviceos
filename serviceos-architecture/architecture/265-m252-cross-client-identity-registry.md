---
title: M252 多端 Page/Feature/Action 机器注册表
status: Implemented
milestone: M252
lastUpdated: 2026-07-18
relatedMilestones: [M188, M247, M248, M251]
---

# M252 多端 Page/Feature/Action 机器注册表

## 范围与证据

- 新增版本化 `serviceos-client-identities-v1.json`，登记后端当前 33 个稳定 Page ID、已接受且默认关闭的
  `FORMAL_SETTLEMENT` Feature ID，以及 Core OpenAPI 已发布的 11 个 Action Code；
- 从单一 JSON 源确定性生成 TypeScript 常量/联合类型和 Swift 6 常量集合，不在注册表中放菜单、路由组件、
  角色授权判断、动作参数或展示文案；
- 两端生成物均提供 `isKnownActionCode` / `filterKnownActionCodes`：旧客户端遇到服务端新动作时只隐藏未知值，
  不猜测命令地址、请求体、授权或成功语义；
- Contract Test 对齐 Core OpenAPI 中 Task、Appointment、Visit、OperationalException 的公开 action enum；
- Backend Test 对齐 M188 `CodePageRegistry` 与机器注册表，任何新增、删除或拼写漂移都失败；
- `agent-verify.sh client-identities` 执行两次生成摘要、TypeScript strict 负向探针、Swift 6 strict 负向探针及
  Java 契约/后端对齐测试；
- Core OpenAPI 仍为 `1.0.20`，Page Registry 运行时 catalog 仍为 `page-registry-v16`，Flyway 仍为 100/102。

## Feature ID 边界

当前工程唯一具备 Accepted 语义的 Feature Gate ID 是 `FORMAL_SETTLEMENT`，且仍为二期预留并默认关闭。
本切片只登记身份，不启用该能力、不增加结算页面，也不把 Feature ID 当 Capability 或前端授权结果。

## 明确未实现

clientKind/clientVersion、支持能力协商、生成制品发布、独立 Network/H5/iOS App 实际导入、运行时 Feature
目录 API、正式结算、未知 schema 处理，以及任何新业务动作。
