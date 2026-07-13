package com.serviceos.files.infrastructure;

import com.serviceos.files.spi.ObjectMetadata;
import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.files.spi.ObjectTransferAuthorization;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 可运行的本地私有对象存储沙箱。
 *
 * <p>它使用 HMAC 短期能力 URL、服务端生成的对象 key、精确大小和 MIME 约束；目录从不作为静态资源公开。
 * 生产环境应将此 Bean 替换为 S3/云对象存储预签名适配器。</p>
 */
@Component
@ConditionalOnProperty(
        name = "serviceos.files.storage",
        havingValue = "local-private",
        matchIfMissing = true
)
final class LocalPrivateObjectStorageGateway implements ObjectStorageGateway, LocalObjectTransferService {
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final Path root;
    private final byte[] signingKey;
    private final String transferBaseUrl;
    private final Clock clock;

    LocalPrivateObjectStorageGateway(
            @Value("${serviceos.files.local.root:${java.io.tmpdir}/serviceos-private-files}") String root,
            @Value("${serviceos.files.local.signing-key:local-development-key-change-before-production-32bytes}")
            String signingKey,
            @Value("${serviceos.files.local.transfer-base-url:http://localhost:8080/api/v1/file-transfers}")
            String transferBaseUrl,
            Clock clock
    ) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
        if (this.signingKey.length < 32) {
            throw new IllegalArgumentException("Local file signing key must contain at least 32 bytes");
        }
        this.transferBaseUrl = stripTrailingSlash(transferBaseUrl);
        this.clock = clock;
    }

    @Override
    public ObjectTransferAuthorization authorizeUpload(
            String objectKey,
            long exactSize,
            String declaredMimeType,
            Duration lifetime
    ) {
        Instant expiresAt = clock.instant().plus(lifetime);
        String token = issueToken("UPLOAD", objectKey, exactSize, declaredMimeType, expiresAt);
        return new ObjectTransferAuthorization(
                "PUT", transferBaseUrl + "/" + url(token),
                Map.of("Content-Type", declaredMimeType, "Content-Length", Long.toString(exactSize)),
                expiresAt);
    }

    @Override
    public ObjectMetadata inspect(String objectKey) throws IOException {
        Path path = safePath(objectKey);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Object does not exist");
        }
        long size = Files.size(path);
        String checksum = sha256(path);
        String detectedMime;
        try (InputStream content = new BufferedInputStream(Files.newInputStream(path))) {
            detectedMime = FileMagic.detect(content);
        }
        return new ObjectMetadata(size, checksum, detectedMime);
    }

    @Override
    public InputStream openForScan(String objectKey) throws IOException {
        Path path = safePath(objectKey);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Object does not exist");
        }
        return new BufferedInputStream(Files.newInputStream(path));
    }

    @Override
    public ObjectTransferAuthorization authorizeDownload(
            String objectKey,
            String responseMimeType,
            Duration lifetime
    ) {
        Instant expiresAt = clock.instant().plus(lifetime);
        String token = issueToken("DOWNLOAD", objectKey, 0, responseMimeType, expiresAt);
        return new ObjectTransferAuthorization(
                "GET", transferBaseUrl + "/" + url(token), Map.of(), expiresAt);
    }

    @Override
    public void upload(
            String token,
            String contentType,
            long contentLength,
            InputStream content
    ) throws IOException {
        TransferClaim claim = verifyToken(token, "UPLOAD");
        String normalizedContentType = normalizeContentType(contentType);
        if (!claim.mimeType().equals(normalizedContentType)) {
            throw new BusinessProblem(ProblemCode.FILE_OBJECT_MISMATCH,
                    "Upload Content-Type does not match the signed authorization");
        }
        if (contentLength >= 0 && contentLength != claim.exactSize()) {
            throw new BusinessProblem(ProblemCode.FILE_OBJECT_MISMATCH,
                    "Upload Content-Length does not match the signed authorization");
        }

        Path destination = safePath(claim.objectKey());
        Files.createDirectories(destination.getParent());
        Path temporary = destination.getParent().resolve("." + destination.getFileName() + "." + UUID.randomUUID());
        Path consumedMarker = destination.getParent().resolve("." + destination.getFileName() + ".consumed");
        boolean completed = false;
        boolean markerCreated = false;
        try {
            // CREATE_NEW 是原子的一次性消费闩锁；即使某些文件系统的 ATOMIC_MOVE 会替换目标，也不能重复使用 token。
            Files.createFile(consumedMarker);
            markerCreated = true;
            long written = copyExactly(content, temporary, claim.exactSize());
            if (written != claim.exactSize()) {
                throw new BusinessProblem(ProblemCode.FILE_OBJECT_MISMATCH,
                        "Uploaded byte count does not match the signed authorization");
            }
            moveWithoutReplace(temporary, destination);
            completed = true;
        } catch (FileAlreadyExistsException exception) {
            throw new BusinessProblem(ProblemCode.FILE_UPLOAD_CONFLICT,
                    "The signed upload authorization has already been consumed");
        } finally {
            Files.deleteIfExists(temporary);
            if (markerCreated && !completed) {
                Files.deleteIfExists(consumedMarker);
            }
        }
    }

    @Override
    public DownloadedObject download(String token) throws IOException {
        TransferClaim claim = verifyToken(token, "DOWNLOAD");
        Path path = safePath(claim.objectKey());
        if (!Files.isRegularFile(path)) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "File object was not found");
        }
        return new DownloadedObject(
                new BufferedInputStream(Files.newInputStream(path)), Files.size(path), claim.mimeType());
    }

    private String issueToken(
            String operation,
            String objectKey,
            long exactSize,
            String mimeType,
            Instant expiresAt
    ) {
        safePath(objectKey);
        String payload = String.join("|",
                "v1", operation, Long.toString(expiresAt.getEpochSecond()), Long.toString(exactSize),
                BASE64.encodeToString(mimeType.getBytes(StandardCharsets.UTF_8)),
                BASE64.encodeToString(objectKey.getBytes(StandardCharsets.UTF_8)),
                UUID.randomUUID().toString());
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        return BASE64.encodeToString(payloadBytes) + "." + BASE64.encodeToString(hmac(payloadBytes));
    }

    private TransferClaim verifyToken(String token, String expectedOperation) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) {
                throw invalidToken();
            }
            byte[] payloadBytes = BASE64_DECODER.decode(parts[0]);
            byte[] suppliedSignature = BASE64_DECODER.decode(parts[1]);
            if (!MessageDigest.isEqual(hmac(payloadBytes), suppliedSignature)) {
                throw invalidToken();
            }
            String[] fields = new String(payloadBytes, StandardCharsets.UTF_8).split("\\|", -1);
            if (fields.length != 7 || !"v1".equals(fields[0]) || !expectedOperation.equals(fields[1])) {
                throw invalidToken();
            }
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(fields[2]));
            if (!clock.instant().isBefore(expiresAt)) {
                throw new BusinessProblem(ProblemCode.FILE_UPLOAD_EXPIRED,
                        "File transfer authorization has expired");
            }
            long exactSize = Long.parseLong(fields[3]);
            String mimeType = new String(BASE64_DECODER.decode(fields[4]), StandardCharsets.UTF_8);
            String objectKey = new String(BASE64_DECODER.decode(fields[5]), StandardCharsets.UTF_8);
            safePath(objectKey);
            return new TransferClaim(expectedOperation, objectKey, exactSize, mimeType, expiresAt);
        } catch (BusinessProblem problem) {
            throw problem;
        } catch (RuntimeException exception) {
            throw invalidToken();
        }
    }

    private Path safePath(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/") || objectKey.contains("\\")) {
            throw new IllegalArgumentException("objectKey is invalid");
        }
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("objectKey escapes the private storage root");
        }
        return resolved;
    }

    private byte[] hmac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return mac.doFinal(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot sign local object transfer authorization", exception);
        }
    }

    private static long copyExactly(InputStream content, Path target, long exactSize) throws IOException {
        long maximum = exactSize + 1;
        long total = 0;
        byte[] buffer = new byte[8192];
        try (OutputStream output = Files.newOutputStream(target)) {
            while (total < maximum) {
                int requested = (int) Math.min(buffer.length, maximum - total);
                int read = content.read(buffer, 0, requested);
                if (read < 0) {
                    break;
                }
                output.write(buffer, 0, read);
                total += read;
            }
        }
        return total;
    }

    private static void moveWithoutReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
        try (InputStream content = new BufferedInputStream(Files.newInputStream(path))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = content.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private static String stripTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("transferBaseUrl must not be blank");
        }
        return normalized;
    }

    private static String url(String token) {
        return URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private static BusinessProblem invalidToken() {
        return new BusinessProblem(ProblemCode.ACCESS_DENIED,
                "File transfer authorization is invalid");
    }

    private record TransferClaim(
            String operation,
            String objectKey,
            long exactSize,
            String mimeType,
            Instant expiresAt
    ) {
    }
}
