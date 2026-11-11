package org.artifactory.livelogs;

import lombok.NonNull;

import java.util.List;

/**
 * @author Andrey Tashlitsky
 */
public interface LiveLogsService {

    /**
     * Verify the requested log name is not empty.
     *
     * @param selectedLog Requested log name.
     */
    void assertIdIsNotEmpty(String selectedLog);

    /**
     * Verify the user is not trying to send too many requests one after the other.
     *
     * @param selectedLog Requested log name.
     */
    void assertUserCanRefreshLog(@NonNull String selectedLog);

    /**
     * Verify the Live Logs feature is enabled.
     */
    void assertLiveLogsFeatureIsEnabled();

    /**
     * Verify the requested log is one of the allowed logs.
     *
     * @param selectedLog Requested log name.
     */
    void assertRequestedLogIsValid(@NonNull String selectedLog);

    /**
     * Verify the user is allowed to get the log data.
     *
     * @param selectedLog Requested log name.
     */
    void assertCanGetLogData(@NonNull String selectedLog);

    /**
     * Build a config model and return it.
     *
     * @return Config model.
     */
    LiveLogsConfig getLiveLogsConfig();

    /**
     * Build the data model based on the selected log and return it.
     *
     * @param selectedLog Requested log name.
     * @param lastPointer The pointer to start of logs (in bytes).
     * @return Data model.
     */
    LiveLogsData getLiveLogsData(@NonNull String selectedLog, long lastPointer);

    /**
     * Get additional sensitive regex string to filter the logs.
     *
     * @return List of regex strings.
     */
    List<String> getAdditionalSensitiveRegex();

    /**
     * Get the read block size.
     *
     * @return Integer - the read block size in Kilobyte.
     */
    int getReadBlockSizeFromSystemYaml();
}
