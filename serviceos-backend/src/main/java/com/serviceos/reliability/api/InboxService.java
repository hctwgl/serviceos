package com.serviceos.reliability.api;

import java.util.UUID;

/**
 * Inbox 公开端口。begin、消费者领域写入、complete 必须位于同一数据库事务。
 */
public interface InboxService {
    InboxDecision begin(String tenantId, String consumerName, UUID eventId, int schemaVersion, String payloadDigest);

    void complete(String tenantId, String consumerName, UUID eventId, String resultDigest);
}
