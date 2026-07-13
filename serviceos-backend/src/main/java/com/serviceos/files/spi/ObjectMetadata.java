package com.serviceos.files.spi;

public record ObjectMetadata(long size, String checksumSha256, String detectedMimeType) {
}
