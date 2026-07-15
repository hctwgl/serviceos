package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

/** REVIEW_REQUIRED 条件变化的高风险处置端口。 */
public interface EvidenceConditionDispositionService {
    EvidenceConditionDispositionView resolve(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ResolveEvidenceConditionChangeCommand command
    );
}
