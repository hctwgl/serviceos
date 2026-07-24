package com.serviceos.configuration.api;

import java.util.List;
import java.util.Map;

/**
 * 履约版本中的业务节点草稿。
 *
 * <p>节点资产以当前版本内的独立草稿快照保存。公共模板只负责复制初值，发布版本
 * 不持有可变模板引用。</p>
 */
public record ProjectFulfillmentNodeDraft(
        String nodeId,
        String nodeType,
        String nodeName,
        String phaseId,
        String description,
        double positionX,
        double positionY,
        String responsibilityRole,
        String executionSubjectRule,
        boolean reassignable,
        Map<String, Object> task,
        Map<String, Object> form,
        List<Map<String, Object>> evidence,
        Map<String, Object> sla,
        List<String> completionResults,
        Map<String, Object> systemAction,
        Map<String, Object> eventWait,
        Map<String, Object> condition,
        String exceptionStrategy,
        List<String> notificationRules
) {
    public ProjectFulfillmentNodeDraft {
        task = immutableMap(task);
        form = immutableMap(form);
        evidence = evidence == null ? List.of() : evidence.stream()
                .map(ProjectFulfillmentNodeDraft::immutableMap)
                .toList();
        sla = immutableMap(sla);
        completionResults = List.copyOf(completionResults == null ? List.of() : completionResults);
        systemAction = immutableMap(systemAction);
        eventWait = immutableMap(eventWait);
        condition = immutableMap(condition);
        notificationRules = List.copyOf(notificationRules == null ? List.of() : notificationRules);
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value) {
        return value == null ? Map.of() : Map.copyOf(value);
    }
}
