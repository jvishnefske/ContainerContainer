package org.artifactory.rest.resource.retention.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record PackagesDto(
        String path
) {
}