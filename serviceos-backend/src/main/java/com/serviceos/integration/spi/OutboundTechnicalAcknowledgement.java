package com.serviceos.integration.spi;

import java.util.Objects;

/**
 * 技术 ACK 解释结果。
 *
 * <p>技术接受不等于业务审核通过；业务 ACK 仍由回调/回执路径处理。
 * {@link Outcome#UNKNOWN} 表示无法确认远端状态，必须失败关闭为 UNKNOWN。</p>
 */
public record OutboundTechnicalAcknowledgement(
        Outcome outcome,
        String reasonCode,
        String acknowledgementReasonCode
) {
    public enum Outcome {
        ACCEPTED,
        REJECTED,
        UNKNOWN
    }

    public OutboundTechnicalAcknowledgement {
        Objects.requireNonNull(outcome, "outcome must not be null");
        reasonCode = required(reasonCode, "reasonCode");
        if (outcome == Outcome.UNKNOWN) {
            acknowledgementReasonCode = null;
        } else {
            acknowledgementReasonCode = required(acknowledgementReasonCode, "acknowledgementReasonCode");
        }
    }

    public static OutboundTechnicalAcknowledgement accepted(String reasonCode, String ackReason) {
        return new OutboundTechnicalAcknowledgement(Outcome.ACCEPTED, reasonCode, ackReason);
    }

    public static OutboundTechnicalAcknowledgement rejected(String reasonCode, String ackReason) {
        return new OutboundTechnicalAcknowledgement(Outcome.REJECTED, reasonCode, ackReason);
    }

    public static OutboundTechnicalAcknowledgement unknown(String reasonCode) {
        return new OutboundTechnicalAcknowledgement(Outcome.UNKNOWN, reasonCode, null);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
