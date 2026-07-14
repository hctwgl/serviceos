package com.serviceos.dispatch.api;

/**
 * 扫描一个到期但未终结的 ServiceAssignment 激活 saga。
 *
 * <p>返回 {@code true} 表示已在同一事务内冻结超时 occurrence 与 Outbox 事件；调用方可继续批量扫描。</p>
 */
public interface ServiceAssignmentTimeoutScanner {
    boolean detectNextTimeout();
}
