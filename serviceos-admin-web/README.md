# ServiceOS Admin Web（M101～M108）

总部运营后台。当前切片提供：

- 审核/整改/任务/SLA/异常/外发队列与工单/项目目录
- 工单工作区（sections/activity-summary）与 allowed-actions 命令面板
- 本地 JWT；写操作仅调用服务端命令并携带幂等键/版本

## 开发

```bash
npm install
npm run dev
```

## 构建

```bash
npm run build
```

## 明确未实现

设计系统、SavedView、正式 OIDC SDK、表单/资料提交流程编排、Network/Technician、E2E。
