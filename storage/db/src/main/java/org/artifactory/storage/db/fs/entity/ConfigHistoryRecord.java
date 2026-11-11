package org.artifactory.storage.db.fs.entity;

import lombok.Builder;

@Builder
public record ConfigHistoryRecord(
        int id,
        String configData,
        String repoConfigData,
        String logConfigData
) {
}
