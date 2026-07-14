package com.serviceos.files.web;

import com.serviceos.files.api.AuthorizeDownloadCommand;
import com.serviceos.files.api.BeginUploadCommand;
import com.serviceos.files.api.DownloadAuthorizationView;
import com.serviceos.files.api.FileCommandService;
import com.serviceos.files.api.FinalizeUploadCommand;
import com.serviceos.files.api.InvalidateStoredFileCommand;
import com.serviceos.files.api.StoredFileView;
import com.serviceos.files.api.UploadSessionView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * 文件控制面 API。上传/下载的数据面使用短期能力 URL，tenant 与 actor 仅来自已验证 JWT。
 */
@RestController
@RequestMapping("/api/v1/files")
final class FileController {
    private final FileCommandService files;
    private final CurrentPrincipalProvider principals;

    FileController(FileCommandService files, CurrentPrincipalProvider principals) {
        this.files = files;
        this.principals = principals;
    }

    @PostMapping("/upload-sessions")
    ResponseEntity<UploadSessionView> beginUpload(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody BeginUploadRequest request
    ) {
        CurrentPrincipal principal = principals.current();
        UploadSessionView result = files.beginUpload(
                principal,
                new CommandMetadata(correlationId, idempotencyKey),
                new BeginUploadCommand(
                        request.businessContextType(), request.businessContextId(),
                        request.originalFileName(), request.declaredMimeType(),
                        request.expectedSize(), request.expectedSha256()));
        return ResponseEntity
                .created(URI.create("/api/v1/files/upload-sessions/" + result.uploadSessionId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(result);
    }

    @PostMapping("/upload-sessions/{uploadSessionId}:finalize")
    ResponseEntity<StoredFileView> finalizeUpload(
            @PathVariable UUID uploadSessionId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody FinalizeUploadRequest request
    ) {
        CurrentPrincipal principal = principals.current();
        StoredFileView result = files.finalizeUpload(
                principal,
                // finalizeCommandId 同时作为传输层和离线设备幂等键，避免维护两套相互矛盾的键。
                new CommandMetadata(correlationId, request.finalizeCommandId()),
                uploadSessionId,
                new FinalizeUploadCommand(request.actualSha256(), request.finalizeCommandId()));
        return ResponseEntity
                .created(URI.create("/api/v1/files/" + result.fileId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(result);
    }

    @PostMapping("/{fileId}:invalidate")
    ResponseEntity<StoredFileView> invalidate(
            @PathVariable UUID fileId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody InvalidateFileRequest request
    ) {
        CurrentPrincipal principal = principals.current();
        StoredFileView result = files.invalidate(
                principal,
                new CommandMetadata(correlationId, idempotencyKey),
                new InvalidateStoredFileCommand(
                        fileId, request.reasonCode(), request.sourceType(), request.sourceId()));
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(result);
    }

    @PostMapping("/{fileId}/download-authorizations")
    ResponseEntity<DownloadAuthorizationView> authorizeDownload(
            @PathVariable UUID fileId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody AuthorizeDownloadRequest request
    ) {
        CurrentPrincipal principal = principals.current();
        DownloadAuthorizationView result = files.authorizeDownload(
                principal, correlationId, fileId, new AuthorizeDownloadCommand(request.purpose()));
        return ResponseEntity
                .created(URI.create("/api/v1/files/" + fileId + "/download-authorizations/"
                        + result.authorizationId()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(result);
    }

    record InvalidateFileRequest(String reasonCode, String sourceType, String sourceId) {
    }
}
