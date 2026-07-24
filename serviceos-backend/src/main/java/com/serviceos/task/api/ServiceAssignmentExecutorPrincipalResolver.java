package com.serviceos.task.api;

/**
 * ServiceAssignment 责任标识到 Task 执行主体的解析端口。
 *
 * <p>Task 只认识可登录的 principal；具体责任档案归属其他模块，由实现方失败关闭地解析。</p>
 */
public interface ServiceAssignmentExecutorPrincipalResolver {
    String requireActivePrincipalId(String tenantId, String assigneeId);
}
