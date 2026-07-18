---
title: M247 TypeScript OpenAPI Client 消费基础
status: Implemented
milestone: M247
lastUpdated: 2026-07-18
relatedMilestones: [M12, M246]
---

# M247 TypeScript OpenAPI Client 消费基础

## 范围与证据

- 继续以 Core OpenAPI 为唯一输入，使用固定 OpenAPI Generator `7.22.0` 生成
  `@serviceos/core-client`，不提交或手工维护生成源码；
- 提供受版本控制的最小模板覆盖，使生成包在仓库锁定的 TypeScript 6 下明确 `rootDir`，并兼容
  生成器当前仍输出的 `moduleResolution=node`；
- 新增独立消费门禁：两次干净生成树摘要一致，然后完成 CommonJS/ESM 编译、npm tarball 打包、
  临时消费者安装、类型导入以及 `Configuration`/`DefaultApi` 运行时实例化；
- `bash scripts/agent-verify.sh client-ts` 是本里程碑的统一精准入口；生成包仍不包含 Portal、角色、
  菜单、数据范围或业务默认值假设；
- Core OpenAPI 仍为 `1.0.20`，Flyway 仍为 100/102，本里程碑不改变 HTTP、事件或数据库契约。

## 明确未实现

Swift Client、制品仓库发布/签名/SBOM、Web auth/context/error/trace 封装、设计 Token、独立 Network Web、
Technician H5/iOS，以及 `clientKind`/`clientVersion` 可观测元数据。

## 失败关闭语义

缺少仓库锁定的 TypeScript 编译器、生成树漂移、任一模块格式编译失败、npm 安装失败、类型导入失败或
运行时无法实例化时，门禁均非零退出；不得退化为只校验生成目录存在。
