package org.artifactory.storage.db.bundle.service;

import lombok.NonNull;
import org.artifactory.bundle.BundleType;
import org.artifactory.storage.db.bundle.model.BundleNode;
import org.artifactory.storage.db.bundle.model.DBArtifactsBundle;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * @author Eli Skoran 10/08/2020
 */
public interface ArtifactBundlesService {

    /**
     * Gets the Release bundle metadata object
     */

    @Nullable
    DBArtifactsBundle getArtifactsBundle(@NonNull String bundleName, @NonNull String bundleVersion,
            @NonNull BundleType source);

    /**
     * Gets the Artifacts list for the release bundle version.
     *
     * @param bundleId - the id of the release bundle (taken from the artifacts_bundle table)
     */
    @NonNull
    List<BundleNode> getBundleNodes(long bundleId);

}
