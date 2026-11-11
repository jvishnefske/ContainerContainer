package org.artifactory.storage.db.bundle.service;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.bundle.BundleType;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao;
import org.artifactory.storage.db.bundle.model.BundleNode;
import org.artifactory.storage.db.bundle.model.DBArtifactsBundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;

/**
 * @author Eli Skoran 10/08/2020
 */
@Slf4j
@Service
public class ArtifactBundlesServiceImpl implements ArtifactBundlesService {

    private final ArtifactBundlesDao artifactBundlesDao;

    @Autowired
    public ArtifactBundlesServiceImpl(@NonNull ArtifactBundlesDao artifactBundlesDao) {
        this.artifactBundlesDao = artifactBundlesDao;
    }

    @Override
    @Nullable
    public DBArtifactsBundle getArtifactsBundle(@NonNull String bundleName, @NonNull String bundleVersion,
            @NonNull BundleType source) {
        try {
            return artifactBundlesDao.getArtifactsBundle(bundleName, bundleVersion, source);
        } catch (SQLException e) {
            String message = String.format("Error while getting artifacts bundle for %s/%s", bundleName, bundleVersion);
            throw new StorageException(message, e);
        }
    }

    @Override
    @NonNull
    public List<BundleNode> getBundleNodes(long bundleId) {
        try {
            return artifactBundlesDao.getBundleNodes(bundleId);
        } catch (SQLException e) {
            String message = String.format("Error while getting bundle nodes for %d", bundleId);
            throw new StorageException(message, e);
        }
    }
}
