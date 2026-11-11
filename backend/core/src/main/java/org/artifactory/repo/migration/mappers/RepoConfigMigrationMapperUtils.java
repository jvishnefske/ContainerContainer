package org.artifactory.repo.migration.mappers;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.artifactory.descriptor.repo.ExternalDependenciesConfig;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RealRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.XrayRepoConfigDescriptor;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;

import java.util.HashMap;
import java.util.Map;

import static org.artifactory.descriptor.repo.RepoType.fromType;
import static org.artifactory.descriptor.repo.RepositoryConfigSource.entityToFlatMapKeys;
import static org.artifactory.util.PackageTypeConfigFields.ENCRYPT_STATES;

@UtilityClass
public class RepoConfigMigrationMapperUtils {

    @Nonnull
    public Map<String, Object> buildBaseConfig(@Nonnull RepoDescriptor descriptor) {
        Map<String, Object> baseConfig = new HashMap<>();
        baseConfig.put("description", descriptor.getDescription());
        baseConfig.put("notes", descriptor.getNotes());

        if (descriptor.getRepoLayout() != null) {
            baseConfig.put("repoLayoutRef", descriptor.getRepoLayout().getName());
        }
        baseConfig.put("includesPattern", descriptor.getIncludesPattern());
        baseConfig.put("excludesPattern", descriptor.getExcludesPattern());
        return baseConfig;
    }

    @Nonnull
    public Map<String, Object> buildSecurityConfig(@Nonnull RealRepoDescriptor descriptor) {
        Map<String, Object> securityConfig = new HashMap<>();
        if (descriptor.getXrayConfig() != null) {
            Map<String, Object> xrayConfig = new HashMap<>();
            XrayRepoConfigDescriptor xrayConfigDescriptor = descriptor.getXrayConfig();
            xrayConfig.put("dataTtl", xrayConfigDescriptor.getDataTtl());
            xrayConfig.put("enabled", xrayConfigDescriptor.isEnabled());
            xrayConfig.put("xrayNotAvailableMode", xrayConfigDescriptor.getXrayNotAvailableMode());
            securityConfig.put("xrayConfig", xrayConfig);
        }
        if (descriptor.getPrimaryKeyPairDescriptor() != null) {
            securityConfig.put("primaryKeyPairRef", descriptor.getPrimaryKeyPairDescriptor().getKey());
        }
        if (descriptor.getSecondaryKeyPairDescriptor() != null) {
            securityConfig.put("secondaryKeyPairRef", descriptor.getSecondaryKeyPairDescriptor().getKey());
        }
        securityConfig.put("signedUrlTtl", descriptor.getSignedUrlTtl());
        return securityConfig;
    }


    public void externalDependenciesToConfig(@Nonnull Map<String, String> packageTypeConfig,
            @Nullable ExternalDependenciesConfig externalDependencies) {
        if (externalDependencies != null) {
            packageTypeConfig.put(
                    "externalDependenciesEnabled",
                    String.valueOf(externalDependencies.isEnabled()));
            entityToFlatMapKeys(externalDependencies.getPatterns(),
                    "externalDependenciesPatterns",
                    packageTypeConfig);
            if (externalDependencies.getRemoteRepo() != null) {
                packageTypeConfig
                        .put("externalDependenciesRemoteRepoRef",
                                externalDependencies.getRemoteRepo().getKey());
            }
        } else {
            packageTypeConfig.remove("externalDependenciesEnabled");
            packageTypeConfig.remove("externalDependenciesPatterns");
            packageTypeConfig.remove("externalDependenciesRemoteRepoRef");
        }
    }

    public static <E extends RepoDescriptor> DbRepositoryConfig createDbRepoConfig(E descriptor,
            RepoDetailsType repoDetailsType, byte[] infoBlobFields, long timestamp) {
        DbRepositoryConfig dbRepositoryConfig = new DbRepositoryConfig();
        dbRepositoryConfig.setKey(descriptor.getKey());
        dbRepositoryConfig.setCreated(timestamp);
        dbRepositoryConfig.setModified(timestamp);
        dbRepositoryConfig.setRevision(1);
        dbRepositoryConfig.setType(repoDetailsType.typeNameLowercase());
        dbRepositoryConfig.setPackageType(descriptor.getType().getType());
        dbRepositoryConfig.setRepositoryInfoBlob(infoBlobFields);
        return dbRepositoryConfig;
    }

    @Nonnull
    public static Map<String, String> buildLocalPackageTypeConfig(LocalRepoDescriptor descriptor) {
        Map<String, String> packageTypeConfig = new HashMap<>();
        if (descriptor.getType().isMavenGroup()) {
            packageTypeConfig
                    .put("suppressPomConsistencyChecks",
                            String.valueOf(descriptor.isSuppressPomConsistencyChecks()));
            if (RepoType.P2.equals(descriptor.getType())) {
                packageTypeConfig.put("forceP2Authentication", String.valueOf(descriptor.isForceP2Authentication()));
            }
        } else if (RepoType.Cargo.equals(descriptor.getType())) {
            packageTypeConfig.put("cargoAnonymousAccess", String.valueOf(descriptor.isCargoAnonymousAccess()));
            packageTypeConfig.put("cargoInternalIndex", String.valueOf(descriptor.isCargoInternalIndex()));
        } else if (RepoType.Composer.equals(descriptor.getType())) {
            packageTypeConfig.put("enableComposerV1Indexing",
                    String.valueOf(descriptor.isEnableComposerV1Indexing()));
        } else if (RepoType.Debian.equals(descriptor.getType())) {
            packageTypeConfig.put("debianTrivialLayout", String.valueOf(descriptor.isDebianTrivialLayout()));
            packageTypeConfig.put("ddebSupported", String.valueOf(descriptor.isDdebSupported()));
            entityToFlatMapKeys(descriptor.getOptionalIndexCompressionFormats(), "optionalIndexCompressionFormats",
                    packageTypeConfig);
        } else if (descriptor.getType().isDockerGroup()) {
            packageTypeConfig.put("dockerTagRetention", String.valueOf(descriptor.getDockerTagRetention()));
            packageTypeConfig.put("dockerApiVersion", descriptor.getDockerApiVersion().toString());
            packageTypeConfig.put("blockPushingSchema1", String.valueOf(descriptor.isBlockPushingSchema1()));
            packageTypeConfig.put("maxUniqueTags", String.valueOf(descriptor.getMaxUniqueTags()));
        } else if (RepoType.NuGet.equals(descriptor.getType())) {
            packageTypeConfig.put("forceNugetAuthentication",
                    String.valueOf(descriptor.isForceNugetAuthentication()));
        } else if (RepoType.YUM.equals(fromType(descriptor.getType().getType()))) {
            packageTypeConfig.put("calculateYumMetadata", String.valueOf(descriptor.isCalculateYumMetadata()));
            packageTypeConfig.put("yumRootDepth", String.valueOf(descriptor.getYumRootDepth()));
            packageTypeConfig.put("enableFileListsIndexing", String.valueOf(descriptor.isEnableFileListsIndexing()));
            if (descriptor.getYumGroupFileNames() != null) {
                packageTypeConfig.put("yumGroupFileNames", String.valueOf(descriptor.getYumGroupFileNames()));
            }
        } else if (RepoType.Terraform.equals(descriptor.getType())) {
            packageTypeConfig.put("terraformType", descriptor.getTerraformType());
        } else if (RepoType.Conan.equals(descriptor.getType())) {
            packageTypeConfig.put("forceConanAuthentication",
                    String.valueOf(descriptor.isForceConanAuthentication()));
        } else if (RepoType.TerraformBackend.equals(descriptor.getType())) {
            packageTypeConfig.put(ENCRYPT_STATES.getKey(), String.valueOf(descriptor.isEncryptStates()));
        }
        return packageTypeConfig;
    }
}