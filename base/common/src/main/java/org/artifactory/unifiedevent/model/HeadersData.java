package org.artifactory.unifiedevent.model;

import lombok.Builder;

@Builder
public record HeadersData(
        String filterableData
) {
}
