package com.serviceos.network.api;

/** 未完成工作影响摘要，用于清退待办与停用决策。 */
public record NetworkWorkImpact(
        int openTasks,
        int openAppointments,
        int openVisits,
        int activeAssignments,
        int offlinePackages
) {}
