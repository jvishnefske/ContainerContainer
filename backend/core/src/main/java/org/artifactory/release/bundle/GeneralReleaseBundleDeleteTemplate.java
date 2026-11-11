package org.artifactory.release.bundle;

import lombok.NonNull;
import org.artifactory.api.rest.distribution.bundle.models.ArtifactsBundleModel;
import org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao;
import org.artifactory.storage.db.bundle.model.BundleNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * @author Uriah Levy
 */
public class GeneralReleaseBundleDeleteTemplate implements ReleaseBundleDeleteTemplate {
    private static final Logger log = LoggerFactory.getLogger(DerbyReleaseBundleDeleteTemplate.class);

    private final ArtifactBundlesDao bundlesDao;
    private final InternalReleaseBundleService internalReleaseBundleService;

    public GeneralReleaseBundleDeleteTemplate(ArtifactBundlesDao bundlesDao,
            InternalReleaseBundleService internalReleaseBundleService) {
        this.bundlesDao = bundlesDao;
        this.internalReleaseBundleService = internalReleaseBundleService;
    }

    @Override
    public void deleteBundle(@NonNull ArtifactsBundleModel artifactsBundle,
            @NonNull List<BundleNode> bundleNodes,
            @NonNull Set<Long> nodeIdsWithArtifactsToDelete) throws SQLException {
        log.debug("Deleting all files related to Release Bundle {} from table while deleting content",
                artifactsBundle.getId());

        bundlesDao.deleteBundleNodes(artifactsBundle.getId());
        internalReleaseBundleService.undeployIfNeeded(artifactsBundle, bundleNodes,
                nodeIdsWithArtifactsToDelete);
    }
}
