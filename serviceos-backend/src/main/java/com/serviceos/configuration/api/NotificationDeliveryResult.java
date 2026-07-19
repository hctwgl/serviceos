package com.serviceos.configuration.api;

/**
 * 通道发送结果。
 *
 * <p>UNKNOWN 表示外部状态无法确认，不得当作成功；FAILED 可明确失败。</p>
 */
public sealed interface NotificationDeliveryResult {
    record Sent(String providerMessageId, boolean replay) implements NotificationDeliveryResult {
        public Sent {
            if (providerMessageId == null || providerMessageId.isBlank()) {
                throw new IllegalArgumentException("providerMessageId must not be blank");
            }
            providerMessageId = providerMessageId.trim();
        }
    }

    record Unknown(String reasonCode) implements NotificationDeliveryResult {
        public Unknown {
            if (reasonCode == null || reasonCode.isBlank()) {
                throw new IllegalArgumentException("reasonCode must not be blank");
            }
            reasonCode = reasonCode.trim();
        }
    }

    record Failed(String reasonCode) implements NotificationDeliveryResult {
        public Failed {
            if (reasonCode == null || reasonCode.isBlank()) {
                throw new IllegalArgumentException("reasonCode must not be blank");
            }
            reasonCode = reasonCode.trim();
        }
    }
}
