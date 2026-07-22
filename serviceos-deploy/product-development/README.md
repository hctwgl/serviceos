# 本地产品场景重置

唯一入口：

```bash
cd frontend
pnpm product-data:reset
```

该命令只允许在 `SERVICEOS_ENVIRONMENT=local` 执行。它会销毁并重建本地 PostgreSQL 与
Keycloak 数据卷，执行正式 Flyway，通过正式业务 API 创建项目、履约配置、网点、师傅、资质、
容量和工单。核心业务数据不通过 SQL 绕过领域规则；SQL 只负责本地 OIDC 主体、Persona 与
RoleGrant 的身份授权引导。

## 角色账号

| 角色 | 账号 | 密码 |
|---|---|---|
| 平台管理员 | `developer` | `local-dev-change-me` |
| 只读观察员 | `viewer` | `local-viewer-change-me` |
| 运营专员 | `operator` | `local-operator-change-me` |
| 平台调度 | `dispatcher` | `local-dispatcher-change-me` |
| 质量审核 | `reviewer` | `local-reviewer-change-me` |
| 项目经理 | `project-manager` | `local-project-manager-change-me` |
| 项目助理 | `project-assistant` | `local-project-assistant-change-me` |
| 网点负责人 | `network-manager` | `local-network-manager-change-me` |
| 网点调度 | `network-dispatcher` | `local-network-dispatcher-change-me` |

场景使用稳定中文名称和业务编号。正式产品页面不提供初始化按钮，也不得把这里的本地密码带入
其他环境。
