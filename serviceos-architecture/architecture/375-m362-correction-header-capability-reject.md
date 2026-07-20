---
title: M362 整改列表/头级客户端能力预检
status: Implemented
milestone: M362
lastUpdated: 2026-07-19
relatedMilestones: [M359, M361, M266, M253]
openapiVersion: "1.0.55"
flywayVersion: "131"
---

# M362 整改列表/头级客户端能力预检

## 目标

关闭 M359/M361 遗留的「整改入口无头级预检」缺口：Technician 整改列表与生命周期投影
对源业务 Task 冻结 Bundle 的 FORM/EVIDENCE 做能力预检软注解，避免师傅在领取/开工/
补传中途才发现不兼容。

## 范围与非目标

- 范围：
  - 复用 `FrozenBundleClientCapabilityProbe`（与 M359 Feed 同构）；
  - `GET /technician/me/corrections` 及 claim/start/resubmit 投影可选
    `clientCapabilityUnsupportedDetail`；
  - Controller 透传 `X-ServiceOS-Client-Kind`；
  - OpenAPI 1.0.55；
  - H5 Feed 展示说明并阻断「查看整改要求」；详情页阻断领取/开工/补传；
  - 单元 + 控制器安全测试。
- 明确不做：
  - claim/start 服务端硬 422（本切片软注解 + 客户端阻断；资料路径仍由 M361 硬拒）；
  - Network Portal on-behalf 整改能力门禁（`NETWORK_WEB` 不在当前 RuntimeGate 强制范围）；
  - iOS 条件执行器 / 全量硬阻断；
  - 派单过滤；REVIEW_TASK 模板分离；
  - UNKNOWN 强制升级；Flyway 新迁移（仍为 131）。

## 事实源

- M359 Feed/详情头能力拒单
- M361 整改资料路径 RuntimeGate
- M266 独立 correction Task
- M253 有界客户端元数据与 UNKNOWN 迁移窗口
- Product/08：进入现场中途执行前失败关闭

## 设计要点

1. 预检对象是**源业务 Task** 冻结 Bundle（与 M361 资料门禁一致），不是 correction Task。
2. 列表只注解、不删项：整改责任仍可见，客户端展示并阻止进入领取/补传。
3. `UNKNOWN`/非师傅端由 Probe 短路跳过（保留 M253）。
4. 不发明 Network Portal 代办语义；不升级 claim/start 为服务端硬拒（避免无 Accepted 设计的状态机副作用）。

## 已实现

- `TechnicianCorrectionView.clientCapabilityUnsupportedDetail`
- `DefaultTechnicianCorrectionService` 注入 Probe；list/claim/start/resubmit 注解
- Controller 透传 `ClientMetadata.KIND_ATTRIBUTE`
- OpenAPI 1.0.55
- H5 Feed/Correction 阻断
- `DefaultTechnicianCorrectionServiceTest` 注解用例
- `TechnicianCorrectionControllerSecurityTest` 透传与字段表面

## 明确未实现

- iOS 条件共用执行器与 catalog 硬阻断翻转；
- 派单级 `supportedClientKinds` 过滤；
- 独立审核 HUMAN Task 模板分离；
- Network Portal on-behalf 能力门禁。

## 工程证据

| 类别 | 证据 |
|---|---|
| OpenAPI | 1.0.55；契约兼容门禁 |
| Flyway | 131（无新迁移） |
| 单元 | `DefaultTechnicianCorrectionServiceTest` |
| 安全 | `TechnicianCorrectionControllerSecurityTest` |
| ArchitectureTest | evidence → configuration::api（既有） |
| H5 | Feed/Correction 能力说明与动作阻断 |

## 验证命令

```bash
bash scripts/agent-verify.sh test DefaultTechnicianCorrectionServiceTest,TechnicianCorrectionControllerSecurityTest
bash scripts/agent-verify.sh arch
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh client-ts
bash scripts/agent-verify.sh docs
```
