package com.serviceos.task.application;

import com.serviceos.task.spi.AutomatedTaskHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 执行器注册表在启动时拒绝重复 taskType，避免运行时随机选择处理器。
 */
public final class TaskHandlerRegistry {
    private final Map<String, AutomatedTaskHandler> handlers;

    public TaskHandlerRegistry(List<AutomatedTaskHandler> handlers) {
        Map<String, AutomatedTaskHandler> indexed = new LinkedHashMap<>();
        for (AutomatedTaskHandler handler : handlers) {
            String type = handler.taskType();
            if (type == null || type.isBlank()) {
                throw new IllegalStateException("AutomatedTaskHandler taskType must not be blank");
            }
            if (indexed.putIfAbsent(type, handler) != null) {
                throw new IllegalStateException("Duplicate AutomatedTaskHandler for taskType " + type);
            }
        }
        this.handlers = Map.copyOf(indexed);
    }

    Optional<AutomatedTaskHandler> find(String taskType) {
        return Optional.ofNullable(handlers.get(taskType));
    }
}
