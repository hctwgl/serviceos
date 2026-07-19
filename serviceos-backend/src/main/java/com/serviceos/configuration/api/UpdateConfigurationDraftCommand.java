package com.serviceos.configuration.api;

import java.util.List;
import java.util.UUID;

public record UpdateConfigurationDraftCommand(
        UUID draftId,
        long expectedVersion,
        String definitionJson,
        List<String> supportedClientKinds
) {
    public UpdateConfigurationDraftCommand {
        // null = 调用方未携带该字段时保留原值；空列表非法，由应用层拒绝。
        supportedClientKinds = supportedClientKinds == null
                ? null : List.copyOf(supportedClientKinds);
    }

    /** 仅更新定义，保留原 supportedClientKinds。 */
    public UpdateConfigurationDraftCommand(
            UUID draftId,
            long expectedVersion,
            String definitionJson
    ) {
        this(draftId, expectedVersion, definitionJson, null);
    }
}
