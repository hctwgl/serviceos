package com.serviceos.task.api;

/** 候选快照的权威来源；来源内容必须另有稳定 ID，不能只靠自由文本解释。 */
public enum AssignmentSourceType {
    ASSIGNEE_POLICY,
    MANUAL,
    SYSTEM
}
