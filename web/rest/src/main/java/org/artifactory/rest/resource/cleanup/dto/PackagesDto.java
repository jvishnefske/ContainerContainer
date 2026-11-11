package org.artifactory.rest.resource.cleanup.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record PackagesDto(
        String path
) {
}
