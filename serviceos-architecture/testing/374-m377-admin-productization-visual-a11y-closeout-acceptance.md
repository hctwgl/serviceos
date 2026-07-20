---
title: M377 Admin 产品化视觉可访问性回归关闭验收
status: Accepted
milestone: M377
lastUpdated: 2026-07-20
---

| ID | 期望 | 证据 |
|---|---|---|
| V1 | 工单中心产品化截图无完整 UUID | admin-productization-visual.spec.ts |
| V2 | AppShell/ScopeBar 可见 | 同上 |
| V3 | check:tokens / unit / build 通过 | npm scripts |
| V4 | 06 status 仍为 Proposed | product/06 frontmatter |
| V5 | OIDC 冒烟 | 环境具备时执行；否则登记未完成 |

## 人工检查清单（已按实现走查）

- [x] 焦点 outline 全局样式存在
- [x] prefers-reduced-motion Token/CSS 存在
- [x] SLA aria-live 仅阶段变化播报（SlaCountdown）
- [ ] 200% 缩放真机人工（本环境未做桌面缩放录屏）
- [ ] 完整读屏旅程人工
