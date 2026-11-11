package org.artifactory.addon.packagesarchive;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.model.packagearchive.PackageArchiveQuery;
import com.jfrog.ph.acl.model.packagearchive.PackageArchivesResult;
import lombok.NonNull;
import org.artifactory.common.addon.Addon;

import java.util.List;

/**
 * @author Ron Malka
 */

public interface PackagesArchiveAddon extends Addon {
    /**
     * @return Packages to archive by specific package type according to the parameters in the packageArchiveQuery.
     */
    default PackageArchivesResult getPackagesToArchive(PackageArchiveQuery query) throws PackageException {
        return new PackageArchivesResult(List.of(), null);
    }

    /**
     * @return Packages count of the packageType according to the parameters in the packageArchiveQuery.
     * @throws PackageException If the package type is not supported.
     */
    default int getPackagesCountToArchive(@NonNull PackageArchiveQuery query) throws PackageException {
        return 0;
    }

    /**
     * Validates the given `PackageArchiveQuery`.
     *
     * @param query the `PackageArchiveQuery` to validate
     * @return `true` if the query is valid, otherwise `false`
     */
    default boolean validateQuery(PackageArchiveQuery query) {
        return true;
    }
}
