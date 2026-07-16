# ServiceOS Admin Web（M101～M105）

总部运营后台只读外壳。当前切片提供：

- Portal shell 与 Page ID 路由：`ADMIN.REVIEW.QUEUE`、`ADMIN.CORRECTION.QUEUE`、
  `ADMIN.INTEGRATION.OUTBOUND`、`ADMIN.EXCEPTION.QUEUE`、`ADMIN.WORKORDER.WORKSPACE`
- 调用后端 M85～M100 工作区与专项队列 API
- 本地保存 JWT，不在前端做 tenant/capability 判定

## 开发

```bash
npm install
npm run dev
```

默认通过 Vite 代理访问 `http://127.0.0.1:8080/api`。可在「访问令牌」页粘贴 JWT。

## 构建

```bash
npm run build
```

## 明确未实现

设计系统完整 Token、SavedView、正式 OIDC SDK、表单/资料提交流程编排、Network/Technician 端、E2E。
