package com.serviceos.fieldwork.application;

import java.util.UUID;

/** 项目级围栏策略快照；策略版本随 Visit 冻结，后续变更不得改写历史判断。 */
public record GeofencePolicy(
        UUID projectId,
        double targetLatitude,
        double targetLongitude,
        double radiusMeters,
        double maxAccuracyMeters,
        String exceptionAction,
        String policyVersion
) {
}
