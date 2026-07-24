package com.serviceos.configuration.api;

/** 履约版本中的业务阶段；只承担组织、展示和统计，不参与流程推进。 */
public record ProjectFulfillmentPhaseDraft(
        String phaseId,
        String phaseName,
        int sequence,
        String description,
        String displayColor
) {
}
