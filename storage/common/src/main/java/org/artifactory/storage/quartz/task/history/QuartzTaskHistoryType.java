package org.artifactory.storage.quartz.task.history;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum QuartzTaskHistoryType {
    CLEANUP("cleanup");

    @Getter
    private final String code;
}