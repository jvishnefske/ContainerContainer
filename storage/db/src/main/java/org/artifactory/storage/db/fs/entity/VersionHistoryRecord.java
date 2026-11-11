package org.artifactory.storage.db.fs.entity;

import lombok.Builder;

@Builder
public record VersionHistoryRecord (
        int id,
        String version,
        int configVersionId,
        boolean backwardCompatible,
        boolean active
) {
}
