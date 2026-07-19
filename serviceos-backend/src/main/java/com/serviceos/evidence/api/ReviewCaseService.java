package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.List;
import java.util.UUID;

/** ReviewCase / ReviewDecision 命令与查询端口。 */
public interface ReviewCaseService {
    ReviewCaseView create(CurrentPrincipal principal, CommandMetadata metadata, CreateReviewCaseCommand command);

    ReviewCaseView createClient(
            CurrentPrincipal principal, CommandMetadata metadata, CreateClientReviewCaseCommand command);

    DecideReviewCaseResult decide(
            CurrentPrincipal principal, CommandMetadata metadata, DecideReviewCaseCommand command);

    ReviewCaseView forceApprove(
            CurrentPrincipal principal, CommandMetadata metadata, ForceApproveReviewCaseCommand command);

    ReviewCaseView reopen(CurrentPrincipal principal, CommandMetadata metadata, ReopenReviewCaseCommand command);

    ReviewCaseView get(CurrentPrincipal principal, String correlationId, UUID reviewCaseId);

    /** 按 Task 列出审核案例；要求 evidence.read 与实时 Project Scope。 */
    List<ReviewCaseView> listForTask(CurrentPrincipal principal, String correlationId, UUID taskId);
}
