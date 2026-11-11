package org.artifactory.policy;

import lombok.Builder;

@Builder(toBuilder = true)
public record NodeMetadata(Long created, Long modified, Long lastDownloaded) {
}
