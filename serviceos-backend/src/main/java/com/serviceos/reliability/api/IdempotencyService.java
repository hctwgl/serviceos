package com.serviceos.reliability.api;

import com.serviceos.shared.CommandContext;

/**
 * 幂等记录公开端口。调用者必须在自己的领域事务内调用 begin 与 complete。
 */
public interface IdempotencyService {
    IdempotencyDecision begin(CommandContext context, String operationType, String requestDigest);

    void complete(CommandContext context, String operationType, String resourceId, String responseDigest);
}
