package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.List;
import java.util.UUID;

public interface EvidenceSlotQueryService {
    List<EvidenceSlotView> listForTask(
            CurrentPrincipal principal, String correlationId, UUID taskId);
}
