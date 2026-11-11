package org.artifactory.repo.migration.mappers;

import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.repo.RepoDetailsType;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.artifactory.descriptor.repo.RepositoryConfigSource.entityToFlatMapKeys;

public class VirtualRepositoryDescriptorMapper implements RepoConfigMapper<VirtualRepoDescriptor> {

    @Override
    public Map<String, Object> map(VirtualRepoDescriptor descriptor) {
        Map<String, Object> repositoryConfig = new HashMap<>();
        repositoryConfig.put("key", descriptor.getKey());
        repositoryConfig.put("packageType", descriptor.getType().getType());
        repositoryConfig.put("type", "virtual");
        repositoryConfig.put("baseConfig", RepoConfigMigrationMapperUtils.buildBaseConfig(descriptor));
        repositoryConfig.put("packageTypeConfig", new HashMap<>(buildPackageTypeConfig(descriptor)));
        repositoryConfig.put("repoTypeConfig", buildRepoTypeConfig(descriptor));
        repositoryConfig.put("securityConfig", buildSecurityConfig(descriptor));
        return repositoryConfig;
    }

    @Override
    public RepoDetailsType getRepoType() {
        return RepoDetailsType.VIRTUAL;
    }

    private Map<String, String> buildPackageTypeConfig(VirtualRepoDescriptor descriptor) {
        Map<String, String> packageTypeConfig = new HashMap<>();
        if (RepoType.Helm.equals(descriptor.getType())) {
            packageTypeConfig.put("useNamespaces", String.valueOf(descriptor.isUseNamespaces()));
        } else if (RepoType.Bower.equals(descriptor.getType())) {
            RepoConfigMigrationMapperUtils.externalDependenciesToConfig(packageTypeConfig,
                    descriptor.getExternalDependencies());
        } else if (RepoType.Npm.equals(descriptor.getType())) {
            RepoConfigMigrationMapperUtils.externalDependenciesToConfig(packageTypeConfig,
                    descriptor.getExternalDependencies());
        } else if (RepoType.Cargo.equals(descriptor.getType())) {
            if (descriptor.getCargoConfig() != null) {
                packageTypeConfig
                        .put("cargoAnonymousAccess",
                                String.valueOf(descriptor.getCargoConfig().isCargoAnonymousAccess()));
            }
        } else if (RepoType.Debian.equals(descriptor.getType())) {
            packageTypeConfig.put("ddebSupported", String.valueOf(descriptor.isDdebSupported()));
            entityToFlatMapKeys(descriptor.getDebianOptionalIndexCompressionFormats(),
                    "debianOptionalIndexCompressionFormats", packageTypeConfig);
            packageTypeConfig.put("debianDefaultArchitectures",
                    String.valueOf(descriptor.getDebianDefaultArchitectures()));
        } else if (descriptor.getType().isDockerGroup()) {
            packageTypeConfig.put("dockerApiVersion", String.valueOf(descriptor.getDockerApiVersion()));
            packageTypeConfig.put("resolveDockerTagsByTimestamp",
                    String.valueOf(descriptor.isResolveDockerTagsByTimestamp()));
            packageTypeConfig.put("cachingLocalForeignLayersEnabled",
                    String.valueOf(descriptor.isCachingLocalForeignLayersEnabled()));
        } else if (descriptor.getType().isMavenGroup()) {
            packageTypeConfig.put("pomRepositoryReferencesCleanupPolicy",
                    String.valueOf(descriptor.getPomRepositoryReferencesCleanupPolicy()));
            packageTypeConfig.put("forceMavenAuthentication",
                    String.valueOf(descriptor.isForceMavenAuthentication()));
            if (RepoType.P2.equals(descriptor.getType()) && descriptor.getP2() != null &&
                    descriptor.getP2().getUrls() != null) {
                entityToFlatMapKeys(descriptor.getP2().getUrls(), "urls", packageTypeConfig);
                packageTypeConfig.put("forceP2Authentication",
                        String.valueOf(descriptor.isForceP2Authentication()));
            }
        } else if (RepoType.NuGet.equals(descriptor.getType())) {
            packageTypeConfig.put("forceNugetAuthentication",
                    String.valueOf(descriptor.isForceNugetAuthentication()));
        } else if (RepoType.Go.equals(descriptor.getType())) {
            RepoConfigMigrationMapperUtils.externalDependenciesToConfig(packageTypeConfig,
                    descriptor.getExternalDependencies());
        } else if (RepoType.Conan.equals(descriptor.getType())) {
            packageTypeConfig.put("forceConanAuthentication",
                    String.valueOf(descriptor.isForceConanAuthentication()));
        }
        return packageTypeConfig;
    }

    private Map<String, Object> buildRepoTypeConfig(VirtualRepoDescriptor descriptor) {
        Map<String, Object> virtualConfig = new HashMap<>();
        virtualConfig.put("repositoryRefs",
                descriptor.getRepositories().stream().map(RepoDescriptor::getKey).collect(Collectors.toList()));
        if (descriptor.getDefaultDeploymentRepo() != null) {
            virtualConfig.put("defaultDeploymentRepoRef", descriptor.getDefaultDeploymentRepo().getKey());
        }
        virtualConfig.put("artifactoryRequestsCanRetrieveRemoteArtifacts",
                descriptor.isArtifactoryRequestsCanRetrieveRemoteArtifacts());
        if (descriptor.getVirtualCacheConfig() != null) {
            Map<String, Object> virtualCacheConfigNew = new HashMap<>();
            virtualCacheConfigNew.put("virtualRetrievalCachePeriodSecs",
                    descriptor.getVirtualCacheConfig().getVirtualRetrievalCachePeriodSecs());
            virtualConfig.put("virtualCacheConfig", virtualCacheConfigNew);
        }
        return virtualConfig;
    }

    private Map<String, Object> buildSecurityConfig(VirtualRepoDescriptor descriptor) {
        Map<String, Object> securityConfig = new HashMap<>();
        if (descriptor.getPrimaryKeyPairDescriptor() != null) {
            securityConfig.put("primaryKeyPairRef", descriptor.getPrimaryKeyPairDescriptor().getKey());
        }
        if (descriptor.getSecondaryKeyPairDescriptor() != null) {
            securityConfig.put("secondaryKeyPairRef", descriptor.getSecondaryKeyPairDescriptor().getKey());
        }
        securityConfig.put("signedUrlTtl", descriptor.getSignedUrlTtl());
        securityConfig.put("hideUnauthorizedResources", descriptor.isHideUnauthorizedResources());
        securityConfig.put("keyPair", descriptor.getKeyPair());
        return securityConfig;
    }


}