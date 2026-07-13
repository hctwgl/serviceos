package com.serviceos.files.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 本地对象存储的数据面。这里故意不使用 Bearer JWT：HMAC token 本身就是范围受限、短期的能力凭证。
 */
@RestController
@RequestMapping("/api/v1/file-transfers")
@ConditionalOnBean(LocalObjectTransferService.class)
final class LocalFileTransferController {
    private final LocalObjectTransferService transfers;

    LocalFileTransferController(LocalObjectTransferService transfers) {
        this.transfers = transfers;
    }

    @PutMapping("/{token}")
    ResponseEntity<Void> upload(@PathVariable String token, HttpServletRequest request) throws IOException {
        transfers.upload(token, request.getContentType(), request.getContentLengthLong(), request.getInputStream());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{token}")
    ResponseEntity<InputStreamResource> download(@PathVariable String token) throws IOException {
        LocalObjectTransferService.DownloadedObject object = transfers.download(token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(object.mimeType()))
                .contentLength(object.size())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(object.content()));
    }
}
