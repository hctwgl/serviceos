package com.serviceos.reliability.infrastructure;

import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.reliability.spi.OutboxPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 模块化单体默认使用的进程内可靠发布适配器。
 *
 * <p>它只负责把已经由 Outbox Worker 认领的消息交给模块消费者。会推进业务的内部触发事件
 * 必须存在消费者，否则失败关闭；纯通知事件允许零订阅者，避免本地模式把正常 Outbox 堵死。
 * 切换到 Broker 时禁用本适配器，并由 Broker consumer 调用相同的业务消费者端口。</p>
 */
@Component
@ConditionalOnProperty(
        name = "serviceos.outbox.publisher",
        havingValue = "local",
        matchIfMissing = true)
final class LocalOutboxPublisher implements OutboxPublisher {
    private static final Set<String> REQUIRED_LOCAL_DELIVERY = Set.of(
            "workorder.received@v1", "task.completed@v1",
            "service.assignment.pending-activation@v2",
            "task.assignment-prepared@v1",
            "service.assignment.task-prepared@v2",
            "service.assignment.activated@v2",
            "task.assignment-activated@v1",
            "service.assignment.activation-aborted@v2",
            "task.assignment-aborted@v1",
            "service.assignment.activation-timed-out@v1",
            "service.assignment.activation-completed@v1");
    private final List<OutboxMessageHandler> handlers;

    LocalOutboxPublisher(List<OutboxMessageHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    @Override
    public void publish(OutboxMessage message) {
        List<OutboxMessageHandler> matched = handlers.stream()
                .filter(handler -> handler.supports(message.eventType(), message.schemaVersion()))
                .toList();
        if (matched.isEmpty() && REQUIRED_LOCAL_DELIVERY.contains(
                message.eventType() + "@v" + message.schemaVersion())) {
            throw new IllegalStateException(
                    "No local outbox handler for " + message.eventType() + "@v" + message.schemaVersion());
        }
        for (OutboxMessageHandler handler : matched) {
            handler.handle(message);
        }
    }
}
