package org.artifactory.storage.db.event.error.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;

import jakarta.annotation.Nonnull;

@Value
@EqualsAndHashCode
@AllArgsConstructor
public class RepoAndCategory {
    @Nonnull
    String repoKey;
    @Nonnull
    String eventCategory;
}
