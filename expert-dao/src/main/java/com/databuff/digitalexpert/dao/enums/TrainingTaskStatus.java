package com.databuff.digitalexpert.dao.enums;

import java.util.List;

public enum TrainingTaskStatus {
    PENDING,
    RUNNING,
    VERIFYING_ARTIFACTS,
    IMPORTING_SKILLS,
    RELEASING,
    SUCCEEDED,
    FAILED,
    TIMEOUT;

    private static final List<String> ACTIVE_TASK_STATUSES = List.of(
            PENDING.name(),
            RUNNING.name(),
            VERIFYING_ARTIFACTS.name(),
            IMPORTING_SKILLS.name(),
            RELEASING.name()
    );

    private static final List<String> POLLING_STATUSES = List.of(
            RUNNING.name(),
            RELEASING.name()
    );

    public static List<String> activeTaskStatuses() {
        return ACTIVE_TASK_STATUSES;
    }

    public static List<String> pollingStatuses() {
        return POLLING_STATUSES;
    }
}
