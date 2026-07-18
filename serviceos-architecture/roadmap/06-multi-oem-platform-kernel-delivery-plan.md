---
title: 多车企平台内核持续交付计划
version: 0.1.0
status: Accepted
lastUpdated: 2026-07-18
---

# 多车企平台内核持续交付计划

## 1. 目标

把 ServiceOS 推进到：新增车企主要依靠 Connector Adapter、Integration Mapping、Project / ServiceProduct、ConfigurationBundle 与版本化配置资产，而不是复制或分叉 WorkOrder / Workflow 核心逻辑。

本计划承接用户批准的阶段一范围，并与 `testing/multi-oem-platform-kernel-program-acceptance.md` 对齐。里程碑号在开工时按当时 `latestMilestone + 1` 领取。

## 2. 阶段一完成标准

```text
BYD 和 OEM2（REFERENCE 或真实）使用独立 Connector
两家车企使用独立 ConfigurationBundle
核心域不存在车企协议判断
历史工单锁定配置版本
第二家车企完成安装全链路（REFERENCE 允许模拟外发）
重复、冲突、驳回、UNKNOWN、乱序回执均有验证
第三家车企可按手册接入
```

## 3. 交付切片顺序

| 顺序 | 切片 | 说明 |
|---|---|---|
| 1 | 多车企平台化验收标准 | 本计划 + 程序级验收矩阵 |
| 2 | 通用 Connector SPI | Envelope → Canonical → 领域命令管道 |
| 3 | BYD 接入通用 Connector 边界 | 行为保持的归位重构 |
| 4 | 配置治理 MVP | 发布前静态校验与依赖闭包加厚 |
| 5 | 条件 Transition | SERVICEOS_EXPR_V1 出边条件 |
| 6 | EXCLUSIVE_GATEWAY | 零/多命中失败关闭 |
| 7 | WAIT_EVENT | 事件关联与幂等唤醒 |
| 8 | 标准家充安装模板 | 平台模板，不绑定单一协议 |
| 9 | REFERENCE_OEM 或真实第二家适配器 | 缺协议处 `TBD_EXTERNAL_CONTRACT` |
| 10 | 双车企端到端回归 | 独立 Bundle + 独立 Connector |
| 11 | 第三家车企接入手册 | 可重复接入路径 |

## 4. 硬约束

- 禁止核心域 `if ("BYD".equals(clientCode))` 类协议分叉；
- 禁止 `BydWorkOrder` / `Oem2WorkOrder` 复制聚合；
- Connector 不得直接写领域表；
- 工单必须锁定 ConfigurationBundle / Manifest Digest / Workflow 版本；
- 已发布配置不可原地修改；
- 配置零/多命中、网关零/多命中、未知节点/表达式、摘要冲突必须失败关闭。

## 5. 与 Track F / 低代码的关系

- `roadmap/05` Track F（iOS 离线）仍 Accepted，但不阻塞本计划；真机签名/TestFlight 为 `BLOCKED_EXTERNAL`。
- 阶段三交付领域配置资产设计器，不自研通用页面低代码平台（见产品宪法 P-01）。

## 6. 当前进度

阶段一声明范围已闭合（真实 OEM2/OEM3 协议仍 BLOCKED_EXTERNAL）。


| 切片 | 状态 | 证据 |
|---|---|---|
| 1～3（M267） | Implemented | ADR-085、`280-m267-*`、`264-m267-*`、BYD 入站 IT、ArchitectureTest |
| 4（M268） | Implemented | workflow condition 对齐表达式、EXCLUSIVE_GATEWAY 静态校验、`281-m268-*` |
| 5～6（M269） | Implemented | EXCLUSIVE_GATEWAY 运行时零/多命中失败关闭、`282-m269-*` |
| 7（M270） | Implemented | WAIT_EVENT 挂起/幂等唤醒、`283-m270-*` |
| 8（M271） | Implemented | 标准家充勘安模板、`284-m271-*` |
| 9（M272） | Implemented | REFERENCE_OEM SAMPLE Connector、`285-m272-*` |
| 10（M273） | Implemented | 双车企入站回归、`286-m273-*` |
| 11（M274） | Implemented | 第三家接入手册、`287-m274-*` |
