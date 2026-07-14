package com.serviceos.fieldwork.api;

/** 客户端采集的 WGS84 定位证据。GPS 仅参与策略判断，不单独决定业务真伪。 */
public record VisitLocation(double latitude, double longitude, double accuracyMeters) {
    public VisitLocation {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude is invalid");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude is invalid");
        }
        if (!Double.isFinite(accuracyMeters) || accuracyMeters <= 0 || accuracyMeters > 10000) {
            throw new IllegalArgumentException("accuracyMeters is invalid");
        }
    }
}
