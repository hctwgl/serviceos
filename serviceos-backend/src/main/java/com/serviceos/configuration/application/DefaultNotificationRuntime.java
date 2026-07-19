package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.ExpressionDefinition;
import com.serviceos.configuration.api.ExpressionEvaluator;
import com.serviceos.configuration.api.NotificationChannelPort;
import com.serviceos.configuration.api.NotificationDeliveryResult;
import com.serviceos.configuration.api.NotificationResolution;
import com.serviceos.configuration.api.NotificationResolveCommand;
import com.serviceos.configuration.api.NotificationRuntime;
import com.serviceos.configuration.api.NotificationSendRequest;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 冻结 Bundle NOTIFICATION 执行器。
 *
 * <p>按 eventType 匹配触发器 → when 求值 → 收件人角色解析 → 通道 SPI 发送。
 * 幂等键 = tenant|policy|trigger|eventId|recipient|channel。UNKNOWN/FAILED 标记人工接管。</p>
 */
@Service
public class DefaultNotificationRuntime implements NotificationRuntime {
    private final ConfigurationService configurations;
    private final ExpressionEvaluator expressions;
    private final List<NotificationChannelPort> channels;
    private final ObjectMapper objectMapper;

    public DefaultNotificationRuntime(
            ConfigurationService configurations,
            ExpressionEvaluator expressions,
            List<NotificationChannelPort> channels,
            ObjectMapper objectMapper
    ) {
        this.configurations = configurations;
        this.expressions = expressions;
        this.channels = List.copyOf(Objects.requireNonNull(channels, "channels"));
        this.objectMapper = objectMapper;
    }

    @Override
    public NotificationResolution resolveAndDispatch(NotificationResolveCommand command) {
        Objects.requireNonNull(command, "command");
        List<ConfigurationAssetDefinition> assets = configurations.listBundleAssets(
                command.tenantId(), command.bundleId(), command.expectedManifestDigest(),
                ConfigurationAssetType.NOTIFICATION);
        List<ConfigurationAssetDefinition> matches = assets.stream()
                .filter(asset -> command.policyKey().equals(readPolicyKey(asset)))
                .toList();
        if (matches.isEmpty()) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                    "NOTIFICATION policyKey not found in frozen bundle: " + command.policyKey());
        }
        if (matches.size() > 1) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Multiple NOTIFICATION assets share policyKey in frozen bundle: "
                            + command.policyKey());
        }
        ConfigurationAssetDefinition asset = matches.getFirst();
        PolicyDefinition policy = parse(asset.definitionJson());

        List<String> explanations = new ArrayList<>();
        List<NotificationResolution.DeliveryAttempt> attempts = new ArrayList<>();
        boolean requiresManual = false;

        List<TriggerDefinition> matchedTriggers = policy.triggers().stream()
                .filter(trigger -> command.eventType().equals(trigger.eventType()))
                .toList();
        if (matchedTriggers.isEmpty()) {
            explanations.add("no trigger matched eventType=" + command.eventType());
        }

        for (TriggerDefinition trigger : matchedTriggers) {
            boolean when = expressions.evaluate(
                    new ExpressionDefinition(trigger.whenLanguage(), trigger.whenSource()),
                    command.expressionContext()).result();
            explanations.add(trigger.triggerKey() + ": when=" + when);
            if (!when) {
                continue;
            }
            String channel = trigger.channel() == null || trigger.channel().isBlank()
                    ? policy.defaultChannel() : trigger.channel();
            List<String> recipients = resolveRecipients(trigger, command.recipientsByRole(), explanations);
            if (recipients.isEmpty()) {
                explanations.add(trigger.triggerKey() + ": no recipients; manual intervention");
                requiresManual = true;
                continue;
            }
            NotificationChannelPort port = selectChannel(channel);
            for (String recipient : recipients) {
                String idempotencyKey = Sha256.digest(command.tenantId() + "|"
                        + policy.policyKey() + "|" + trigger.triggerKey() + "|"
                        + command.eventId() + "|" + recipient + "|" + channel);
                NotificationDeliveryResult result = port.send(new NotificationSendRequest(
                        channel, recipient, trigger.templateKey(), idempotencyKey,
                        command.templateVariables()));
                String outcome;
                String detail;
                switch (result) {
                    case NotificationDeliveryResult.Sent sent -> {
                        outcome = sent.replay() ? "SENT_REPLAY" : "SENT";
                        detail = sent.providerMessageId();
                    }
                    case NotificationDeliveryResult.Unknown unknown -> {
                        outcome = "UNKNOWN";
                        detail = unknown.reasonCode();
                        requiresManual = true;
                    }
                    case NotificationDeliveryResult.Failed failed -> {
                        outcome = "FAILED";
                        detail = failed.reasonCode();
                        requiresManual = true;
                    }
                }
                attempts.add(new NotificationResolution.DeliveryAttempt(
                        trigger.triggerKey(), trigger.eventType(), channel, recipient,
                        trigger.templateKey(), idempotencyKey, outcome, detail));
                explanations.add(trigger.triggerKey() + ": recipient=" + recipient
                        + " channel=" + channel + " outcome=" + outcome);
            }
        }

        return new NotificationResolution(
                policy.policyKey(),
                asset.versionId(),
                asset.contentDigest(),
                attempts,
                requiresManual,
                explanations);
    }

    private List<String> resolveRecipients(
            TriggerDefinition trigger,
            Map<String, List<String>> recipientsByRole,
            List<String> explanations
    ) {
        if (trigger.recipientRole() == null || trigger.recipientRole().isBlank()) {
            explanations.add(trigger.triggerKey() + ": recipientRole blank");
            return List.of();
        }
        List<String> recipients = recipientsByRole.getOrDefault(trigger.recipientRole(), List.of());
        explanations.add(trigger.triggerKey() + ": role=" + trigger.recipientRole()
                + " recipients=" + recipients.size());
        return recipients;
    }

    private NotificationChannelPort selectChannel(String channel) {
        List<NotificationChannelPort> matches = channels.stream()
                .filter(port -> port.supports(channel))
                .toList();
        if (matches.isEmpty()) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                    "No NotificationChannelPort supports channel: " + channel);
        }
        if (matches.size() > 1) {
            // 多实现时优先非 LocalReference（类名不含 LocalReference）
            return matches.stream()
                    .filter(port -> !port.getClass().getSimpleName().contains("LocalReference"))
                    .findFirst()
                    .orElse(matches.getFirst());
        }
        return matches.getFirst();
    }

    private String readPolicyKey(ConfigurationAssetDefinition asset) {
        return parse(asset.definitionJson()).policyKey();
    }

    private PolicyDefinition parse(String definitionJson) {
        try {
            Map<String, Object> root = objectMapper.readValue(definitionJson, new TypeReference<>() { });
            String policyKey = text(root.get("policyKey"), "policyKey");
            String defaultChannel = text(root.get("defaultChannel"), "defaultChannel");
            Object triggersRaw = root.get("triggers");
            if (!(triggersRaw instanceof List<?> triggerList) || triggerList.isEmpty()) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "NOTIFICATION triggers must be a non-empty array");
            }
            List<TriggerDefinition> triggers = new ArrayList<>();
            java.util.Set<String> keys = new java.util.LinkedHashSet<>();
            for (Object item : triggerList) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "NOTIFICATION trigger must be an object");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> trigger = (Map<String, Object>) map;
                String triggerKey = text(trigger.get("triggerKey"), "triggerKey");
                if (!keys.add(triggerKey)) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "NOTIFICATION triggerKey must be unique: " + triggerKey);
                }
                Map<?, ?> whenMap = (Map<?, ?>) trigger.get("when");
                if (whenMap == null) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "NOTIFICATION when must be an expression object");
                }
                triggers.add(new TriggerDefinition(
                        triggerKey,
                        text(trigger.get("eventType"), "eventType"),
                        text(trigger.get("templateKey"), "templateKey"),
                        text(trigger.get("channel"), "channel"),
                        text(whenMap.get("language"), "when.language"),
                        text(whenMap.get("source"), "when.source"),
                        trigger.get("recipientRole") == null
                                ? null : text(trigger.get("recipientRole"), "recipientRole")));
            }
            return new PolicyDefinition(policyKey, defaultChannel, triggers);
        } catch (BusinessProblem problem) {
            throw problem;
        } catch (RuntimeException exception) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "NOTIFICATION definitionJson is invalid: " + exception.getMessage());
        }
    }

    private static String text(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is invalid");
        }
        return text.trim();
    }

    private record PolicyDefinition(
            String policyKey,
            String defaultChannel,
            List<TriggerDefinition> triggers
    ) {
    }

    private record TriggerDefinition(
            String triggerKey,
            String eventType,
            String templateKey,
            String channel,
            String whenLanguage,
            String whenSource,
            String recipientRole
    ) {
    }
}
