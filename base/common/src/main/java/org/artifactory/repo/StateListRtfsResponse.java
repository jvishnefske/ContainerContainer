package org.artifactory.repo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.List;


/**
 * Represents the state list response received from a remote server's RTFS (Artifactory federation Service)
 * Contains the List of federation of the jpd the request was to.
 * each federation contains one or more server with the aggregated status, lag, disabled and supported details.
 * including the self perspective
 * @author Shahar Palmor
 */

@Getter
@Builder
public class StateListRtfsResponse {
    private List<FederationState> federations;


    @AllArgsConstructor
    @Data
    public static class FederationState {
        String localRepoKey;
        List<MemberInfo> memberInfoList;
        RepoPriority priority;
    }
    @AllArgsConstructor
    @Data
    public static class MemberInfo {
        String url;
        String repoKey;
        String aggregatedStatus;
        boolean isInLag;
        boolean isDisabled;
        boolean isSupported;
    }
}