package com.serviceos.integration.byd.infrastructure;

/** 数据库级反重放判定。 */
public record BydCpimReplayDecision(Kind kind, String resultDigest) {
    public enum Kind { NEW, REPLAY }

    public static BydCpimReplayDecision newRequest() {
        return new BydCpimReplayDecision(Kind.NEW, null);
    }

    public static BydCpimReplayDecision replay(String resultDigest) {
        return new BydCpimReplayDecision(Kind.REPLAY, resultDigest);
    }
}
