package org.artifactory.unifiedevent.model;

import lombok.Builder;

import java.util.List;

@Builder
public record IncludeExclude(
        List<String> include,
        List<String> exclude
) {
}
