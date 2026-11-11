package org.artifactory.federation;


import lombok.AllArgsConstructor;
import lombok.Getter;
import org.artifactory.storage.federation.FederatedArtifactStatus;
import org.jfrog.storage.binstore.providers.base.BinariesTasksInfo;

import java.util.List;

@AllArgsConstructor
@Getter
public class FederatedRepoStatusInfo {

    private String localKey;
    private BinariesTasksInfo binariesTasksInfo;
    private List<MirrorEventsStatusInfo> mirrorEventsStatusInfo;
    private FederatedArtifactStatus federatedArtifactStatus;
}
