package com.serviceos.reliability.api;

/**
 * 消费者幂等判定：REPLAY 表示该 eventId 已由同一消费者成功处理，不得再次写业务结果。
 */
public record InboxDecision(Kind kind) {
    public enum Kind { NEW, REPLAY }

    public static InboxDecision newEvent() {
        return new InboxDecision(Kind.NEW);
    }

    public static InboxDecision replay() {
        return new InboxDecision(Kind.REPLAY);
    }
}
