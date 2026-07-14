package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.UUID;

/** ExternalReviewReceipt 命令与查询端口。 */
public interface ExternalReviewReceiptService {
    ExternalReviewReceiptView record(
            CurrentPrincipal principal, CommandMetadata metadata, RecordExternalReviewReceiptCommand command);

    ExternalReviewReceiptView get(CurrentPrincipal principal, String correlationId, UUID receiptId);
}
