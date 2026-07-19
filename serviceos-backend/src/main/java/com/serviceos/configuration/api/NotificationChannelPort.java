package com.serviceos.configuration.api;

/**
 * 通知通道 SPI。真实短信/邮件供应商实现此端口；缺凭据时使用本地参考 Adapter。
 */
public interface NotificationChannelPort {
    /** 支持的通道枚举值，如 IN_APP / SMS / EMAIL / PUSH。 */
    boolean supports(String channel);

    NotificationDeliveryResult send(NotificationSendRequest request);
}
