package org.artifactory.federation;

import org.artifactory.hqc.enums.HeavyQueryCacheType;
import org.artifactory.repo.ConnectionStatesRtfsResponse;
import org.artifactory.repo.RepoBinariesStateRtfsResponse;
import org.artifactory.repo.StateListRtfsResponse;
import org.artifactory.repo.StateSummaryRtfsResponse;
import org.jfrog.storage.hqc.model.HeavyQueryCache;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;

public interface FederatedStatusService {

    Map<String, Integer> getFederatedRepoStatuses();
    /**
     * @return list of federated mirrors that have lag from activating their next event
     */
    List<MirrorLagInfo> getMirrorsLag();

    /**
     * @return list of federated mirrors that have lag from activating their next event
     */
    List<MirrorLagInfo> getMirrorsLagWithThreshold(@Nullable Long thresholdInMs);

    /**
     * @return status for provided repo
     */
    FederatedRepoStatusInfo getFederatedRepoStatus(String repoKey, Long currentTime, boolean v2);

    /**
     * @return mirror status for provided repo
     */
    FederatedMirrorEventsStatusInfo getFederatedMirrorEventsStatus(String repoKey, Long currentTime, boolean v2);

    /**
     * @return Binaries status
     */
    @HeavyQueryCache(
            hqcType = HeavyQueryCacheType.TOTAL_ARTIFACT_PER_REPO,
            minCallableDurationMillis = 2000,
            expiryFactor = 10,
            expiryUpperBound = 180_000, // 3 minute
            expiryLowerBound = 20000 // 20 second
    )
    FederatedArtifactsStatusInfo getFederatedArtifactsRepoStatus(String repoKey);

    /**
     * @return federation status of all JPDs
     */
    List<JpdFederationStatus> getAllJpdFederationStatus();

    /**
     * @return federation status of the current JPD
     */
    JpdFederationStatus getCurrentJpdFederationStatus();

    /**
     * @return list of federated mirrors that are not in "HEALTHY" status
     */
    List<UnavailableMirrorInfo> getUnavailableMirrorsByTypeAndDifferentProvidedStatus();

    /**
     * @param statuses - List of specific statuses to include
     * @return StateListRtfsResponse which represents the state list of all the federations
     */
    StateListRtfsResponse getFederationStateList(List<String> statuses);

    /**
     * @return StateSummaryRtfsResponse which represents the state summary
     */
    StateSummaryRtfsResponse getFederationSummary();

    /**
     * @param repoKey - The repository key for the connection states request to run against
     * @return The connection states result which represents details of each mirror related to this repo
     */
    ConnectionStatesRtfsResponse getConnectionStates(String repoKey);

    RepoBinariesStateRtfsResponse getRepoBinariesState(String repoKey, String remoteUrl);
}
