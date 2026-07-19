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

4. 三门户本地 OIDC（首次克隆后执行一次，再重启对应 `npm run dev`）：

```bash
cp serviceos-admin-web/.env.development.example \
   serviceos-admin-web/.env.development.local
cp serviceos-network-web/.env.development.example \
   serviceos-network-web/.env.development.local
cp serviceos-technician-web/.env.development.example \
   serviceos-technician-web/.env.development.local
```

若登录页提示「身份接入尚未配置」，通常是缺少上述 `.env.development.local`。

## 命令

```bash
# 初始化（幂等）：admin-pilot + WO-DEMO-* + 济南网点 Portal + 20 态任务
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
| 工单编号 | `WO-DEMO-20260719-001` … `WO-DEMO-20260719-020`（客户名带场景后缀） |
| 兼容试点单 | `ADMIN-PILOT-001`（含 `PILOT_SURVEY` READY 任务） |
| 任务 UUID 前缀 | `d3500000-2000-…`（见 `seed-demo-tasks.sql`） |

## 20 态演示场景（`seed-demo-tasks.sql`）

| 编号 | 场景 | 列表识别 | 演练重点 |
|---|---|---|---|
| 001 | 待初审 | 王先生·待初审 | 工单 RECEIVED |
| 002 | 待分配网点 | 王先生·待分配网点 | 任务 READY、无网点责任 |
| 003 | 网点待接单 | 王先生·网点待接单 | 无 ACTIVE 网点责任 → 网点接单 |
| 004 | 待指派师傅 | 王先生·待指派师傅 | 已有网点责任 → 指派师傅 |
| 005 | 待联系客户 | 王先生·待联系客户 | 网点+张师傅、任务 READY |
| 006 | 待预约 | 王先生·待预约 | 任务 CLAIMED |
| 007 | 待上门 | 王先生·待上门 | 任务 CLAIMED |
| 008 | 勘测中 | 王先生·勘测中 | 任务 RUNNING |
| 009 | 待审核勘测资料 | 王先生·待审核勘测资料 | 任务 COMPLETED（审核写命令另走） |
| 010 | 待安装 | 王先生·待安装 | 安装阶段 READY |
| 011 | 安装中 | 王先生·安装中 | 安装阶段 RUNNING |
| 012 | 待提交完工资料 | 王先生·待提交完工资料 | 安装阶段 RUNNING |
| 013 | 待审核完工资料 | 王先生·待审核完工资料 | 任务 COMPLETED |
| 014 | 整改中 | 王先生·整改中 | CORRECTION 阶段（无伪造整改单） |
| 015 | 已重新提交 | 王先生·已重新提交 | CORRECTION 任务 COMPLETED |
| 016 | 已完成 | 王先生·已完成 | 工单 FULFILLED |
| 017 | 已取消 | 王先生·已取消 | 工单/任务 CANCELLED |
| 018 | SLA即将超时 | 王先生·SLA即将超时 | SLA RUNNING |
| 019 | SLA已超时 | 王先生·SLA已超时 | SLA BREACHED |
| 020 | 运营异常 | 王先生·运营异常 | OPEN 运营异常 |

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

1. **管理端** → 工单中心确认 `WO-DEMO-*` 客户名带场景后缀，且各单有 HUMAN 任务  
2. **网点接单演练**：打开 `WO-DEMO-20260719-003`（网点待接单）→「交给网点」→ 网点端接单  
3. **指派演练**：打开 `WO-DEMO-20260719-004`（已有网点责任）→ 网点端指派张师傅  
4. **师傅端**：登录后应看到已指派场景（如 005～008、010～012、018～019）  
5. **SLA / 异常**：管理端查看 018/019/020  
6. 审核/整改完整写链路仍走真实命令（009/013/014 仅为列表态投影）  

### 明确仍阻塞（不得伪造成功）

| 环节 | 状态 |
|---|---|
| 网点独立「资料复核」写命令 | 未实现；网点可只读 / 代补 |
| 正式结算归档 | 仍为 PROPOSED；仅有定价影子快照 |
| 审核/整改/预约领域完整对象 | 未种子（缺证据快照等前置时禁止灌库） |
| PR 全量 CI | 本仓库无通用 PR verify workflow；以本地 L1/L2 为准 |

## 与正式 migration 的隔离

- 演示数据使用固定 UUID 前缀 `d3500000-…` 与 `WO-DEMO-*` 外部编号
- 清空脚本仅删除上述标记数据，不触碰正式业务表结构
- 生产环境默认不包含本目录脚本的自动执行入口
