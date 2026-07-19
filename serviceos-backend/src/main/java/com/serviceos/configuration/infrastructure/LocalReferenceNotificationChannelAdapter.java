package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.NotificationChannelPort;
import com.serviceos.configuration.api.NotificationDeliveryResult;
import com.serviceos.configuration.api.NotificationSendRequest;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地参考通知通道：无外部凭据时使用。
 *
 * <p>IN_APP/PUSH 记为 SENT；SMS/EMAIL 在无凭据环境下记为 UNKNOWN（不得伪造成功），
 * 并按幂等键去重。</p>
 */
@Component
public class LocalReferenceNotificationChannelAdapter implements NotificationChannelPort {
    private static final Set<String> SUPPORTED = Set.of("IN_APP", "SMS", "EMAIL", "PUSH");

    private final ConcurrentHashMap<String, NotificationDeliveryResult> idempotentResults =
            new ConcurrentHashMap<>();

    @Override
    public boolean supports(String channel) {
        return channel != null && SUPPORTED.contains(channel.trim());
    }

    @Override
    public NotificationDeliveryResult send(NotificationSendRequest request) {
        NotificationDeliveryResult cached = idempotentResults.get(request.idempotencyKey());
        if (cached != null) {
            return switch (cached) {
                case NotificationDeliveryResult.Sent sent ->
                        new NotificationDeliveryResult.Sent(sent.providerMessageId(), true);
                case NotificationDeliveryResult.Unknown unknown -> unknown;
                case NotificationDeliveryResult.Failed failed -> failed;
            };
        }
        NotificationDeliveryResult result = switch (request.channel()) {
            case "IN_APP", "PUSH" -> new NotificationDeliveryResult.Sent(
                    "local-ref-" + Sha256.digest(request.idempotencyKey()).substring(0, 16), false);
            case "SMS", "EMAIL" -> new NotificationDeliveryResult.Unknown(
                    "LOCAL_REFERENCE_NO_CREDENTIALS");
            default -> new NotificationDeliveryResult.Failed("UNSUPPORTED_CHANNEL");
        };
        NotificationDeliveryResult previous = idempotentResults.putIfAbsent(request.idempotencyKey(), result);
        if (previous != null) {
            return switch (previous) {
                case NotificationDeliveryResult.Sent sent ->
                        new NotificationDeliveryResult.Sent(sent.providerMessageId(), true);
                case NotificationDeliveryResult.Unknown unknown -> unknown;
                case NotificationDeliveryResult.Failed failed -> failed;
            };
        }
        return result;
    }
}
