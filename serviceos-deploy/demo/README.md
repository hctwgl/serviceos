# ServiceOS 演示数据基线（本地 / 演示环境）

本目录提供可重复初始化、重置、清空的演示数据脚本。**禁止在生产环境执行。**

## 前置条件

1. 本地 Docker Compose 已启动 PostgreSQL / Keycloak（`serviceos-deploy/compose.yaml`）
2. 后端已完成 Flyway 迁移（当前含 `V128` `networkPortal.acceptAssignment`）
3. 已执行本地 Admin 授权：

```bash
docker compose -f serviceos-deploy/compose.yaml exec -T postgres \
  psql -U serviceos_app -d serviceos \
  < serviceos-deploy/keycloak/grant-local-project-admin.sql
```

## 命令

```bash
# 初始化（幂等）：admin-pilot + WO-DEMO-* + 济南网点 Portal 夹具
bash serviceos-deploy/demo/init-demo.sh

# 重置（清空演示标记数据后再初始化）
bash serviceos-deploy/demo/reset-demo.sh

# 仅清空演示标记数据
bash serviceos-deploy/demo/clear-demo.sh
```

演示密码通过环境变量注入（Keycloak 本地默认值如下，**不得写入生产**）：

```bash
export SERVICEOS_DEMO_ADMIN_PASSWORD='local-dev-change-me'
export SERVICEOS_DEMO_NETWORK_PASSWORD='local-dev-change-me'
export SERVICEOS_DEMO_TECHNICIAN_PASSWORD='local-dev-change-me'
```

## 固定演示主体

| 主体 | 值 |
|---|---|
| 车企 | 吉利汽车（GEELY） |
| 项目 | 吉利家充安装试点项目 |
| 服务区域 | 山东省济南市 |
| 服务网点 | 济南恒通新能源服务中心（`d3500000-1000-4000-8000-000000000002`） |
| 师傅 | 张师傅（可登录）、李师傅（仅指派目标） |
| 客户 | 王先生（演示手机号 13800000001） |
| 车辆 | 银河 E5 |
| 工单编号 | `WO-DEMO-20260719-001` … `WO-DEMO-20260719-020` |
| 兼容试点单 | `ADMIN-PILOT-001`（含 `PILOT_SURVEY` READY 任务，适合网点接单联调） |

## 本地账号（三门户同一 Keycloak 用户）

本地 realm 仅预置 `developer`。演示脚本为该主体追加 `NETWORK_MEMBER` / `TECHNICIAN` Persona、
网点成员、师傅档案，以及 NETWORK scope 的 `networkPortal.acceptAssignment` 等能力。

| 门户 | 地址 | 账号 | 密码 |
|---|---|---|---|
| 管理端 | http://localhost:5173 | `developer` | `local-dev-change-me` |
| 网点端 | http://localhost:5174 | `developer` | `local-dev-change-me` |
| 师傅端 | http://localhost:5175 | `developer` | `local-dev-change-me` |

登录后在网点端选择「济南恒通新能源服务中心」上下文；师傅端选择对应师傅上下文。

## 黄金流程手工验收（需人工登录）

自动化已覆盖：工作台稳定性、中文标签单测、接单 PostgresIT / SecurityTest、三门户构建。
**完整跨门户写链路需人工登录 Keycloak**，建议按下列顺序：

1. **管理端** →「工单全流程演练」确认 `ADMIN-PILOT-001` / `WO-DEMO-*` 可见  
2. **管理端** → 工单中心打开 `ADMIN-PILOT-001`，记下 READY 任务 ID  
   （本地种子任务：`70000000-0000-4000-8000-000000000001`，类型 `PILOT_SURVEY`）  
3. **网点端** → 任务页「确认接单」：填入上述 taskId，`businessType` 可用 `INSTALLATION`  
4. **网点端** → 指派师傅（张师傅 / 李师傅）  
5. **师傅端** → 当前任务：查看指派、预约/上门（取决于试点模板已实现能力）  
6. **管理端** → 审核中心 / 整改中心继续平台侧操作  

### 明确仍阻塞（不得伪造成功）

| 环节 | 状态 |
|---|---|
| 网点独立「资料复核」写命令 | 未实现；网点可只读 / 代补 |
| 正式结算归档 | 仍为 PROPOSED；仅有定价影子快照 |
| `WO-DEMO-*` 自带 HUMAN 任务 | 未种子；接单联调优先用 `ADMIN-PILOT-001` |
| PR 全量 CI | 本仓库无通用 PR verify workflow；以本地 L1/L2 为准 |

## 与正式 migration 的隔离

- 演示数据使用固定 UUID 前缀 `d3500000-…` 与 `WO-DEMO-*` 外部编号
- 清空脚本仅删除上述标记数据，不触碰正式业务表结构
- 生产环境默认不包含本目录脚本的自动执行入口
