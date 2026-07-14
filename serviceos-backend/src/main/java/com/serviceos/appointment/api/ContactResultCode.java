package com.serviceos.appointment.api;

/** 联系结果使用受控代码，避免报表和 SLA 计算依赖自由文本。 */
public enum ContactResultCode {
    CONNECTED,
    NO_ANSWER,
    BUSY,
    WRONG_NUMBER,
    USER_REQUESTED_LATER,
    INVALID_CONTACT
}
