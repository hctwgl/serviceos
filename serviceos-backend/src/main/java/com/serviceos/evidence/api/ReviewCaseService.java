package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.UUID;

/** ReviewCase / ReviewDecision 命令与查询端口。 */
public interface ReviewCaseService {
    ReviewCaseView create(CurrentPrincipal principal, CommandMetadata metadata, CreateReviewCaseCommand command);

    ReviewCaseView decide(CurrentPrincipal principal, CommandMetadata metadata, DecideReviewCaseCommand command);

    ReviewCaseView get(CurrentPrincipal principal, String correlationId, UUID reviewCaseId);
}
