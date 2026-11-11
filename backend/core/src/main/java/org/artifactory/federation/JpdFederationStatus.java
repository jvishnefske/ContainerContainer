package org.artifactory.federation;

import jakarta.annotation.Nonnull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dor Solomon
 *
 * Federation status info of JDP
 */
@NoArgsConstructor
@Data
public class JpdFederationStatus implements Comparable<JpdFederationStatus> {

    private String url;
    private Long binariesToFetch;
    private Long binariesFetchFailures;
    private List<MirrorLagInfo> mostLaggingMirrors;
    private SyncStatus syncStatus;
    private List<String> errorMessages;
    private Long mirrorLagThresholdInMS;

    public JpdFederationStatus(String url) {
        this.url = url;
    }

    public void addErrorMessage(String errorMessage) {
        if (errorMessages == null) {
            errorMessages = new ArrayList<>();
        }
        errorMessages.add(errorMessage);
    }

    @Override
    public int compareTo(@Nonnull JpdFederationStatus other) {
        if (url == null) {
            return -1;
        }
        if (other.url == null) {
            return 1;
        }
        return this.url.compareTo(other.url);
    }
}