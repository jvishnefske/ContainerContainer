package org.artifactory.storage.db.event.error.model;


import lombok.Builder;
import lombok.Value;
import org.artifactory.event.EventType;
import org.artifactory.repo.RepoPath;

import javax.annotation.Nonnegative;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Mor Merhav
 */
@Value
@Builder(toBuilder = true, builderClassName = "Builder")
public class NodeEventsErrorInfo implements ErrorStatistics {
    @Nonnull
    String eventCategory;
    @Nonnull
    EventType eventType;
    @Nonnull
    RepoPath path;
    String operationKey;
    @Nullable
    Long eventId;
    @Nonnegative
    long eventTime;
    @Nonnegative
    int errorCount;
    @Nonnull
    String errorMessage;
    @Nonnegative
    long firstErrorTime;
    @Nonnegative
    long lastErrorTime;
}
