package org.artifactory.repo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;



/**
 * This class represents a summary of federation states - the amount of members of each state.
 * @author Shahar Palmor
 */

@Getter
@Builder
public class StateSummaryRtfsResponse {
    private FederationStates federationStates;
    @AllArgsConstructor
    @Data
    public static class FederationStates {
        @JsonProperty("HEALTHY")
        long healthy;
        @JsonProperty("READ_ONLY")
        long readOnly;
        @JsonProperty("NOT_AVAILABLE")
        long notAvailable;
        @JsonProperty("DELAYED")
        long delayed;
        @JsonProperty("PENDING_FS")
        long pendingFs;
        @JsonProperty("FULL_SYNC_RUNNING")
        long fullSyncRunning;
        @JsonProperty("ERROR")
        long error;
        @JsonProperty("DISABLED")
        long disabled;
    }
}