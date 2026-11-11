package org.artifactory.ph;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.repo.PackageType;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.service.PackageSpecificationAdapter;
import org.artifactory.model.LocalConfig;
import org.artifactory.repo.service.trash.NonTrashableItem;

import static org.artifactory.ph.PackageRepoTypeMapper.packageType;

/**
 * The purpose of this class is to determine if deleted artifact should be skipped from trash for expirable files in
 * ph packages
 */
@Slf4j
public class PackageMetadataNonTrashableItem implements NonTrashableItem {
    private final PackageSpecificationAdapter packageSpecificationAdapter;

    public PackageMetadataNonTrashableItem(PackageSpecificationAdapter packageSpecificationAdapter) {
        this.packageSpecificationAdapter = packageSpecificationAdapter;
    }

    @Override
    public boolean skipTrash(LocalConfig config, String path) {
        try {
            PackageType packageType = packageType(config.getPackageType());
            String repoKey = config.getKey();
            return packageType != null &&
                    repoKey != null &&
                    packageSpecificationAdapter.isExpirable(repoKey, packageType, path);
        } catch (PackageException e) {
            log.debug("Failed to determine if deleted artifact '{}' should be skipped from trash:", path, e);
        }
        return false;
    }
}
