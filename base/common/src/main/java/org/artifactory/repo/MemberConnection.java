package org.artifactory.repo;

import jakarta.annotation.Nonnull;

public record MemberConnection(@Nonnull String localRepoKey, @Nonnull String localPlatformUrl,
                               @Nonnull String remoteRepoKey, @Nonnull String remotePlatformUrl, @Nonnull String status,
                               Long lagInMs, Long queuedEvents, boolean disabled, boolean supported) {
}
