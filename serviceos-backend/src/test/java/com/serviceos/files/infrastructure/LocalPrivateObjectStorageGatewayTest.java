package com.serviceos.files.infrastructure;

import com.serviceos.files.spi.ObjectMetadata;
import com.serviceos.files.spi.ObjectTransferAuthorization;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalPrivateObjectStorageGatewayTest {
    private static final Instant NOW = Instant.parse("2026-07-13T06:00:00Z");
    private static final String SIGNING_KEY = "test-signing-key-with-more-than-thirty-two-bytes";

    @TempDir
    Path root;

    @Test
    void signedUploadIsOneShotSizeBoundAndInspectable() throws Exception {
        LocalPrivateObjectStorageGateway storage = storage();
        byte[] content = "ServiceOS secure upload".getBytes(StandardCharsets.UTF_8);
        ObjectTransferAuthorization authorization = storage.authorizeUpload(
                "pending/tenant/2026/07/13/object-1", content.length, "text/plain", Duration.ofMinutes(5));
        String token = token(authorization.url());

        storage.upload(token, "text/plain; charset=UTF-8", content.length, new ByteArrayInputStream(content));
        ObjectMetadata metadata = storage.inspect("pending/tenant/2026/07/13/object-1");

        assertThat(metadata.size()).isEqualTo(content.length);
        assertThat(metadata.detectedMimeType()).isEqualTo("text/plain");
        assertThat(metadata.checksumSha256())
                .isEqualTo(com.serviceos.shared.Sha256.digest(new String(content, StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> storage.upload(
                token, "text/plain", content.length, new ByteArrayInputStream(content)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.FILE_UPLOAD_CONFLICT));
    }

    @Test
    void tamperedTokenWrongMimeAndPathEscapeAreRejected() {
        LocalPrivateObjectStorageGateway storage = storage();
        byte[] content = "abc".getBytes(StandardCharsets.UTF_8);
        ObjectTransferAuthorization authorization = storage.authorizeUpload(
                "pending/tenant/object-2", content.length, "text/plain", Duration.ofMinutes(5));
        String token = token(authorization.url());

        assertThatThrownBy(() -> storage.upload(
                token + "x", "text/plain", content.length, new ByteArrayInputStream(content)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        assertThatThrownBy(() -> storage.upload(
                token, "application/pdf", content.length, new ByteArrayInputStream(content)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.FILE_OBJECT_MISMATCH));
        assertThatThrownBy(() -> storage.authorizeUpload(
                "../escape", 3, "text/plain", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signedDownloadReturnsPrivateObjectAndExpiredTokenFails() throws Exception {
        LocalPrivateObjectStorageGateway storage = storage();
        byte[] content = "download me".getBytes(StandardCharsets.UTF_8);
        ObjectTransferAuthorization upload = storage.authorizeUpload(
                "pending/tenant/object-3", content.length, "text/plain", Duration.ofMinutes(5));
        storage.upload(token(upload.url()), "text/plain", content.length, new ByteArrayInputStream(content));
        ObjectTransferAuthorization download = storage.authorizeDownload(
                "pending/tenant/object-3", "text/plain", Duration.ofMinutes(5));

        try (var object = storage.download(token(download.url())).content()) {
            assertThat(object.readAllBytes()).isEqualTo(content);
        }

        LocalPrivateObjectStorageGateway later = new LocalPrivateObjectStorageGateway(
                root.toString(), SIGNING_KEY, "http://localhost/api/v1/file-transfers",
                Clock.fixed(NOW.plus(Duration.ofMinutes(6)), ZoneOffset.UTC));
        assertThatThrownBy(() -> later.download(token(download.url())))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.FILE_UPLOAD_EXPIRED));
    }

    @Test
    void internalStoreIsContentImmutableAndIdempotent() throws Exception {
        LocalPrivateObjectStorageGateway storage = storage();
        byte[] content = "{\"message\":\"m56\"}".getBytes(StandardCharsets.UTF_8);
        String digest = com.serviceos.shared.Sha256.digest(content);

        ObjectMetadata first = storage.storeInternal(
                "integration/inbound/raw.json", new ByteArrayInputStream(content),
                content.length, digest, "application/json");
        ObjectMetadata replay = storage.storeInternal(
                "integration/inbound/raw.json", new ByteArrayInputStream(content),
                content.length, digest, "application/json");

        assertThat(replay).isEqualTo(first);
        assertThatThrownBy(() -> storage.storeInternal(
                "integration/inbound/raw.json",
                new ByteArrayInputStream("different".getBytes(StandardCharsets.UTF_8)),
                9, com.serviceos.shared.Sha256.digest("different"), "application/json"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("different content");
    }

    private LocalPrivateObjectStorageGateway storage() {
        return new LocalPrivateObjectStorageGateway(
                root.toString(), SIGNING_KEY, "http://localhost/api/v1/file-transfers",
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String token(String url) {
        String path = URI.create(url).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
