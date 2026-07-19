# ServiceOS 演示数据基线（本地 / 演示环境）

本目录提供可重复初始化、重置、清空的演示数据脚本。**禁止在生产环境执行。**

## 前置条件

1. 本地 Docker Compose 已启动 PostgreSQL（`serviceos-deploy/compose.yaml`）
2. 后端已完成 Flyway 迁移
3. 建议先完成 Keycloak 本地用户与 Capability 授权（见 `serviceos-deploy/keycloak/`）

## 命令

```bash
# 初始化（幂等）
bash serviceos-deploy/demo/init-demo.sh
# 等价于依次加载 admin-pilot seed + seed-demo-orders.sql

# 重置（清空演示标记数据后再初始化）
bash serviceos-deploy/demo/reset-demo.sh

# 仅清空演示标记数据
bash serviceos-deploy/demo/clear-demo.sh
```

演示密码通过环境变量注入，例如：

```bash
export SERVICEOS_DEMO_ADMIN_PASSWORD='local-dev-change-me'
export SERVICEOS_DEMO_NETWORK_PASSWORD='local-dev-change-me'
export SERVICEOS_DEMO_TECHNICIAN_PASSWORD='local-dev-change-me'
```

不得把真实密码写入仓库。

## 固定演示主体

| 主体 | 值 |
|---|---|
| 车企 | 吉利汽车（GEELY） |
| 项目 | 吉利家充安装试点项目 |
| 服务区域 | 山东省济南市 |
| 服务网点 | 济南恒通新能源服务中心 |
| 师傅 | 张师傅、李师傅 |
| 客户 | 王先生（演示手机号 13800000001） |
| 车辆 | 银河 E5 |
| 工单编号 | `WO-DEMO-20260719-001` … `WO-DEMO-20260719-020` |
| 兼容试点单 | `ADMIN-PILOT-001`（由 admin-pilot seed 提供） |

## 演示账号说明

账号主体由 Keycloak / Identity 模块管理。本脚本只保证业务主数据与工单投影；
门户登录账号请使用本地开发文档中的运营/网点/师傅账号，并确保具备对应 Portal Context。

## 与正式 migration 的隔离

- 演示数据使用固定 UUID 前缀 `d3500000-…` 与 `WO-DEMO-*` 外部编号
- 清空脚本仅删除上述标记数据，不触碰正式业务表结构
- 生产环境默认不包含本目录脚本的自动执行入口
