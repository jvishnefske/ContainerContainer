package org.artifactory.repo.service.trash.rdtt;

import lombok.Data;

import jakarta.validation.constraints.Positive;

/**
 * A request object for Remove Directories Trashcan Time REST API.
 *
 * @author Evgeny Kurtser
 */
@Data
public final class RemoveDirsTrashcanTimeRequestModel {
    private static final int BATCH_SIZE = 1_000;
    private static final long PAUSE_BETWEEN_BATCHES_MS = 1_000L;
    private static final int BATCHES_COUNT = -1;
    public static final RemoveDirsTrashcanTimeRequestModel EMPTY = new RemoveDirsTrashcanTimeRequestModel();

    @Positive(message = "Should be non-negative integer")
    private int batchSize = BATCH_SIZE;
    @Positive(message = "Should be non-negative integer")
    private long pauseBetweenBatchesMs = PAUSE_BETWEEN_BATCHES_MS;
    private int batchesCount = BATCHES_COUNT;
}