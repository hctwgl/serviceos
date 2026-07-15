package com.serviceos.integration.byd.infrastructure;

import java.util.UUID;

/** 数据库级反重放判定。 */
public record BydCpimReplayDecision(Kind kind, UUID inboundEnvelopeId, String resultDigest) {
    public enum Kind { NEW, REPLAY }

    public static BydCpimReplayDecision newRequest(UUID inboundEnvelopeId) {
        return new BydCpimReplayDecision(Kind.NEW, inboundEnvelopeId, null);
    }

    public static BydCpimReplayDecision replay(UUID inboundEnvelopeId, String resultDigest) {
        return new BydCpimReplayDecision(Kind.REPLAY, inboundEnvelopeId, resultDigest);
    }
}
