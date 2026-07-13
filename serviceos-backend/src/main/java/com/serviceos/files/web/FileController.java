package com.serviceos.files.web;

import com.serviceos.files.api.AuthorizeDownloadCommand;
import com.serviceos.files.api.BeginUploadCommand;
import com.serviceos.files.api.DownloadAuthorizationView;
import com.serviceos.files.api.FileCommandService;
import com.serviceos.files.api.FinalizeUploadCommand;
import com.serviceos.files.api.StoredFileView;
import com.serviceos.files.api.UploadSessionView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody BeginUploadRequest request
    ) {
        String correlation = correlation(correlationId);
        CurrentPrincipal principal = principals.current();
        UploadSessionView result = files.beginUpload(
                principal,
                new CommandMetadata(correlation, idempotencyKey),
                new BeginUploadCommand(
                        request.businessContextType(), request.businessContextId(),
                        request.originalFileName(), request.declaredMimeType(),
                        request.expectedSize(), request.expectedSha256()));
        return ResponseEntity
                .created(URI.create("/api/v1/files/upload-sessions/" + result.uploadSessionId()))
                .header("X-Correlation-Id", correlation)
                .body(result);
    }

    @PostMapping("/upload-sessions/{uploadSessionId}:finalize")
    ResponseEntity<StoredFileView> finalizeUpload(
            @PathVariable UUID uploadSessionId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody FinalizeUploadRequest request
    ) {
        String correlation = correlation(correlationId);
        CurrentPrincipal principal = principals.current();
        StoredFileView result = files.finalizeUpload(
                principal,
                // finalizeCommandId 同时作为传输层和离线设备幂等键，避免维护两套相互矛盾的键。
                new CommandMetadata(correlation, request.finalizeCommandId()),
                uploadSessionId,
                new FinalizeUploadCommand(request.actualSha256(), request.finalizeCommandId()));
        return ResponseEntity
                .created(URI.create("/api/v1/files/" + result.fileId()))
                .header("X-Correlation-Id", correlation)
                .body(result);
    }

    @PostMapping("/{fileId}/download-authorizations")
    ResponseEntity<DownloadAuthorizationView> authorizeDownload(
            @PathVariable UUID fileId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody AuthorizeDownloadRequest request
    ) {
        String correlation = correlation(correlationId);
        CurrentPrincipal principal = principals.current();
        DownloadAuthorizationView result = files.authorizeDownload(
                principal, correlation, fileId, new AuthorizeDownloadCommand(request.purpose()));
        return ResponseEntity
                .created(URI.create("/api/v1/files/" + fileId + "/download-authorizations/"
                        + result.authorizationId()))
                .header("X-Correlation-Id", correlation)
                .body(result);
    }

    private static String correlation(String supplied) {
        return supplied == null || supplied.isBlank() ? UUID.randomUUID().toString() : supplied;
    }
}
