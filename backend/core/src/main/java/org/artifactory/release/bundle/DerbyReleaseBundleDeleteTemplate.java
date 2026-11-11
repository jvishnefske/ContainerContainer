package org.artifactory.release.bundle;

import lombok.NonNull;
import lombok.Synchronized;
import org.artifactory.api.rest.distribution.bundle.models.ArtifactsBundleModel;
import org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao;
import org.artifactory.storage.db.bundle.model.BundleNode;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * A release bundle delete template to avoid read-lock related deadlocks with Derby DB
 *
 * @author Omri Ziv
 */
public class DerbyReleaseBundleDeleteTemplate extends GeneralReleaseBundleDeleteTemplate
        implements ReleaseBundleDeleteTemplate {

    public DerbyReleaseBundleDeleteTemplate(@NonNull ArtifactBundlesDao bundlesDao,
            @NonNull InternalReleaseBundleService internalReleaseBundleService) {
        super(bundlesDao, internalReleaseBundleService);
    }

    @Override
    @Synchronized
    public void deleteBundle(@NonNull ArtifactsBundleModel artifactsBundle, @NonNull List<BundleNode> bundleNodes,
            @NonNull Set<Long> nodeIdsWithArtifactsToDelete) throws SQLException {
        super.deleteBundle(artifactsBundle, bundleNodes, nodeIdsWithArtifactsToDelete);
    }

}
