package com.serviceos.files.api;

import java.util.UUID;

/** 将 AVAILABLE StoredFile 作废为 INVALIDATED。 */
public record InvalidateStoredFileCommand(
        UUID fileId,
        String reasonCode,
        String sourceType,
        String sourceId
) {
}
