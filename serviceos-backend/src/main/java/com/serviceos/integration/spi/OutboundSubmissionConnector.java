package com.serviceos.integration.spi;

import java.util.Optional;

/**
 * 出站提审/外发连接器 SPI。
 *
 * <p>协议签名、HTTP 发送与技术 ACK 解释留在 OEM 适配器；通用管道负责 attempt 登记、
 * 响应私有存储、Delivery 状态迁移与本地幂等落账。适配器不得直接写领域业务表。</p>
 */
public interface OutboundSubmissionConnector {
    ConnectorIdentity identity();

    /** 绑定的自动任务类型，例如 {@code integration.byd.submit-review}。 */
    String taskType();

    /** 响应对象键中的协议段，例如 {@code byd-cpim/submit-review}。 */
    String responseStorageSegment();

    /**
     * 发送前配置门禁。返回非空错误码时，管道在未创建 attempt 前以 FAILED_FINAL 结束。
     */
    Optional<String> preflightErrorCode();

    /**
     * 基于冻结 payload 生成签名请求。失败应抛运行时异常，由管道记为 final failure。
     */
    SignedOutboundRequest prepare(OutboundSubmissionRequest request);

    /**
     * 事务外单次网络发送；不得内部重试。
     */
    OutboundTransportResult send(SignedOutboundRequest signed);

    /**
     * 将 HTTP 响应解释为技术 ACK（接受 / 拒绝 / UNKNOWN）。
     */
    OutboundTechnicalAcknowledgement interpret(int httpStatus, byte[] body);
}
