package org.artifactory.storage.federation;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FederatedArtifactStatus {
    private Long countFullyReplicateArtifacts = 0L;
    private Long countArtificiallyReplicatedArtifacts = 0L;
}
