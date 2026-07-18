---
title: 多车企平台内核程序级验收矩阵
version: 0.1.0
status: Accepted
lastUpdated: 2026-07-18
---

# 多车企平台内核程序级验收矩阵

本矩阵验收阶段一整体能力，不因单个里程碑 IMPLEMENTED 而自动勾选全部行。

| 编号 | 场景 | 预期 | 当前 |
|---|---|---|---|
| MOK-01 | 多车企验收标准成文 | 交付计划与本矩阵 Accepted | PASS |
| MOK-02 | 通用 Connector SPI | Adapter → Envelope → Canonical → 领域命令；无空接口 | PASS（M267） |
| MOK-03 | BYD 使用通用边界 | BYD CREATE_WORK_ORDER 委托 SPI/管道；外部契约不变 | PASS（M267） |
| MOK-04 | 核心域无车企协议分叉 | 架构门禁阻止核心模块依赖 `integration.byd` / 未来 OEM 适配包 | PASS（M267） |
| MOK-05 | 配置治理 MVP | 发布前依赖/条件静态校验失败关闭 | 后续 |
| MOK-06 | 条件 Transition | SERVICEOS_EXPR_V1 求值；非法表达式失败关闭 | 后续 |
| MOK-07 | EXCLUSIVE_GATEWAY | 唯一 true 出边推进；零/多命中失败关闭 | 后续 |
| MOK-08 | WAIT_EVENT | 关联键幂等唤醒；重复事件不双推进 | 后续 |
| MOK-09 | 标准家充安装模板 | 平台 Bundle 模板可发布；不嵌入 OEM DTO | 后续 |
| MOK-10 | REFERENCE_OEM / 第二家 | 独立 Connector + Bundle；不确定协议 `TBD_EXTERNAL_CONTRACT` | 后续 |
| MOK-11 | 双车企 E2E | BYD + OEM2 安装链路；重复/冲突/驳回/UNKNOWN/乱序有证据 | 后续 |
| MOK-12 | 第三家接入手册 | 手册步骤可执行且引用 SPI/配置资产 | 后续 |
| MOK-13 | 历史工单配置锁定 | 新发布不影响在途工单冻结版本 | 既有 M16+；回归保持 |

## 失败关闭不变量

以下任一情况不得静默成功：

- 配置零命中 / 多命中
- 网关零命中 / 多命中
- 未知节点 / 未知表达式
- 引用缺失 / 摘要不一致
- 跨租户引用
- 重复业务键但正文冲突
