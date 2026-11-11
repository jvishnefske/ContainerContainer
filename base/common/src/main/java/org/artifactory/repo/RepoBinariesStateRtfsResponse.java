package org.artifactory.repo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * This class represents the binaries state result from the Rtfs service
 *
 * @author Shahar Palmor
 */
@Data
@Builder
public class RepoBinariesStateRtfsResponse {
    private String repoKey;
    private String platformUrl;
    private BinariesTasksInfo binariesTasksInfo;

    @AllArgsConstructor
    @Data
    public static class BinariesTasksInfo {
        private Long inProgressTasks = 0L;
        private Long failingTasks = 0L;
    }
}