---
title: M320 多 OEM 并行建单冒烟验收矩阵
status: Implemented
milestone: M320
lastUpdated: 2026-07-19
---

# M320 多 OEM 并行建单冒烟验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| S320-01 | 同运行时三 OEM 建单 | 3 条 WO；client_code=BYD/GEELY/REFERENCE_OEM | `MultiOemParallelCreateSmokePostgresIT` |
| S320-02 | Canonical 版本 | 含 byd-cpim / geely-haohan / reference-oem-sample | 同上 |
