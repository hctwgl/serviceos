package com.serviceos.task.application;

public record TaskResolution(Status status) {
    public enum Status { SUCCEEDED, RETRY_SCHEDULED, MANUAL_INTERVENTION }
}
