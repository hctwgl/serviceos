package com.serviceos.evidence.application;

import com.serviceos.evidence.api.ReviewTimelineContext;
import com.serviceos.evidence.api.ReviewTimelineContextQuery;
import com.serviceos.task.api.TaskTimelineContext;
import com.serviceos.task.api.TaskTimelineContextQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 通过 ReviewCase 权威行上的 taskId 与 Task 公开端口解析工单范围；不加载决定或回执图。
 */
@Service
final class DefaultReviewTimelineContextQuery implements ReviewTimelineContextQuery {
    private final ReviewCaseRepository reviewCases;
    private final TaskTimelineContextQuery tasks;

    DefaultReviewTimelineContextQuery(
            ReviewCaseRepository reviewCases,
            TaskTimelineContextQuery tasks
    ) {
        this.reviewCases = reviewCases;
        this.tasks = tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewTimelineContext> find(String tenantId, UUID reviewCaseId) {
        return reviewCases.findTimelineIdentity(tenantId, reviewCaseId)
                .map(identity -> resolve(tenantId, identity));
    }

    private ReviewTimelineContext resolve(String tenantId, ReviewCaseTimelineIdentity identity) {
        if (identity.taskId() == null) {
            // 无 Task 链接不得猜测工单归属；投影方应忽略。
            return new ReviewTimelineContext(identity.reviewCaseId(), identity.projectId(), null);
        }
        TaskTimelineContext task = tasks.find(tenantId, identity.taskId())
                .orElseThrow(() -> new IllegalStateException("时间线事件引用的 Task 不存在"));
        if (identity.projectId() != null && !identity.projectId().equals(task.projectId())) {
            throw new IllegalStateException("ReviewCase Project 与 Task 权威范围不一致");
        }
        return new ReviewTimelineContext(
                identity.reviewCaseId(), task.projectId(), task.workOrderId());
    }
}
