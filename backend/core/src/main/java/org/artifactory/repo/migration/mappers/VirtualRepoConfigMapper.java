package org.artifactory.repo.migration.mappers;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.RepositoryConfigSource;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.model.VirtualRepositoryRepoTypeConfig;
import org.artifactory.repo.virtual.VirtualCacheConfig;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Long.parseLong;
import static java.lang.String.valueOf;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.buildBaseConfig;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.buildSecurityConfig;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.customValueOf;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.setFederationMembers;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.setCustomOrGlobalNugetNormalization;
import static org.artifactory.util.PackageTypeConfigFields.FORCE_NUGET_AUTHENTICATION;

public class VirtualRepoConfigMapper {

    private VirtualRepoConfigMapper() {
    }

    public static VirtualRepoConfig mapVirtualRepoConfig(VirtualRepoConfig virtualRepoConfig,
            Map<String, Object> newValues, CentralConfigDescriptor descriptor,
            RepositoryConfigService repositoryConfigService) {
        buildBaseConfig(virtualRepoConfig.getBaseConfig(), newValues, descriptor);
        buildRepoTypeConfig(virtualRepoConfig.getRepoTypeConfig(), newValues);
        buildPackageTypeConfig(virtualRepoConfig.getPackageTypeConfig(), virtualRepoConfig.getPackageType(),
                newValues, descriptor);
        buildSecurityConfig(virtualRepoConfig.getSecurityConfig(), newValues, descriptor);
        buildFederationConfig(virtualRepoConfig, newValues);
        return virtualRepoConfig;
    }

    private static void buildFederationConfig(VirtualRepoConfig virtualRepoConfig, Map<String, Object> newValues) {
        if (!ConstantValues.virtualRepoFederationEnabled.getBoolean()) {
            return;
        }
        setFederationMembers(virtualRepoConfig, newValues);
    }

    private static void buildRepoTypeConfig(VirtualRepositoryRepoTypeConfig virtualRepositoryRepoTypeConfig,
            Map<?, ?> newValues) {
        for (Map.Entry<?, ?> newValue : newValues.entrySet()) {
            try {
                if ("repositories".equals(newValue.getKey())) {
                    if (newValue.getValue() != null) {
                        LinkedHashSet<String> orderedRepoKeys = new LinkedHashSet<>();
                        Map<String, Object> repositories = (Map<String, Object>) newValue.getValue();
                        // Iterate through the map's values directly, as the keys "{0}", "{1}", etc.,
                        for (int i = 0; i < repositories.size(); i++) {
                            orderedRepoKeys.add(repositories.get("{" + i + "}").toString());
                        }
                        virtualRepositoryRepoTypeConfig.setRepositoryRefs(
                                orderedRepoKeys.stream().toList());
                    } else {
                        virtualRepositoryRepoTypeConfig.setRepositoryRefs(Collections.emptyList());
                    }
                } else if ("defaultDeploymentRepo".equals(newValue.getKey())) {
                    virtualRepositoryRepoTypeConfig.setDefaultDeploymentRepoRef(customValueOf(newValue.getValue()));
                } else if ("artifactoryRequestsCanRetrieveRemoteArtifacts".equals(newValue.getKey())) {
                    virtualRepositoryRepoTypeConfig.setArtifactoryRequestsCanRetrieveRemoteArtifacts(
                            parseBoolean(valueOf(newValue.getValue())));
                }
                if ("virtualCacheConfig".equals(newValue.getKey())) {
                    Map<?, ?> virtualCacheConfigNewValues = (Map<?, ?>) newValue.getValue();
                    for (Map.Entry<?, ?> virtualCacheConfigNewValue :
                            virtualCacheConfigNewValues.entrySet()) {
                        if ("virtualRetrievalCachePeriodSecs".equals(virtualCacheConfigNewValue.getKey())) {
                            VirtualCacheConfig virtualCacheConfig = new VirtualCacheConfig();
                            virtualCacheConfig.setVirtualRetrievalCachePeriodSecs(
                                    parseLong(valueOf(virtualCacheConfigNewValue.getValue())));
                            virtualRepositoryRepoTypeConfig.setVirtualCacheConfig(virtualCacheConfig);
                        }
                    }
                }
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("No values under " + newValue.getKey());
            }
        }
    }

    private static void buildPackageTypeConfig(Map<String, String> packageTypeConfig,
            RepoType repoType, Map<String, Object> newValues, CentralConfigDescriptor descriptor) {
        for (Map.Entry<String, Object> newValue : newValues.entrySet()) {
            try {
                switch (repoType) {
                    case Bower, Npm, Go ->
                            RepoConfigMigrationPatchUtils.externalDependenciesToConfig(packageTypeConfig, newValues);
                    case Docker, OCI, HelmOCI -> buildDockerPackageConfig(packageTypeConfig, newValue);
                    case Maven, Ivy, Gradle, P2, SBT -> buildMavenPackageConfig(packageTypeConfig, repoType, newValue);
                    case NuGet -> buildNugetTypeSpecificConfig(packageTypeConfig, newValue, descriptor);
                    case Helm -> {
                        if ("helm".equals(newValue.getKey())) {
                            Map<?, ?> helmValues = (Map<?, ?>) newValue.getValue();
                            for (Map.Entry<?, ?> helmValue : helmValues.entrySet()) {
                                packageTypeConfig.put("useNamespaces", valueOf(helmValue.getValue()));
                            }
                        }
                    }
                    default -> {return;}
                }
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("No values under " + newValue.getKey());
            }
        }
    }

    private static void buildNugetTypeSpecificConfig(Map<String, String> packageTypeConfig,
            Map.Entry<String, Object> newValue, CentralConfigDescriptor descriptor) {
        if (FORCE_NUGET_AUTHENTICATION.getKey().equals(newValue.getKey())) {
            packageTypeConfig.put("forceNugetAuthentication", customValueOf(newValue.getValue()));
        }
        setCustomOrGlobalNugetNormalization(packageTypeConfig, newValue,
                descriptor.getRepositoriesGeneralConfig());
    }

    private static void buildMavenPackageConfig(Map<String, String> packageTypeConfig, RepoType repoType,
            Map.Entry<String, Object> newValue) {
        if ("pomRepositoryReferencesCleanupPolicy".equals(newValue.getKey())) {
            packageTypeConfig.put("pomRepositoryReferencesCleanupPolicy", customValueOf(newValue.getValue()));
        } else if ("forceMavenAuthentication".equals(newValue.getKey())) {
            packageTypeConfig.put("forceMavenAuthentication", customValueOf(newValue.getValue()));
        } else if (RepoType.P2.equals(repoType) && "p2".equals(newValue.getKey())) {
            Map<?, ?> p2Values = (Map<?, ?>) newValue.getValue();
            Map<?, ?> urlsValues = (Map<?, ?>) p2Values.get("urls");
            List<String> urls = urlsValues.values().stream().map(String.class::cast).toList();
            RepositoryConfigSource.entityToFlatMapKeys(urls, "urls", packageTypeConfig);
            if ("forceP2Authentication".equals(newValue.getKey())) {
                packageTypeConfig.put("forceP2Authentication", customValueOf(newValue.getValue()));
            }
        }
    }

    private static void buildDockerPackageConfig(Map<String, String> packageTypeConfig,
            Map.Entry<String, Object> newValue) {
        if ("dockerApiVersion".equals(newValue.getKey())) {
            packageTypeConfig.put("dockerApiVersion", customValueOf(newValue.getValue()));
        } else if ("resolveDockerTagsByTimestamp".equals(newValue.getKey())) {
            packageTypeConfig.put("resolveDockerTagsByTimestamp", customValueOf(newValue.getValue()));
        } else if ("cachingLocalForeignLayersEnabled".equals(newValue.getKey())) {
            packageTypeConfig.put("cachingLocalForeignLayersEnabled", customValueOf(newValue.getValue()));
        }
    }
}
