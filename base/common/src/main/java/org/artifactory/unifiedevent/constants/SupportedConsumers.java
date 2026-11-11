package org.artifactory.unifiedevent.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SupportedConsumers {
    WORKER("Worker"),
    WORKER_CLIENT("workerClient"),
    XRAY("xray"),
    UNKNOWN("unknown");

    private final String name;
}
