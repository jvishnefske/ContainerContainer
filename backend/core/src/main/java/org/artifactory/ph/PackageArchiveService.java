package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.model.packagearchive.PackageArchive;
import com.jfrog.ph.acl.model.packagearchive.PackageArchiveQuery;
import com.jfrog.ph.acl.model.packagearchive.PackageArchivesResult;
import lombok.NonNull;
import org.artifactory.common.repo.PackageType;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface PackageArchiveService {
    /**
     * @return if the package are supported in packages archiving.
     */
    boolean isPackageArchivingSupported(@NonNull PackageType packageType);

    /**
     * @return Set of package archiving supported packages config
     */
    default Collection<PackageArchiveConfig> getAllSupportedPackages() {
        return Set.of();
    }

    /**
     * @return packages of the packageType according to the parameters in the packageArchiveQuery.
     */
    @Deprecated(since = "7.79.0", forRemoval = true)
    List<PackageArchive> getPackageToArchive(@NonNull PackageArchiveQuery query) throws PackageException;

    PackageArchivesResult getPackageToArchiveResult(@NonNull PackageArchiveQuery query) throws PackageException;

    /**
     * @return Packages count of the packageType according to the parameters in the packageArchiveQuery.
     */
    default int getPackagesCountToArchive(@NonNull PackageArchiveQuery query) throws PackageException {
        return 0;
    }

    /**
     * Validates the given `PackageArchiveQuery`.
     *
     * @param query the `PackageArchiveQuery` to validate
     * @return `true` if the query is valid, otherwise `false`
     * @throws PackageException if the package type isn't supported
     */
    default boolean validateQuery(PackageArchiveQuery query) throws PackageException {
        return true;
    }

    /**
     * @return the package according to the parameters
     */
    @NonNull
    PackageArchive getPackageVersion(
            @NonNull String packageType,
            @NonNull String packageRepoName,
            @NonNull String packageName,
            @NonNull String packageVersion
    ) throws PackageException;
}
