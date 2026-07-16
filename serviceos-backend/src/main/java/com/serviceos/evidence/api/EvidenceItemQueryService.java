package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.List;
import java.util.UUID;

/** EvidenceItem 安全摘要查询边界。 */
public interface EvidenceItemQueryService {
    List<EvidenceItemSummaryView> listSummariesForTask(
            CurrentPrincipal principal, String correlationId, UUID taskId);
}
