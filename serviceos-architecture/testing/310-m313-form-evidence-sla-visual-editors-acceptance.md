---
title: M313 FORM/EVIDENCE/SLA 可视配置器验收矩阵
status: Implemented
milestone: M313
lastUpdated: 2026-07-19
---

# M313 FORM/EVIDENCE/SLA 可视配置器验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| S313-01 | FORM 改 title + 加字段 | JSON 含 title 与 field_ | Playwright M313 |
| S313-02 | EVIDENCE 加 item | JSON 含 item_ | Playwright M313 |
| S313-03 | SLA 改 duration | JSON 含 7200 | Playwright M313 |
| S313-04 | Admin build | vue-tsc + vite 成功 | `npm run build` |
