package com.serviceos.task.spi;

/**
 * 自动任务执行器扩展点。实现必须按 taskId/attemptId 保持业务副作用幂等。
 */
public interface AutomatedTaskHandler {
    String taskType();

    TaskExecutionResult execute(TaskExecutionContext context) throws Exception;
}
