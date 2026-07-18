package com.serviceos.configuration.api;

import java.util.UUID;

public record UpdateConfigurationDraftCommand(
        UUID draftId,
        long expectedVersion,
        String definitionJson
) {
}
