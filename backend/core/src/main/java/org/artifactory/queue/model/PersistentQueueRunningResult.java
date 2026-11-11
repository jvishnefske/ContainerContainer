package org.artifactory.queue.model;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

/**
 * @author avishayh
 */
@Value
public class PersistentQueueRunningResult {

    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final PersistentQueueRunningResult SUCCESS = new PersistentQueueRunningResult(-1, null, false);

    long failedMarker;
    @EqualsAndHashCode.Exclude
    String errorMessage;
    @EqualsAndHashCode.Exclude
    boolean omitError;

    private PersistentQueueRunningResult(long failedMarker, String errorMessage, boolean omitError) {
        this.failedMarker = failedMarker;
        this.errorMessage = StringUtils.abbreviate(errorMessage, MAX_MESSAGE_LENGTH);
        this.omitError = omitError;
    }

    public static PersistentQueueRunningResult success() {
        return SUCCESS;
    }

    public static PersistentQueueRunningResult failure(long marker, @NonNull String errorMessage, boolean omitError) {
        return new PersistentQueueRunningResult(marker, errorMessage, omitError);
    }

    public boolean isFailed() {
        return errorMessage != null;
    }
}
