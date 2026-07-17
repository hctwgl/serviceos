package com.serviceos.readmodel.api;

/**
 * M221：Network Portal 工作区薄 SLA 摘要（非 PII）。
 * 仅含本网点 ACTIVE taskIds 上的 open/breached 计数。
 */
public record NetworkPortalWorkOrderWorkspaceSlaSummary(int openCount, int breachedCount) {
    public NetworkPortalWorkOrderWorkspaceSlaSummary {
        if (openCount < 0 || breachedCount < 0) {
            throw new IllegalArgumentException("SLA counts must be non-negative");
        }
    }
}
