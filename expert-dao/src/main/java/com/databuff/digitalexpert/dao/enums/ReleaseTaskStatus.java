package com.databuff.digitalexpert.dao.enums;

import java.util.List;

public enum ReleaseTaskStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED;

    private static final List<String> ACTIVE_TASK_STATUSES = List.of(
            PENDING.name(),
            RUNNING.name()
    );

    public static List<String> activeTaskStatuses() {
        return ACTIVE_TASK_STATUSES;
    }
}
