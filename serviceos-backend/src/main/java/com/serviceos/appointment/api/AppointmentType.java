package com.serviceos.appointment.api;

/** 受控预约类型；首期覆盖 MVP 现场服务类型，不接受自由文本扩散。 */
public enum AppointmentType {
    SURVEY,
    INSTALLATION,
    REPAIR,
    CORRECTION,
    SECOND_VISIT
}
