package org.artifactory.federation;


import lombok.AllArgsConstructor;
import lombok.Getter;
import org.artifactory.storage.federation.FederatedArtifactStatus;
import org.jfrog.storage.binstore.providers.base.BinariesTasksInfo;

/**
 * @author Daniel simantov
 * This class represents the status information for federated artifacts.
 * It includes information on two types of tasks: tasks that are waiting to download artifacts and tasks that have
 * failed.
 * The class also provides information on the status of federated artifacts, specifically the number of artifacts
 * that exist in their entirety.
 */
@AllArgsConstructor
@Getter
public class FederatedArtifactsStatusInfo {

    private String localKey;
    private BinariesTasksInfo binariesTasksInfo;
    private FederatedArtifactStatus federatedArtifactStatus;
}
