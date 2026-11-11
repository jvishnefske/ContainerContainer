package org.artifactory.unifiedevent.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record DispatcherResponse(
        boolean shouldProceed,
        String message,
        ResponseContextData data
) {
}
