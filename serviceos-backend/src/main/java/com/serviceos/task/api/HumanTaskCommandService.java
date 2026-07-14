package com.serviceos.task.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

/** 人工任务状态机命令入口。HTTP 与内部适配器都必须通过该边界。 */
public interface HumanTaskCommandService {
    HumanTaskCommandReceipt claim(CurrentPrincipal principal, CommandMetadata metadata, ClaimHumanTaskCommand command);
    HumanTaskCommandReceipt start(CurrentPrincipal principal, CommandMetadata metadata, StartHumanTaskCommand command);
    HumanTaskCommandReceipt complete(CurrentPrincipal principal, CommandMetadata metadata, CompleteHumanTaskCommand command);
}
