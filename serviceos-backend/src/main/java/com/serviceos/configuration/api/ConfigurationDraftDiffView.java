package com.serviceos.configuration.api;

import java.util.UUID;

/** 草稿相对基线版本的统一文本 Diff。 */
public record ConfigurationDraftDiffView(
        UUID draftId,
        UUID baseVersionId,
        String baseLabel,
        String draftLabel,
        String unifiedDiff,
        boolean identical
) {
}
