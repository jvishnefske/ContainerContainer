package org.artifactory.addon.worker;

import lombok.Builder;

@Builder
public record WorkerEventsModel(
        String name,
        String service,
        Meta meta,
        Sample sample,
        String payloadDocumentation,
        boolean supportProjects,
        boolean isAsync,
        String filterType,
        boolean mandatoryFilter,
        String executionRequestType
) {
    @Builder
    public record Meta(
            String description,
            String docUrl
    ) {
    }

    @Builder
    public record Sample(
            String payload,
            String code
    ) {
    }
}
