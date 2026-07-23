---
title: ServiceOS 验证执行策略
status: Accepted
lastUpdated: 2026-07-23
---

# ServiceOS 验证执行策略

## 1. 目标

在不降低 PostgreSQL、契约、安全、模块边界和 staging 门禁的前提下，减少开发迭代中重复执行全量验证的时间与计算消耗。

本策略区分两类目标：

- **开发反馈**：尽快证明当前修改和直接修复正确；
- **最终验收**：证明最终候选 HEAD 满足全部适用门禁。

“最终候选必须完整绿色”不等于“每个局部修复后都重新执行全量 verify”。

## 2. 标准执行顺序

```text
静态 Preflight
→ 新功能与直接受影响测试
→ 相邻模块专项回归
→ 冻结最终候选 HEAD
→ 完整 verify 一次
→ 适用客户端生成与 staging 演练
```

### 2.1 静态 Preflight

执行：

```bash
bash scripts/verify-repository-preflight.sh
```

必须在 Maven、Testcontainers 和镜像构建之前发现：

- Flyway 最高版本和迁移数量漂移；
- 测试仍断言旧 Flyway 版本或旧迁移数量；
- staging 发布清单与迁移目录不一致；
- Maven、pnpm、Swift、部署关键工程入口缺失；
- 已删除的独立 Web/Web Core 根目录被重新引入；
- 根目录、契约、部署脚本的 Bash/Node 语法错误；
- Git 空白错误。

Web Workspace 的统一门禁执行：

```bash
bash scripts/agent-verify.sh frontend
```

该入口必须覆盖 Admin、Network、Technician 三个应用以及全部 `@serviceos/*` 共享包；不得只验证
Admin 的依赖闭包后宣称整个 Workspace 已通过。

### 2.2 精准验证

每个逻辑小步只执行直接相关的单元测试、PostgreSQL IT、契约检查或 ArchitectureTest。修复失败后先重新执行直接失败测试，不得为了获得“全绿观感”重复运行无关全量测试。

### 2.3 最终候选

准备运行全量门禁前，应完成代码、迁移、测试、契约、部署清单和必要文档同步，形成稳定 HEAD。全量验证开始后，除修复真实失败外，不再追加无关文档、格式化或重构。

### 2.4 完整门禁

R3 或发布候选时，对最终候选 HEAD 至少执行一次完整 verify，以及适用的客户端生成、staging、Smoke、回滚和恢复演练。

完整门禁失败时：

1. 一次性收集该轮全部失败；
2. 按共同根因归类并批量修复；
3. 精准重跑全部受影响测试；
4. 形成新的稳定候选 HEAD；
5. 再执行一次完整门禁。

禁止按“发现一个失败、修一个、全量一次”的方式串行消耗流水线。

## 3. CI 去重规则

- 功能分支只通过 `pull_request` 运行完整验证；
- `master` 合并后通过 `push` 再运行一次；
- 同一 PR 的新提交会取消旧提交仍在运行的验证；
- staging 只在主验证通过后执行；
- 被取消或已过期 SHA 的绿色结果不得作为当前候选证据。

## 4. 迁移基线唯一来源

Flyway 当前版本和迁移总数从以下目录自动推导：

```text
serviceos-backend/src/main/resources/db/migration/
```

统一入口：

```bash
bash scripts/migration-baseline.sh
```

测试可以保留“当前版本/数量”断言，但 Preflight 会在数字落后于迁移目录时立即失败。staging 环境生成器必须调用同一入口，禁止维护独立数字常量。

## 5. 完成标准

最终报告必须区分：

- 已通过的精准验证；
- 最终候选 HEAD 的完整验证；
- 未运行的门禁及原因；
- 失败后是否重新冻结候选；
- 当前结果对应的准确提交 SHA。

不得把“新功能测试通过”表述为整个产品或能力已经完成，也不得因为最终要求完整绿色而在每个局部修复后重复全量验证。
