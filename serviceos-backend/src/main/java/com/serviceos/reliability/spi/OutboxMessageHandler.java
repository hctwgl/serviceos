package com.serviceos.reliability.spi;

/**
 * 模块化单体内部的可靠事件消费者端口。
 *
 * <p>实现必须在自己的事务中使用 Inbox 去重；同一消息可能因发布结果未知而被重复调用。</p>
 */
public interface OutboxMessageHandler {
    boolean supports(String eventType, int schemaVersion);

    void handle(OutboxMessage message);
}
