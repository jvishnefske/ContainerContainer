package org.artifactory.federation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.artifactory.repo.ConnectionStatesRtfsResponse;
import org.artifactory.repo.RepoBinariesStateRtfsResponse;
import org.artifactory.repo.StateListRtfsResponse;
import org.artifactory.repo.StateSummaryRtfsResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DefaultFederatedStatusServiceImpl implements FederatedStatusService {

    @Override
    public Map<String, Integer> getFederatedRepoStatuses() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public List<MirrorLagInfo> getMirrorsLag() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public List<MirrorLagInfo> getMirrorsLagWithThreshold(@Nullable Long thresholdInMs) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public List<UnavailableMirrorInfo> getUnavailableMirrorsByTypeAndDifferentProvidedStatus() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public StateListRtfsResponse getFederationStateList(List<String> statuses) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public StateSummaryRtfsResponse getFederationSummary() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public ConnectionStatesRtfsResponse getConnectionStates(String repoKey) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public RepoBinariesStateRtfsResponse getRepoBinariesState(String repoKey, String remoteUrl) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public List<JpdFederationStatus> getAllJpdFederationStatus() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public FederatedMirrorEventsStatusInfo getFederatedMirrorEventsStatus(String repoKey, Long currentTime,
            boolean v2) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public FederatedArtifactsStatusInfo getFederatedArtifactsRepoStatus(
            String repoKey) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public JpdFederationStatus getCurrentJpdFederationStatus() {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Override
    public FederatedRepoStatusInfo getFederatedRepoStatus(String repoKey, Long currentTime, boolean v2) {
        throw new UnsupportedOperationException(getUnsupportedMessage());
    }

    @Nonnull
    private String getUnsupportedMessage() {
        return "Unsupported operation: federated";
    }

}
