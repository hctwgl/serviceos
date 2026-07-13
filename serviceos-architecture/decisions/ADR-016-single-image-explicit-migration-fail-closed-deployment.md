# ADR-016：单一镜像、独立迁移与失败关闭发布

- 状态：Accepted
- 日期：2026-07-13

## 背景

应用启动时自动执行 DDL 会把业务进程、数据库高权限凭据和发布时序耦合；分别构建 API、Worker、迁移镜像又可能造成源码或依赖漂移。仅检查“容器启动成功”无法阻止迁移失败后继续替换应用，也无法证明回滚产物与数据库仍兼容。

## 决策

ServiceOS 后端只构建一个不可变 OCI 镜像。该镜像通过受控入口支持 `server` 与 `migrate` 两种模式：迁移作为发布前一次性任务运行，业务进程禁用自动 Flyway 并使用无 DDL 权限的运行账号。

发布按以下顺序失败关闭：

```text
数据库 healthy
→ 同镜像 migrate（退出码必须为 0）
→ 校验实际迁移版本等于 release manifest
→ 替换 server
→ readiness + security smoke
```

构建产物在环境间按 digest 提升，环境差异和 secret 只在运行时注入。应用回滚只允许选择经过数据库/消息向后兼容验证的旧镜像；数据库迁移不做自动向下回滚。

## 约束

- 基础镜像固定多架构 digest；升级时重新执行完整容器验收；
- 正式 staging/production 禁止 tag-only 镜像，必须使用 `name@sha256:...`；
- 迁移账号与运行账号分离，运行账号 DDL 负向测试必须通过；
- 迁移失败或版本不匹配时不得创建/替换新业务容器；
- 镜像不得内置数据库密码、签名密钥或环境配置；
- 容器使用非 root、只读根文件系统、移除 Linux capabilities，并启用 `no-new-privileges`；
- release manifest 至少记录 commit、镜像引用/ID、迁移版本、环境和 smoke 结果；
- 回滚前验证数据库与消息契约兼容，回滚后重新执行 smoke；
- 真实 Secret Manager、镜像签名/SBOM、集群滚动发布和生产审批由部署平台实现，本 ADR 不以本地 Compose 替代它们。

## 后果

迁移失败不能被误判为发布成功，应用账号权限更小，API/Worker/迁移来源一致，回滚路径可重复演练。代价是发布系统必须管理两个数据库角色、显式迁移任务、版本清单及兼容窗口；破坏性数据库变更必须采用 expand/contract。

## 复审触发

- 拆分为多个独立可部署服务且无法继续共享同一后端镜像；
- 部署平台提供经过验证的原生 migration/rollback 编排，需替代 Compose 参考实现；
- Flyway 之外的迁移工具成为事实源；
- 安全基线要求 rootless runtime、镜像签名或 Secret 注入机制发生变化。
