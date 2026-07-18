package com.serviceos.configuration.api;

import java.util.UUID;

public record ApproveConfigurationDraftCommand(
        UUID draftId,
        long expectedVersion,
        String approvalRef
) {
}
