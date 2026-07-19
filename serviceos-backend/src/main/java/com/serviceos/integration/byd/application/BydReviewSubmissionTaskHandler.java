package com.serviceos.integration.byd.application;

import com.serviceos.integration.application.OutboundSubmissionPipeline;
import com.serviceos.task.spi.AutomatedTaskHandler;
import com.serviceos.task.spi.TaskExecutionContext;
import com.serviceos.task.spi.TaskExecutionResult;
import org.springframework.stereotype.Service;

/**
 * BYD 提审自动任务入口：委托通用 {@link OutboundSubmissionPipeline}，
 * 协议差异由 {@link BydOutboundSubmissionConnector} 承担。
 *
 * <p>UNKNOWN 绝不抛 RETRYABLE；只有已经持久化技术接受后的本地 Case/Route 落账可以安全重试。</p>
 */
@Service
final class BydReviewSubmissionTaskHandler implements AutomatedTaskHandler {
    private final OutboundSubmissionPipeline pipeline;
    private final BydOutboundSubmissionConnector connector;

    BydReviewSubmissionTaskHandler(
            OutboundSubmissionPipeline pipeline,
            BydOutboundSubmissionConnector connector
    ) {
        this.pipeline = pipeline;
        this.connector = connector;
    }

    @Override
    public String taskType() {
        return BydOutboundSubmissionConnector.TASK_TYPE;
    }

    @Override
    public TaskExecutionResult execute(TaskExecutionContext context) throws Exception {
        return pipeline.execute(context, connector);
    }
}
