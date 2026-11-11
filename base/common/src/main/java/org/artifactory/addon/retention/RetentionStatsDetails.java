package org.artifactory.addon.retention;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Oleksandr Legenkiy
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public final class RetentionStatsDetails {

    private long policiesCount;
    private long archiveRunCount;
    private long archiveSuccessCount;
    private long archiveFailureCount;
    private long archiveSuccessSize;
    private long archiveFailureSize;
    private long restoreRunCount;
    private long restoreSuccessCount;
    private long restoreFailureCount;
    private long restoreSuccessSize;
    private long restoreFailureSize;

}
