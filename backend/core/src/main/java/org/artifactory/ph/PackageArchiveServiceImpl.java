package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageNotFoundException;
import com.jfrog.ph.acl.exception.PackageUnsupportedOperationException;
import com.jfrog.ph.acl.model.packagearchive.PackageArchive;
import com.jfrog.ph.acl.model.packagearchive.PackageArchiveQuery;
import com.jfrog.ph.acl.model.packagearchive.PackageArchivesResult;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.packagesarchive.PackagesArchiveAddon;
import org.artifactory.common.repo.PackageType;
import org.artifactory.util.CollectionUtils;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class PackageArchiveServiceImpl implements PackageArchiveService {
    public static final String PACKAGE_NOT_SUPPORTED_ERROR = "package type %s is not supported in package archiving";
    private final AddonsManager addonsManager;
    private static final Map<PackageType, PackageArchiveConfig> PACKAGES_ARCHIVING_SUPPORTED =
            new EnumMap<>(PackageType.class);

    static {
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.DOCKER, new PackageArchiveConfig(PackageType.DOCKER, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.MAVEN, new PackageArchiveConfig(PackageType.MAVEN, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.GRADLE, new PackageArchiveConfig(PackageType.GRADLE, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.OCI, new PackageArchiveConfig(PackageType.OCI, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.HELMOCI, new PackageArchiveConfig(PackageType.HELMOCI, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.NPM, new PackageArchiveConfig(PackageType.NPM, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.NUGET, new PackageArchiveConfig(PackageType.NUGET, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.YUM, new PackageArchiveConfig(PackageType.YUM, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.GENERIC, new PackageArchiveConfig(PackageType.GENERIC, false));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.CONAN, new PackageArchiveConfig(PackageType.CONAN, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.HELM, new PackageArchiveConfig(PackageType.HELM, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.DEBIAN, new PackageArchiveConfig(PackageType.DEBIAN, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.GO, new PackageArchiveConfig(PackageType.GO, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.PYPI, new PackageArchiveConfig(PackageType.PYPI, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.GEMS, new PackageArchiveConfig(PackageType.GEMS, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.TERRAFORM, new PackageArchiveConfig(PackageType.TERRAFORM, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.COCOAPODS, new PackageArchiveConfig(PackageType.COCOAPODS, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.HUGGINGFACEML,
                new PackageArchiveConfig(PackageType.HUGGINGFACEML, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.CARGO, new PackageArchiveConfig(PackageType.CARGO, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.TERRAFORMBACKEND,
                new PackageArchiveConfig(PackageType.TERRAFORMBACKEND, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.MACHINELEARNING,
                new PackageArchiveConfig(PackageType.MACHINELEARNING, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.ANSIBLE, new PackageArchiveConfig(PackageType.ANSIBLE, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.CONDA,
                new PackageArchiveConfig(PackageType.CONDA, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.SBT, new PackageArchiveConfig(PackageType.SBT, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.ALPINE,
                new PackageArchiveConfig(PackageType.ALPINE, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.HEX, new PackageArchiveConfig(PackageType.HEX, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.VAGRANT, new PackageArchiveConfig(PackageType.VAGRANT, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.CHEF, new PackageArchiveConfig(PackageType.CHEF, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.PUPPET, new PackageArchiveConfig(PackageType.PUPPET, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.SWIFT, new PackageArchiveConfig(PackageType.SWIFT, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.OPKG, new PackageArchiveConfig(PackageType.OPKG, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.COMPOSER, new PackageArchiveConfig(PackageType.COMPOSER, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.SKILLS, new PackageArchiveConfig(PackageType.SKILLS, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.AGENTPACKAGES,
                new PackageArchiveConfig(PackageType.AGENTPACKAGES, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.AGENTPLUGINS,
                new PackageArchiveConfig(PackageType.AGENTPLUGINS, true));
        PACKAGES_ARCHIVING_SUPPORTED.put(PackageType.LUAROCKS, new PackageArchiveConfig(PackageType.LUAROCKS, true));
    }

    private static final Set<PackageArchiveConfig> PACKAGES_TYPES_SUPPORTED =
            new HashSet<>(PACKAGES_ARCHIVING_SUPPORTED.values());

    public PackageArchiveServiceImpl(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public boolean isPackageArchivingSupported(@NonNull PackageType packageType) {
        return PACKAGES_ARCHIVING_SUPPORTED.get(packageType) != null;
    }

    @Override
    public Collection<PackageArchiveConfig> getAllSupportedPackages() {
        return PACKAGES_TYPES_SUPPORTED;
    }

    @Override
    public List<PackageArchive> getPackageToArchive(@NonNull PackageArchiveQuery query)
            throws PackageException {
        PackageArchivesResult packageArchivesResult = getPackageToArchiveResult(query);
        if (packageArchivesResult == null) {
            return List.of();
        }
        return packageArchivesResult.packageArchives();
    }

    /**
     * Returns the package archive results with a token for the next page access
     * according to the parameters in the packageArchiveQuery.
     *
     * @param query - package archive query
     * @throws PackageException - if the package type is not supported
     */
    @Override
    public PackageArchivesResult getPackageToArchiveResult(@NonNull PackageArchiveQuery query) throws PackageException {
        if (isPackageArchivingSupported(query.packageType())) {
            long start = System.currentTimeMillis();
            PackageArchivesResult packageArchivesResult = getPackageToArchiveFromAddon(query);
            log.debug("getPackageToArchiveResult for query {} took {} ms", query, System.currentTimeMillis() - start);
            return packageArchivesResult;
        }
        throw new PackageNotFoundException(String.format(PACKAGE_NOT_SUPPORTED_ERROR, query.packageType()));
    }

    @Override
    public int getPackagesCountToArchive(@NonNull PackageArchiveQuery query) throws PackageException {
        if (isPackageArchivingSupported(query.packageType())) {
            return getPackagesCountToArchiveFromAddon(query);
        }
        throw new PackageNotFoundException(String.format(PACKAGE_NOT_SUPPORTED_ERROR, query.packageType()));
    }

    @Override
    public boolean validateQuery(PackageArchiveQuery query) throws PackageException {
        if (isPackageArchivingSupported(query.packageType())) {
            return addonsManager.addonByType(PackagesArchiveAddon.class).validateQuery(query);
        }
        throw new PackageNotFoundException(String.format(PACKAGE_NOT_SUPPORTED_ERROR, query.packageType()));
    }

    @Override
    @NonNull
    public PackageArchive getPackageVersion(@NonNull String packageType, @NonNull String packageRepoName,
            @NonNull String packageName, @NonNull String packageVersion) throws PackageException {

        PackageType pkgType;
        try {
            pkgType = PackageType.valueOf(packageType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new PackageNotFoundException("Package type " + packageType + " is not supported");
        }

        PackageArchiveQuery query =
                new PackageArchiveQuery(packageRepoName, pkgType, 0, 0, 0, packageName, packageVersion, 1);

        if (!isPackageArchivingSupported(query.packageType())) {
            throw new PackageUnsupportedOperationException(
                    String.format(PACKAGE_NOT_SUPPORTED_ERROR, query.packageType()));
        }

        PackageArchivesResult packageArchivesResult =
                addonsManager.addonByType(PackagesArchiveAddon.class).getPackagesToArchive(query);
        if (packageArchivesResult == null || CollectionUtils.isNullOrEmpty(packageArchivesResult.packageArchives())) {
            throw new PackageNotFoundException();
        }

        return packageArchivesResult.packageArchives().getFirst();
    }

    private PackageArchivesResult getPackageToArchiveFromAddon(PackageArchiveQuery query) throws PackageException {
        return addonsManager.addonByType(PackagesArchiveAddon.class).getPackagesToArchive(query);
    }

    private int getPackagesCountToArchiveFromAddon(PackageArchiveQuery query) throws PackageException {
        return addonsManager.addonByType(PackagesArchiveAddon.class).getPackagesCountToArchive(query);
    }
}