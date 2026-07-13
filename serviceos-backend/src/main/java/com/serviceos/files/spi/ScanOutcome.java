package com.serviceos.files.spi;

public record ScanOutcome(Result result, String scannerName, String scannerVersion, String reasonCode) {
    public enum Result { CLEAN, MALICIOUS }

    public static ScanOutcome clean(String scannerName, String scannerVersion) {
        return new ScanOutcome(Result.CLEAN, scannerName, scannerVersion, null);
    }

    public static ScanOutcome malicious(String scannerName, String scannerVersion, String reasonCode) {
        return new ScanOutcome(Result.MALICIOUS, scannerName, scannerVersion, reasonCode);
    }
}
