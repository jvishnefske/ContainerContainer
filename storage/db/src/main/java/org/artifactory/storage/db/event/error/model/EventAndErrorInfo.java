package org.artifactory.storage.db.event.error.model;

import lombok.Builder;
import lombok.Value;
import org.artifactory.repo.event.EventInfo;

import javax.annotation.Nonnegative;
import jakarta.annotation.Nonnull;

/**
 * @author avishayh
 */
@Value
@Builder
public class EventAndErrorInfo implements ErrorStatistics {
    @Nonnull
    EventInfo eventInfo;
    @Nonnegative
    long errorId;
    @Nonnegative
    int errorCount;
    @Nonnegative
    long firstErrorTime;
}
