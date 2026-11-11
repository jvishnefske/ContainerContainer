package org.artifactory.repo.migration.mappers;

import org.artifactory.descriptor.repo.CocoaPodsConfiguration;
import org.artifactory.descriptor.repo.HelmConfiguration;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VcsGitConfiguration;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.util.PackageTypeConfigFields;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.util.PackageTypeConfigFields.SYMBOL_SERVER_URL;

public class RemoteRepositoryDescriptorMapper implements RepoConfigMapper<RemoteRepoDescriptor> {

    @Override
    public Map<String, Object> map(RemoteRepoDescriptor descriptor) {
        Map<String, Object> repositoryConfig = new HashMap<>();
        repositoryConfig.put("key", descriptor.getKey());
        repositoryConfig.put("packageType", descriptor.getType().getType());
        repositoryConfig.put("type", "remote");
        repositoryConfig.put("baseConfig", RepoConfigMigrationMapperUtils.buildBaseConfig(descriptor));
        repositoryConfig.put("packageTypeConfig",
                new HashMap<>(buildPackageTypeConfig((HttpRepoDescriptor) descriptor)));
        repositoryConfig.put("repoTypeConfig", buildRepoTypeConfig((HttpRepoDescriptor) descriptor));
        repositoryConfig.put("securityConfig", RepoConfigMigrationMapperUtils.buildSecurityConfig(descriptor));
        return repositoryConfig;
    }

    @Override
    public RepoDetailsType getRepoType() {
        return RepoDetailsType.REMOTE;
    }

    private Map<String, String> buildPackageTypeConfig(HttpRepoDescriptor descriptor) {
        Map<String, String> packageTypeConfig = new HashMap<>();
        if (descriptor.getType().isMavenGroup()) {
            packageTypeConfig.put("suppressPomConsistencyChecks",
                    String.valueOf(descriptor.isSuppressPomConsistencyChecks()));
            packageTypeConfig.put("fetchJarsEagerly", String.valueOf(descriptor.isFetchJarsEagerly()));
            packageTypeConfig.put("fetchSourcesEagerly", String.valueOf(descriptor.isFetchSourcesEagerly()));
            packageTypeConfig.put("rejectInvalidJars", String.valueOf(descriptor.isRejectInvalidJars()));
            if (RepoType.P2.equals(descriptor.getType()) && descriptor.getP2OriginalUrl() != null) {
                packageTypeConfig.put("p2OriginalUrl", String.valueOf(descriptor.getP2OriginalUrl()));
                packageTypeConfig.put("forceP2Authentication", String.valueOf(descriptor.isForceP2Authentication()));
            }
        } else if (RepoType.Debian.equals(descriptor.getType())) {
            packageTypeConfig.put("ddebSupported", String.valueOf(descriptor.isDdebSupported()));
        } else if (RepoType.Cargo.equals(descriptor.getType())) {
            packageTypeConfig.put("cargoAnonymousAccess", String.valueOf(descriptor.isCargoAnonymousAccess()));
            packageTypeConfig.put("cargoInternalIndex", String.valueOf(descriptor.isCargoInternalIndex()));
            if (descriptor.getGit() != null && descriptor.getGit().getRegistryUrl() != null) {
                packageTypeConfig.put("gitRegistryUrl", descriptor.getGit().getRegistryUrl());
            }
        } else if (RepoType.Bower.equals(descriptor.getType())) {
            if (descriptor.getBower() != null) {
                packageTypeConfig.put("bowerRegistryUrl", descriptor.getBower().getBowerRegistryUrl());
            }
            if (descriptor.getVcs() != null) {
                packageTypeConfig.put("vcsType", descriptor.getVcs().getType().name());
                packageTypeConfig.put("vcsGitProvider", descriptor.getVcs().getGit().getProvider().name());
                if (descriptor.getVcs().getGit().getDownloadUrl() != null) {
                    packageTypeConfig.put("vcsGitDownloadUrl", descriptor.getVcs().getGit().getDownloadUrl());
                }
            }
        } else if (RepoType.CocoaPods.equals(descriptor.getType())) {
            CocoaPodsConfiguration cocoaPodsConfiguration = descriptor.getCocoaPods();
            if (cocoaPodsConfiguration != null) {
                packageTypeConfig.put("cocoaPodsSpecsRepoUrl", cocoaPodsConfiguration.getCocoaPodsSpecsRepoUrl());
                packageTypeConfig.put(PackageTypeConfigFields.COCOA_PODS_CDN_URL.getKey(),
                        cocoaPodsConfiguration.getCocoaPodsCdnUrl());
                VcsGitConfiguration specRepoProvider = cocoaPodsConfiguration.getSpecRepoProvider();
                if (specRepoProvider != null) {
                    if (specRepoProvider.getDownloadUrl() != null) {
                        packageTypeConfig.put("specsRepoDownloadUrl", specRepoProvider.getDownloadUrl());
                    }
                    packageTypeConfig.put("specsRepoDownloadProvider", specRepoProvider.getProvider().name());
                }
            }
            if (descriptor.getVcs() != null) {
                packageTypeConfig.put("vcsType", descriptor.getVcs().getType().name());
                packageTypeConfig.put("vcsGitProvider", descriptor.getVcs().getGit().getProvider().name());
                if (descriptor.getVcs().getGit().getDownloadUrl() != null) {
                    packageTypeConfig.put("vcsGitDownloadUrl", descriptor.getVcs().getGit().getDownloadUrl());
                }
            }
            RepoConfigMigrationMapperUtils.externalDependenciesToConfig(packageTypeConfig,
                    descriptor.getExternalDependencies());
        } else if (RepoType.Composer.equals(descriptor.getType())) {
            if (descriptor.getComposer() != null) {
                packageTypeConfig.put("composerRegistryUrl", descriptor.getComposer().getComposerRegistryUrl());
            }
            if (descriptor.getVcs() != null) {
                packageTypeConfig.put("vcsType", descriptor.getVcs().getType().name());
                packageTypeConfig.put("vcsGitProvider", descriptor.getVcs().getGit().getProvider().name());
                if (descriptor.getVcs().getGit().getDownloadUrl() != null) {
                    packageTypeConfig.put("vcsGitDownloadUrl", descriptor.getVcs().getGit().getDownloadUrl());
                }
            }
        } else if (descriptor.getType().isDockerGroup()) {
            packageTypeConfig.put("dockerApiVersion", descriptor.getDockerApiVersion().name());
            packageTypeConfig.put("blockPushingSchema1", String.valueOf(descriptor.isBlockPushingSchema1()));
            packageTypeConfig.put("maxUniqueTags", String.valueOf(descriptor.getMaxUniqueTags()));
            packageTypeConfig.put("dockerProjectId", descriptor.getDockerProjectId());
            RepoConfigMigrationMapperUtils.externalDependenciesToConfig(packageTypeConfig,
                    descriptor.getExternalDependencies());
        } else if (RepoType.Helm.equals(descriptor.getType())) {
            HelmConfiguration helmConfig = descriptor.getHelm();
            if (helmConfig != null && helmConfig.getChartsBaseUrl() != null) {
                packageTypeConfig.put("chartsBaseUrl", String.valueOf(descriptor.getHelm().getChartsBaseUrl()));
            }
            RepoConfigMigrationMapperUtils.externalDependenciesToConfig(packageTypeConfig,
                    descriptor.getExternalDependencies());
        } else if (RepoType.NuGet.equals(descriptor.getType())) {
            if (descriptor.getNuget() != null) {
                packageTypeConfig.put("v3FeedUrl", String.valueOf(descriptor.getNuget().getV3FeedUrl()));
                packageTypeConfig.put("feedContextPath", String.valueOf(descriptor.getNuget().getFeedContextPath()));
                packageTypeConfig.put("downloadContextPath",
                        String.valueOf(descriptor.getNuget().getDownloadContextPath()));
                packageTypeConfig.put(SYMBOL_SERVER_URL.getKey(),
                        descriptor.getNuget().getSymbolServerUrl());
            }
            packageTypeConfig.put("forceNugetAuthentication",
                    String.valueOf(descriptor.isForceNugetAuthentication()));
        } else if (RepoType.Pypi.equals(descriptor.getType())) {
            // TODO [u.l] what an ugly field name
            if (descriptor.getPypi() != null) {
                packageTypeConfig.put("pyPIRegistryUrl", String.valueOf(descriptor.getPypi().getPyPIRegistryUrl()));
                if (descriptor.getPypi().getPackagesContextPath() != null) {
                    packageTypeConfig.put("packagesContextPath",
                            String.valueOf(descriptor.getPypi().getPackagesContextPath()));
                }
                packageTypeConfig.put("repositorySuffix", String.valueOf(descriptor.getPypi().getRepositorySuffix()));
                if (descriptor.getPypi().getIndexContextPath() != null) {
                    packageTypeConfig.put("indexContextPath",
                            String.valueOf(descriptor.getPypi().getIndexContextPath()));
                }
            }
        } else if (RepoType.VCS.equals(descriptor.getType())) {
            if (descriptor.getVcs() != null) {
                if (descriptor.getVcs().getGit() != null) {
                    packageTypeConfig.put("vcsGitProvider", descriptor.getVcs().getGit().getProvider().name());
                    if (descriptor.getVcs().getGit().getDownloadUrl() != null) {
                        packageTypeConfig.put("vcsGitDownloadUrl", descriptor.getVcs().getGit().getDownloadUrl());
                    }
                }
                packageTypeConfig.put("vcsType", descriptor.getVcs().getType().name());
            }
        } else if (RepoType.Terraform.equals(descriptor.getType())) {
            if (descriptor.getTerraform() != null) {
                packageTypeConfig.put("terraformProvidersUrl", descriptor.getTerraform().getTerraformProvidersUrl());
                packageTypeConfig.put("terraformRegistryUrl", descriptor.getTerraform().getTerraformRegistryUrl());
            }
        } else if (RepoType.Go.equals(descriptor.getType())) {
            if (descriptor.getVcs() != null) {
                packageTypeConfig.put("vcsType", descriptor.getVcs().getType().name());
                packageTypeConfig.put("vcsGitProvider", descriptor.getVcs().getGit().getProvider().name());
                if (descriptor.getVcs().getGit().getDownloadUrl() != null) {
                    packageTypeConfig.put("vcsGitDownloadUrl", descriptor.getVcs().getGit().getDownloadUrl());
                }
            }
            RepoConfigMigrationMapperUtils.externalDependenciesToConfig(packageTypeConfig,
                    descriptor.getExternalDependencies());
        } else if (RepoType.Conan.equals(descriptor.getType())) {
            packageTypeConfig.put("forceConanAuthentication",
                    String.valueOf(descriptor.isForceConanAuthentication()));
        } else if (RepoType.Swift.equals(descriptor.getType())) {
            RepoConfigMigrationMapperUtils.externalDependenciesToConfig(packageTypeConfig,
                    descriptor.getExternalDependencies());
        } else if (RepoType.Hex.equals(descriptor.getType())) {
            packageTypeConfig.put("hexRepositoryUrl", String.valueOf(descriptor.getHexRepositoryUrl()));
            packageTypeConfig.put("hexPublicKey", String.valueOf(descriptor.getHexPublicKey()));
        }
        return packageTypeConfig;
    }

    private Map<String, Object> buildRepoTypeConfig(HttpRepoDescriptor descriptor) {
        Map<String, Object> remoteConfig = new HashMap<>();
        remoteConfig.put("handleReleases", descriptor.isHandleReleases());
        remoteConfig.put("handleSnapshots", descriptor.isHandleSnapshots());
        remoteConfig.put("maxUniqueSnapshots", descriptor.getMaxUniqueSnapshots());
        if (descriptor.getCdnRedirectRepoConfig() != null) {
            Map<String, Object> cdnRedirectRepoConfig = new HashMap<>();
            cdnRedirectRepoConfig.put("enabled", descriptor.getCdnRedirectRepoConfig().isEnabled());
            remoteConfig.put("cdnRedirectRepoConfig", cdnRedirectRepoConfig);
        }
        if (descriptor.getDownloadRedirectConfig() != null) {
            Map<String, Object> downloadRedirectConfig = new HashMap<>();
            downloadRedirectConfig.put("enabled", descriptor.getDownloadRedirectConfig().isEnabled());
            remoteConfig.put("downloadRedirectConfig", downloadRedirectConfig);
        }
        remoteConfig.put("username", descriptor.getUsername());
        remoteConfig.put("password", descriptor.getPassword());
        remoteConfig.put("localAddress", descriptor.getLocalAddress());
        if (descriptor.getRemoteRepoLayout() != null) {
            remoteConfig.put("remoteRepoLayoutRef", descriptor.getRemoteRepoLayout().getName());
        }
        if (descriptor.getProxy() != null) {
            remoteConfig.put("proxyRef", descriptor.getProxy().getKey());
        }
        remoteConfig.put("disableProxy", descriptor.isDisableProxy());
        List<String> propertySetRefs = new ArrayList<>();
        descriptor.getPropertySets().forEach(p -> propertySetRefs.add(p.getName()));
        remoteConfig.put("propertySetRefs", propertySetRefs);
        remoteConfig.put("checksumPolicyType",
                org.artifactory.repo.ChecksumPolicyType.valueOf(descriptor.getChecksumPolicyType().name()));
        remoteConfig.put("assumedOfflinePeriodSecs", descriptor.getAssumedOfflinePeriodSecs());
        remoteConfig.put("retrievalCachePeriodSecs", descriptor.getRetrievalCachePeriodSecs());
        remoteConfig.put("missedRetrievalCachePeriodSecs", descriptor.getMissedRetrievalCachePeriodSecs());
        remoteConfig.put("metadataRetrievalTimeoutSecs", descriptor.getMetadataRetrievalTimeoutSecs());
        remoteConfig.put("socketTimeoutMillis", descriptor.getSocketTimeoutMillis());
        remoteConfig.put("unusedArtifactsCleanupPeriodHours", descriptor.getUnusedArtifactsCleanupPeriodHours());
        remoteConfig.put("clientTlsCertificate", descriptor.getClientTlsCertificate());

        if (descriptor.getContentSynchronisation() != null) {
            Map<String, Object> contentSynchronisation = new HashMap<>();
            contentSynchronisation.put("enabled", descriptor.getContentSynchronisation().isEnabled());
            if (descriptor.getContentSynchronisation().getProperties() != null) {
                Map<String, Object> properties = new HashMap<>();
                properties.put("enabled", descriptor.getContentSynchronisation().getProperties().isEnabled());
                contentSynchronisation.put("properties", properties);
            }
            if (descriptor.getContentSynchronisation().getSource() != null) {
                Map<String, Object> source = new HashMap<>();
                source.put("originAbsenceDetection",
                        descriptor.getContentSynchronisation().getSource().isOriginAbsenceDetection());
                contentSynchronisation.put("source", source);
            }
            if (descriptor.getContentSynchronisation().getStatistics() != null) {
                Map<String, Object> statistics = new HashMap<>();
                statistics.put("enabled", descriptor.getContentSynchronisation().getStatistics().isEnabled());
                contentSynchronisation.put("statistics", statistics);
            }
            remoteConfig.put("contentSynchronisation", contentSynchronisation);
        }
        remoteConfig.put("queryParams", descriptor.getQueryParams());
        remoteConfig.put("url", descriptor.getUrl());
        remoteConfig.put("mismatchingMimeTypesOverrideList", descriptor.getMismatchingMimeTypesOverrideList());
        remoteConfig.put("synchronizeProperties", descriptor.isSynchronizeProperties());
        remoteConfig.put("shareConfiguration", descriptor.isShareConfiguration());
        remoteConfig.put("storeArtifactsLocally", descriptor.isStoreArtifactsLocally());
        remoteConfig.put("hardFail", descriptor.isHardFail());
        remoteConfig.put("enableCookieManagement", descriptor.isEnableCookieManagement());
        remoteConfig.put("blackedOut", descriptor.isBlackedOut());
        remoteConfig.put("allowAnyHostAuth", descriptor.isAllowAnyHostAuth());
        remoteConfig.put("archiveBrowsingEnabled", descriptor.isArchiveBrowsingEnabled());
        remoteConfig.put("blockMismatchingMimeTypes", descriptor.isBlockMismatchingMimeTypes());
        remoteConfig.put("bypassHeadRequests", descriptor.isBypassHeadRequests());
        remoteConfig.put("disableUrlNormalization", descriptor.isDisableUrlNormalization());
        remoteConfig.put("listRemoteFolderItems", descriptor.isListRemoteFolderItems());
        remoteConfig.put("offline", descriptor.isOffline());
        remoteConfig.put("propagateQueryParams", descriptor.isPropagateQueryParams());
        remoteConfig.put("enableTokenAuthentication", descriptor.isEnableTokenAuthentication());
        remoteConfig.put("priorityResolution", descriptor.isPriorityResolution());
        return remoteConfig;
    }

}