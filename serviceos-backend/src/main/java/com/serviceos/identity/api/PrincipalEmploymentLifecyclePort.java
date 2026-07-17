package com.serviceos.identity.api;

/**
 * 任职终止触发的主体停用端口，由 identity 模块实现。
 *
 * <p>组织命令在已授权后同事务调用，不要求调用方额外持有 {@code identity.manageLifecycle}。</p>
 */
public interface PrincipalEmploymentLifecyclePort {
    void disableForEmploymentTermination(
            String tenantId, java.util.UUID principalId, String actorId,
            String reason, String correlationId);
}
