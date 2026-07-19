package com.serviceos.integration.geely.api;

/**
 * 吉利通知接口明文反参：code="0" 表示接收成功。
 */
public record GeelyNotifyResponse(String code, String message) {
    public static GeelyNotifyResponse ok() {
        return new GeelyNotifyResponse("0", "");
    }

    public static GeelyNotifyResponse fail(String message) {
        return new GeelyNotifyResponse("1", message == null ? "rejected" : message);
    }
}
