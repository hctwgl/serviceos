package com.serviceos.configuration.api;

import java.util.UUID;

public record DeactivateBundleChannelCommand(
        UUID activationId,
        long expectedVersion,
        String approvalRef
) {
}
