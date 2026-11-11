package org.artifactory.release.bundle;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.rest.distribution.bundle.models.ArtifactsBundleModel;
import org.artifactory.storage.db.bundle.model.BundleNode;
import org.artifactory.storage.tx.SessionResourceAdapter;

import java.util.List;
import java.util.Set;

@Slf4j
@NoArgsConstructor
@Getter
public class DeleteReleaseBundleArtifactsTransactionResource extends SessionResourceAdapter {


    private InternalReleaseBundleService internalReleaseBundleService;
    private ArtifactsBundleModel artifactsBundle;
    private List<BundleNode> bundleNodes;
    private Set<Long> nodeIdsWithArtifactsToDelete;

    public void init(InternalReleaseBundleService internalReleaseBundleService, ArtifactsBundleModel bundleModel,
            List<BundleNode> bundleNodes, Set<Long> nodeIdsWithArtifactsToDelete) {
        this.internalReleaseBundleService = internalReleaseBundleService;
        this.artifactsBundle = bundleModel;
        this.bundleNodes = bundleNodes;
        this.nodeIdsWithArtifactsToDelete = nodeIdsWithArtifactsToDelete;
    }

    @Override
    public void afterConnectionReleased(boolean committed) {
        if (committed) {
            internalReleaseBundleService.undeployIfNeeded(artifactsBundle, bundleNodes, nodeIdsWithArtifactsToDelete);
        }
    }
}
