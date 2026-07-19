package com.serviceos.configuration.api;

/**
 * 领域事件驱动的 NOTIFICATION 可靠投递入口。
 *
 * <p>Inbox 去重、角色收件人解析、通道发送与 Intent/Delivery/Attempt 必须同事务。</p>
 */
public interface NotificationEventDispatchService {
    void dispatch(NotificationEventDispatchCommand command);
}
