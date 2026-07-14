/**
 * 预约模块：拥有 Appointment、不可变 Revision 与状态历史，不拥有 Task、ServiceAssignment 或通知。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Appointment",
        allowedDependencies = {
                "shared", "identity::api", "authorization::api", "audit::api",
                "reliability::api", "task::api", "dispatch::api"
        }
)
package com.serviceos.appointment;
