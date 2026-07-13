package com.serviceos.reliability.spi;

/**
 * Broker/HTTP 等发布适配器 SPI。实现必须把 eventId 作为下游去重键。
 */
@FunctionalInterface
public interface OutboxPublisher {
    void publish(OutboxMessage message) throws Exception;
}
