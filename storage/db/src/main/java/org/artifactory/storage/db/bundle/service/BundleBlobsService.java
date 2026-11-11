package org.artifactory.storage.db.bundle.service;

import lombok.NonNull;
import org.artifactory.bundle.BundleType;
import org.artifactory.storage.db.bundle.model.DBBundleBlobsResult;

import jakarta.annotation.Nullable;

/**
 * @author Eli Skoran 10/08/2020
 */
public interface BundleBlobsService {

    /**
     * Gets the signed release bundle data (1 big signed blob)
     */
    @Nullable
    DBBundleBlobsResult getBundleBlobData(@NonNull String bundleName, @NonNull String bundleVersion,
            @NonNull BundleType bundleType);
}
