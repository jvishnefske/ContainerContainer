package org.artifactory.ph;

import com.google.common.annotations.VisibleForTesting;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageRuntimeException;
import com.jfrog.ph.acl.service.PackageSpecificationAdapter;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.repo.PackageType;
import org.artifactory.common.repo.RepoInfo;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.event.EventHintType;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.cache.expirable.IndexChecker;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

import static org.artifactory.descriptor.repo.RepoType.Conan;

import static org.artifactory.descriptor.repo.RepoType.Alpine;
import static org.artifactory.descriptor.repo.RepoType.BazelModules;
import static org.artifactory.descriptor.repo.RepoType.CRAN;
import static org.artifactory.descriptor.repo.RepoType.Cargo;
import static org.artifactory.descriptor.repo.RepoType.CocoaPods;
import static org.artifactory.descriptor.repo.RepoType.Debian;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.descriptor.repo.RepoType.GitLfs;
import static org.artifactory.descriptor.repo.RepoType.Gradle;
import static org.artifactory.descriptor.repo.RepoType.HelmOCI;
import static org.artifactory.descriptor.repo.RepoType.Hex;
import static org.artifactory.descriptor.repo.RepoType.Ivy;
import static org.artifactory.descriptor.repo.RepoType.JetBrainsPlugins;
import static org.artifactory.descriptor.repo.RepoType.Maven;
import static org.artifactory.descriptor.repo.RepoType.NimModel;
import static org.artifactory.descriptor.repo.RepoType.Npm;
import static org.artifactory.descriptor.repo.RepoType.NuGet;
import static org.artifactory.descriptor.repo.RepoType.OCI;
import static org.artifactory.descriptor.repo.RepoType.Opkg;
import static org.artifactory.descriptor.repo.RepoType.P2;
import static org.artifactory.descriptor.repo.RepoType.Pypi;
import static org.artifactory.descriptor.repo.RepoType.SBT;
import static org.artifactory.descriptor.repo.RepoType.Terraform;
import static org.artifactory.descriptor.repo.RepoType.Vagrant;
import static org.artifactory.descriptor.repo.RepoType.YUM;
import static org.artifactory.ph.PackageRepoTypeMapper.packageType;
import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;

@Slf4j
public class PackageSpecificationImpl extends IndexChecker implements PackageSpecification {
    private final PackageSpecificationAdapter packageSpecificationAdapter;

    @VisibleForTesting
    static final EnumSet<RepoType> PATH_REINDEX_SUPPORTED = EnumSet.of(Conan);

    @VisibleForTesting
    static final EnumMap<RepoType, String> SPECIAL_SMART_URL_PREFIXES = new EnumMap<>(RepoType.class);

    // OCI and HelmOCI are excluded because customers use /api/docker prefix in some cases
    @VisibleForTesting
    static final EnumSet<RepoType> PACKAGES_LACKING_SMART_URL_PREFIX =
            EnumSet.of(Maven, Debian, YUM, Ivy, SBT, Gradle, P2, CRAN, Alpine, Opkg, Hex, Pypi, Generic, Cargo, OCI,
                    HelmOCI, Vagrant, GitLfs, NimModel, Terraform, BazelModules, JetBrainsPlugins);

    static {
        SPECIAL_SMART_URL_PREFIXES.put(CocoaPods, "pods");
    }

    public PackageSpecificationImpl(PackageSpecificationAdapter packageSpecificationAdapter) {
        this.packageSpecificationAdapter = packageSpecificationAdapter;
    }

    @Override
    public boolean isExpirable(RepoType repoType, String repoKey, @Nonnull String path) {
        try {
            PackageType packageType = packageType(repoType);
            if (packageType == null || !packageSpecificationAdapter.isSupportedPackage(packageType)) {
                return false;
            }
            return packageSpecificationAdapter.isExpirable(repoKey, packageType, path);
        } catch (PackageException e) {
            log.error("Failed to check if artifact is expired, reason:{}", e.getInternalReason());
            throw new PackageRuntimeException(e.getStatus(), e.getPublicReason(), e.getInternalReason(), e);
        }
    }

    @Override
    public boolean isLocalGenerated(@Nonnull RepoType repoType, @Nonnull String repoKey, @Nonnull String path) {
        try {
            PackageType packageType = packageType(repoType);
            if (packageType == null || !packageSpecificationAdapter.isSupportedPackage(packageType)) {
                return false;
            }
            return packageSpecificationAdapter.shouldExcludeFromReplication(packageType, path);
        } catch (PackageException e) {
            log.error("Failed to check if artifact is locally generated, reason:{}", e.getInternalReason());
            throw new PackageRuntimeException(e.getStatus(), e.getPublicReason(), e.getInternalReason(), e);
        }
    }

    @Override
    public Set<String> excludePropertyValueFromMirroring(@Nonnull String propertyKey,
            @Nonnull Set<String> propValues, RepoType repoType) {
        try {
            PackageType packageType = packageType(repoType);
            if (!shouldExcludePropertyValueFromMirroring(packageType)) {
                return propValues;
            }
            return packageSpecificationAdapter.excludePropertyValueFromMirroring(propertyKey, propValues,
                    packageType);
        } catch (PackageException e) {
            log.error("Failed to check if property should be excluded from mirroring, reason:{}",
                    e.getInternalReason());
            throw new PackageRuntimeException(e.getStatus(), e.getPublicReason(), e.getInternalReason(), e);
        }
    }

    private boolean shouldExcludePropertyValueFromMirroring(PackageType packageType) throws PackageException {
        return packageType != null && packageSpecificationAdapter.isSupportedPackage(packageType) &&
                EventLogHintManager.getHints() != null &&
                !EventLogHintManager.contains(EventHintType.ReplicationAllowed);
    }

    @Override
    public boolean shouldExcludePropertyFromMirroring(@Nonnull RepoType repoType, @Nonnull String propertyKey) {
        try {
            PackageType packageType = packageType(repoType);
            if (packageType == null || !packageSpecificationAdapter.isSupportedPackage(packageType)) {
                return false;
            }
            return packageSpecificationAdapter.isLocallyGeneratedProperty(packageType, propertyKey);
        } catch (PackageException e) {
            log.error("Failed to check if property should be excluded from mirroring, reason:{}",
                    e.getInternalReason());
            throw new PackageRuntimeException(e.getStatus(), e.getPublicReason(), e.getInternalReason(), e);
        }
    }

    @Override
    public boolean isPackageArtifact(@Nonnull String repoKey, @Nonnull String path) {
        try {
            if (!packageSpecificationAdapter.isSupportedPackage(repoKey)) {
                return false;
            }
            return packageSpecificationAdapter.isPackageArtifact(repoKey, path);
        } catch (PackageException e) {
            log.error("Failed to check if artifact is package artifact, reason:{}", e.getInternalReason());
            throw new PackageRuntimeException(e.getStatus(), e.getPublicReason(), e.getInternalReason(), e);
        }
    }

    @Override
    public boolean hasReindex(org.artifactory.common.repo.RepoType repoType, RepoType packageType) {
        return packageSpecificationAdapter.hasReindex(getRepoInfo(repoType, packageType));
    }

    @Override
    public boolean hasPathReindex(RepoType packageType) {
        return packageType != null && PATH_REINDEX_SUPPORTED.contains(packageType);
    }

    @Override
    public boolean isViewable(@Nonnull String repoKey, @Nonnull String path, RepoType repoPkgType) {
        return switch (repoPkgType) {
            // special cases, remove once package is moved to the PH
            case Cargo -> path.startsWith("index/") && !path.endsWith("/");
            case Debian -> path.endsWith("/Packages") || path.endsWith("/Release");
            case CocoaPods -> path.endsWith(".podspec");
            default -> packageSpecificationAdapter.isViewable(repoKey, path);
        };
    }

    @Override
    public boolean isPackageArtifact(@Nonnull String repoKey, @Nonnull String path, @Nonnull RepoType repoType) {
        if (packageSpecificationAdapter.isSupportedPackage(repoKey)) {
            return isPackageArtifact(repoKey, path);
        }
        if (NuGet.equals(repoType) && path.endsWith(".nupkg")) {
            return true;
        }
        return Npm.equals(repoType) && path.endsWith(".tgz");
    }

    @Override
    public boolean isVirtualCanContainRepoWithType(RepoType virtualRepoType, RepoType aggregatedRepoType) {
        if (Generic == virtualRepoType) {
            //generic can aggregate any type
            return true;
        }
        if (virtualRepoType.isMavenGroup()) {
            //maven group can aggregate any maven group
            return aggregatedRepoType.isMavenGroup();
        }
        if (OCI == virtualRepoType) {
            //oci virtual repository can aggregate any member of oci group
            return aggregatedRepoType == OCI || aggregatedRepoType == HelmOCI;
        }
        return virtualRepoType == aggregatedRepoType;
    }

    @Override
    @Nullable
    public Boolean hasMigrationAction(@Nonnull RepoConfig repoConfig) {
        if (repoConfig.getPackageType().isHuggingFace() &&
                ConstantValues.huggingFaceMLEnableFrogMLLayout.getBoolean()) {
            String isMlRepoLayout = repoConfig.getPackageTypeConfig().get(IS_ML_REPO_LAYOUT);
            return !"true".equals(isMlRepoLayout);
        }
        return null;
    }

    @Override
    public String getPrefixForSmartUrl(RepoType repoType) {
        if (repoType == null || PACKAGES_LACKING_SMART_URL_PREFIX.contains(repoType)) {
            return null;
        }
        if (SPECIAL_SMART_URL_PREFIXES.containsKey(repoType)) {
            return SPECIAL_SMART_URL_PREFIXES.get(repoType);
        }
        return repoType.getType();
    }

    private RepoInfo getRepoInfo(org.artifactory.common.repo.RepoType repoType, RepoType packageType) {
        PackageType packageT = PackageRepoTypeMapper.packageType(packageType);
        return switch (repoType) {
            case REMOTE -> new RepoInfo(org.artifactory.common.repo.RepoType.REMOTE, packageT);
            case VIRTUAL -> new RepoInfo(org.artifactory.common.repo.RepoType.VIRTUAL, packageT);
            case LOCAL -> new RepoInfo(org.artifactory.common.repo.RepoType.LOCAL, packageT);
        };
    }

    @Override
    public RepoType[] getTypes() {
        // will always be evaluated
        return RepoType.values();
    }
}