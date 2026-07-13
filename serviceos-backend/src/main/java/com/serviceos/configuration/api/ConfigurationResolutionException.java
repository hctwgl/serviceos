package com.serviceos.configuration.api;

public final class ConfigurationResolutionException extends RuntimeException {
    private final Reason reason;

    public ConfigurationResolutionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        PROJECT_NOT_ACTIVE,
        NO_MATCH,
        AMBIGUOUS_MATCH
    }
}
