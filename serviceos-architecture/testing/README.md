# 测试与验收入口

本目录不再保存逐切片验收矩阵。可自动化的验收必须靠近实现并由测试直接证明；产品验收边界位于 `product-design/`。

## 工程证据

- 后端单元/集成测试：`serviceos-backend/src/test/java/com/serviceos/<module>/`
- Spring Modulith 边界：`serviceos-backend/src/test/java/com/serviceos/ArchitectureTest.java`
- OpenAPI/事件契约：`serviceos-contracts/src/test/` 与 `serviceos-contracts/scripts/`
- Web 静态检查、单测和构建：`corepack pnpm --dir serviceos-frontend check`
- Technician iOS：`serviceos-technician-ios/Tests/` 与 `scripts/verify-technician-ios-*.sh`
- staging/smoke/回滚：`serviceos-deploy/staging/`

SQL、Flyway、锁、唯一约束、Inbox/Outbox、claim/lease/retry 和授权范围必须由真实 PostgreSQL 或适用安全测试证明，不能用 Markdown 勾选代替。

## 产品验收

产品目标、页面边界和核心旅程见 `serviceos-architecture/product-design/`。自动化测试通过不等于产品已接受；真实环境、真实身份、浏览器/设备和人工产品评审证据必须按当前任务保存到外部验收记录或 PR，而不是在仓库累积永久截图和逐页面总结。

## 新增测试的原则

- 测试名称表达业务行为和失败条件；
- 与实现放在同一模块测试目录；
- 修改机器契约时同步契约测试；
- 数据库语义使用 PostgreSQL Testcontainers；
- 授权同时覆盖允许、拒绝和拒绝审计；
- 可靠消息覆盖重复、崩溃恢复、租约和最终失败；
- 不删除失败测试、放宽核心断言或用 Mock 替代必须证明的基础设施行为。
