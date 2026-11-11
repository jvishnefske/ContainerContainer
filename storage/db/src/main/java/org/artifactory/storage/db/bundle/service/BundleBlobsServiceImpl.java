package org.artifactory.storage.db.bundle.service;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.bundle.BundleType;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.bundle.dao.BundleBlobsDao;
import org.artifactory.storage.db.bundle.model.DBBundleBlobsResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nullable;
import java.sql.SQLException;

/**
 * @author Eli Skoran 10/08/2020
 */
@Slf4j
@Service
public class BundleBlobsServiceImpl implements BundleBlobsService {

    private final BundleBlobsDao bundleBlobsDao;

    @Autowired
    public BundleBlobsServiceImpl(@NonNull BundleBlobsDao bundleBlobsDao) {
        this.bundleBlobsDao = bundleBlobsDao;
    }

    @Override
    @Nullable
    public DBBundleBlobsResult getBundleBlobData(@NonNull String bundleName, @NonNull String bundleVersion,
            @NonNull BundleType bundleType) {
        try {
            return bundleBlobsDao.getBundleBlobData(bundleName, bundleVersion, bundleType);
        } catch (SQLException e) {
            String message = String.format("Error while getting bundle blob data for %s/%s", bundleName, bundleVersion);
            throw new StorageException(message, e);
        }
    }
}
