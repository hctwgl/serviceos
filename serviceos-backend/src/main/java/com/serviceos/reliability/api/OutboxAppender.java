package com.serviceos.reliability.api;

/**
 * 追加 Outbox 事件的公开端口；实现只能写本地数据库，不得在事务内发布网络消息。
 */
public interface OutboxAppender {
    void append(OutboxEvent event);
}
