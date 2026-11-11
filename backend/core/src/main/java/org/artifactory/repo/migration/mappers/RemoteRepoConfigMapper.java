package org.artifactory.repo.migration.mappers;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.repo.ChecksumPolicyType;
import org.artifactory.repo.remote.ContentSynchronisation;
import org.artifactory.repo.remote.CustomHttpHeader;
import org.artifactory.repo.remote.PropertiesContent;
import org.artifactory.repo.remote.SourceContent;
import org.artifactory.repo.remote.StatisticsContent;
import org.artifactory.repo.vcs.VcsGitProvider;
import org.artifactory.repo.vcs.VcsType;
import org.artifactory.util.CustomHttpHeaderValidator;
import org.artifactory.util.PackageTypeConfigFields;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.lang.String.valueOf;
import static org.artifactory.descriptor.repo.RepoType.P2;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.buildBaseConfig;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.buildSecurityConfig;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.customValueOf;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.externalDependenciesToConfig;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.setFederationMembers;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.setCdnRedirectRepoConfig;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.setCustomOrGlobalNugetNormalization;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.setDownloadRedirectRepoConfig;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.setPropertySetRefs;
import static org.artifactory.util.PackageTypeConfigFields.GITLAB_RESOLVE_SUBGROUPS;
import static org.artifactory.util.PackageTypeConfigFields.HEX_REGISTRY_PUBLIC_KEY;
import static org.artifactory.util.PackageTypeConfigFields.HEX_REGISTRY_URL;

@Slf4j
public class RemoteRepoConfigMapper {

    private static final String ENABLED = "enabled";

    private RemoteRepoConfigMapper() {
    }

    public static RemoteRepoConfig mapRemoteRepoConfig(RemoteRepoConfig remoteRepoConfig,
            Map<String, Object> newValues, CentralConfigDescriptor descriptor) {
        buildBaseConfig(remoteRepoConfig.getBaseConfig(), newValues, descriptor);
        buildRepoTypeConfig(remoteRepoConfig.getRepoTypeConfig(), newValues, descriptor);
        buildPackageTypeConfig(remoteRepoConfig.getPackageTypeConfig(), remoteRepoConfig.getPackageType(),
                newValues, descriptor);
        buildSecurityConfig(remoteRepoConfig.getSecurityConfig(), newValues, descriptor);
        buildFederationConfig(remoteRepoConfig, newValues);
        return remoteRepoConfig;
    }

    private static void buildFederationConfig(RemoteRepoConfig remoteRepoConfig, Map<String, Object> newValues) {
        if (!ConstantValues.remoteRepoFederationEnabled.getBoolean()) {
            return;
        }
        setFederationMembers(remoteRepoConfig, newValues);
    }

    private static void buildPackageTypeConfig(Map<String, String> packageTypeConfig,
            RepoType repoType, Map<String, Object> newValues, CentralConfigDescriptor descriptor) {
        for (Map.Entry<String, Object> newValue : newValues.entrySet()) {
            try {
                switch (repoType) {
                    case Maven, Ivy, Gradle, P2, SBT ->
                            addValuesToMavenPackageConfig(packageTypeConfig, repoType, newValue);
                    case Debian -> buildDebianTypeConfig(packageTypeConfig, newValue);
                    case Cargo -> buildCargoTypeConfig(packageTypeConfig, newValue);
                    case Bower -> buildBowerTypeConfig(packageTypeConfig, newValue);
                    case Composer -> buildComposerTypeConfig(packageTypeConfig, newValue);
                    case Docker -> buildDockerTypeConfig(packageTypeConfig, newValue);
                    case OCI, HelmOCI -> buildOciTypeConfig(packageTypeConfig, newValue);
                    case Helm -> buildHelmTypeConfig(packageTypeConfig, newValue);
                    case NuGet -> buildNugetTypeConfig(packageTypeConfig, newValue, descriptor);
                    case Pypi -> buildPypiTypeConfig(packageTypeConfig, newValue);
                    case VCS -> buildVcsTypeConfig(packageTypeConfig, newValue);
                    case Go -> buildGoTypeConfig(packageTypeConfig, newValue);
                    case Terraform -> buildTerraformTypeConfig(packageTypeConfig, newValue);
                    case Conan -> buildConanTypeConfig(packageTypeConfig, newValue);
                    case Swift -> externalDependenciesToConfig(packageTypeConfig, newValues);
                    case CocoaPods -> buildPodsTypeConfig(packageTypeConfig, newValue);
                    case Hex -> buildHexConfig(packageTypeConfig, newValue);
                    default -> log.trace("No type config for {}", repoType);
                }
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("No values under " + newValue.getKey());
            }
            buildVcsTypeConfig(packageTypeConfig, newValue);
            externalDependenciesToConfig(packageTypeConfig, newValues);
        }
    }

    private static void buildGoTypeConfig(Map<String, String> packageTypeConfig, Map.Entry<String, Object> newValue) {
        addValueToPackageTypeConfigIfNeeded(GITLAB_RESOLVE_SUBGROUPS.getKey(), packageTypeConfig, newValue);
    }

    private static void buildDebianTypeConfig(Map<String, String> packageTypeConfig,
            Map.Entry<String, Object> newValue) {
        addValueToPackageTypeConfigIfNeeded("ddebSupported", packageTypeConfig, newValue);
    }

    private static void buildPodsTypeConfig(Map<String, String> packageTypeConfig, Map.Entry<String, Object> newValue) {
        if ("cocoaPodsConfiguration".equals(newValue.getKey())) {
            Map<?, ?> cocoaPodsConfigurationValues = (Map<?, ?>) newValue.getValue();
            for (Map.Entry<?, ?> cocoaPodsConfigurationValue :
                    cocoaPodsConfigurationValues.entrySet()) {
                addValueToPackageTypeConfigIfNeeded("cocoaPodsSpecsRepoUrl", packageTypeConfig,
                        cocoaPodsConfigurationValue);
                addValueToPackageTypeConfigIfNeeded(PackageTypeConfigFields.COCOA_PODS_CDN_URL.getKey(),
                        packageTypeConfig, cocoaPodsConfigurationValue);
                addValueToPackageTypeConfigIfNeeded(
                        PackageTypeConfigFields.COCOA_PODS_FORCE_MODIFY_INDEX.getKey(),
                        packageTypeConfig, cocoaPodsConfigurationValue);

                if ("specRepoProvider".equals(cocoaPodsConfigurationValue.getKey())) {
                    Map<?, ?> specRepoProviderValues = (Map<?, ?>) newValue.getValue();
                    for (Map.Entry<?, ?> specRepoProviderValue :
                            specRepoProviderValues.entrySet()) {
                        addValueToPackageTypeConfigIfNeeded("specsRepoDownloadUrl", packageTypeConfig,
                                specRepoProviderValue);
                        addValueToPackageTypeConfigIfNeeded("specsRepoDownloadProvider",
                                packageTypeConfig,
                                specRepoProviderValue);
                    }
                }
            }
        }
    }

    private static void buildHexConfig(Map<String, String> packageTypeConfig, Map.Entry<String, Object> newValue) {
        if ("hexConfiguration".equals(newValue.getKey())) {
            Map<?, ?> hexConfigValues = (Map<?, ?>) newValue.getValue();
            for (Map.Entry<?, ?> hexConfigurationValue : hexConfigValues.entrySet()) {
                addValueToPackageTypeConfigIfNeeded(HEX_REGISTRY_URL.getKey(), packageTypeConfig,
                        hexConfigurationValue);
                addValueToPackageTypeConfigIfNeeded(HEX_REGISTRY_PUBLIC_KEY.getKey(), packageTypeConfig,
                        hexConfigurationValue);
            }
        }
    }

    private static void buildConanTypeConfig(Map<String, String> packageTypeConfig,
            Map.Entry<String, Object> newValue) {
        addValueToPackageTypeConfigIfNeeded("forceConanAuthentication", packageTypeConfig,
                newValue);
    }

    private static void buildTerraformTypeConfig(Map<String, String> packageTypeConfig,
            Map.Entry<String, Object> newValue) {
        if ("terraform".equals(newValue.getKey())) {
            Map<?, ?> terraformValues = (Map<?, ?>) newValue.getValue();
            for (Map.Entry<?, ?> terraformValue : terraformValues.entrySet()) {
                addValueToPackageTypeConfigIfNeeded("terraformProvidersUrl", packageTypeConfig,
                        terraformValue);
                addValueToPackageTypeConfigIfNeeded("terraformRegistryUrl", packageTypeConfig,
                        terraformValue);
            }
        }
    }

    private static void buildVcsTypeConfig(Map<String, String> packageTypeConfig, Map.Entry<String, Object> newValue) {
        if ("vcs".equals(newValue.getKey())) {
            setVcsNeeded((Map<?, ?>) newValue.getValue(), packageTypeConfig);
        }
    }

    private static void buildPypiTypeConfig(Map<String, String> packageTypeConfig, Map.Entry<String, Object> newValue) {
        if ("pypi".equals(newValue.getKey())) {
            Map<?, ?> pypiValues = (Map<?, ?>) newValue.getValue();
            for (Map.Entry<?, ?> pypiValue : pypiValues.entrySet()) {
                addValueToPackageTypeConfigIfNeeded("pyPIRegistryUrl", packageTypeConfig, pypiValue);
                addValueToPackageTypeConfigIfNeeded("packagesContextPath", packageTypeConfig,
                        pypiValue);
                addValueToPackageTypeConfigIfNeeded("repositorySuffix", packageTypeConfig, pypiValue);
                addValueToPackageTypeConfigIfNeeded("indexContextPath", packageTypeConfig, pypiValue);
            }
        }
    }

    private static void buildNugetTypeConfig(Map<String, String> packageTypeConfig,
            Map.Entry<String, Object> newValue, CentralConfigDescriptor descriptor) {
        if ("nuget".equals(newValue.getKey())) {
            Map<?, ?> nugetValues = (Map<?, ?>) newValue.getValue();
            for (Map.Entry<?, ?> nugetValue : nugetValues.entrySet()) {
                addValueToPackageTypeConfigIfNeeded("downloadContextPath", packageTypeConfig,
                        nugetValue);
                addValueToPackageTypeConfigIfNeeded("v3FeedUrl", packageTypeConfig, nugetValue);
                addValueToPackageTypeConfigIfNeeded("feedContextPath", packageTypeConfig, nugetValue);
                addValueToPackageTypeConfigIfNeeded("symbolServerUrl", packageTypeConfig, nugetValue);
                setCustomOrGlobalNugetNormalization(packageTypeConfig, nugetValue,
                        descriptor.getRepositoriesGeneralConfig());
            }
        }
        addValueToPackageTypeConfigIfNeeded("forceNugetAuthentication", packageTypeConfig, newValue);
    }

    private static void buildHelmTypeConfig(Map<String, String> packageTypeConfig, Map.Entry<String, Object> newValue) {
        if ("helm".equals(newValue.getKey())) {
            Map<?, ?> helmValues = (Map<?, ?>) newValue.getValue();
            for (Map.Entry<?, ?> helmValue : helmValues.entrySet()) {
                addValueToPackageTypeConfigIfNeeded("chartsBaseUrl", packageTypeConfig, helmValue);
            }
        }
    }

    private static void buildOciTypeConfig(Map<String, String> packageTypeConfig, Map.Entry<String, Object> newValue) {
        addValueToPackageTypeConfigIfNeeded("dockerTagRetention", packageTypeConfig, newValue);
        addValueToPackageTypeConfigIfNeeded("maxUniqueTags", packageTypeConfig, newValue);
        addValueToPackageTypeConfigIfNeeded("dockerProjectId", packageTypeConfig, newValue);
    }

    private static void buildComposerTypeConfig(Map<String, String> packageTypeConfig,
            Map.Entry<String, Object> newValue) {
        if ("composer".equals(newValue.getKey())) {
            Map<?, ?> composerNewValues = (Map<?, ?>) newValue.getValue();
            for (Map.Entry<?, ?> composerNewValue : composerNewValues.entrySet()) {
                addValueToPackageTypeConfigIfNeeded("composerRegistryUrl", packageTypeConfig,
                        composerNewValue);
            }
        }
    }

    private static void buildBowerTypeConfig(Map<String, String> packageTypeConfig,
            Map.Entry<String, Object> newValue) {
        if ("bower".equals(newValue.getKey())) {
            packageTypeConfig.put("bowerRegistryUrl",
                    valueOf(((Map<?, ?>) newValue.getValue()).get("bowerRegistryUrl")));
        }
    }

    private static void buildDockerTypeConfig(Map<String, String> packageTypeConfig,
            Map.Entry<String, Object> newValue) {
        addValueToPackageTypeConfigIfNeeded("dockerTagRetention", packageTypeConfig, newValue);
        addValueToPackageTypeConfigIfNeeded("dockerApiVersion", packageTypeConfig, newValue);
        addValueToPackageTypeConfigIfNeeded("blockPushingSchema1", packageTypeConfig, newValue);
        addValueToPackageTypeConfigIfNeeded("maxUniqueTags", packageTypeConfig, newValue);
        addValueToPackageTypeConfigIfNeeded("dockerProjectId", packageTypeConfig, newValue);
    }

    private static void buildCargoTypeConfig(Map<String, String> packageTypeConfig,
            Map.Entry<String, Object> newValue) {
        addValueToPackageTypeConfigIfNeeded("cargoAnonymousAccess", packageTypeConfig, newValue);
        addValueToPackageTypeConfigIfNeeded("cargoInternalIndex", packageTypeConfig, newValue);
        if ("git".equals(newValue.getKey())) {
            Map<?, ?> gitNewValues = (Map<?, ?>) newValue.getValue();
            for (Map.Entry<?, ?> gitNewValue : gitNewValues.entrySet()) {
                addValueToPackageTypeConfigIfNeeded("gitRegistryUrl", packageTypeConfig,
                        gitNewValue);
            }
        } else {
            addValueToPackageTypeConfigIfNeeded("gitRegistryUrl", packageTypeConfig, newValue);
        }
    }

    private static void addValuesToMavenPackageConfig(
            Map<String, String> packageTypeConfig, RepoType repoType, Map.Entry<String, Object> newValue) {
        addValueToPackageTypeConfigIfNeeded("suppressPomConsistencyChecks", packageTypeConfig, newValue);
        addValueToPackageTypeConfigIfNeeded("fetchJarsEagerly", packageTypeConfig, newValue);
        addValueToPackageTypeConfigIfNeeded("fetchSourcesEagerly", packageTypeConfig, newValue);
        addValueToPackageTypeConfigIfNeeded("rejectInvalidJars", packageTypeConfig, newValue);
        if (P2.equals(repoType)) {
            addValueToPackageTypeConfigIfNeeded("p2OriginalUrl", packageTypeConfig, newValue);
            addValueToPackageTypeConfigIfNeeded("forceP2Authentication", packageTypeConfig, newValue);
        }

    }

    private static void setVcsNeeded(Map<?, ?> vcs, Map<String, String> packageTypeConfig) {
        if (vcs.get("type") != null) {
            packageTypeConfig.put("vcsType",
                    Objects.requireNonNull(VcsType.fromString(valueOf(vcs.get("type"))))
                            .name());
        }
        if (vcs.get("git") != null) {
            Map<?, ?> git = (Map<?, ?>) vcs.get("git");
            if (git.get("provider") != null) {
                packageTypeConfig.put("vcsGitProvider",
                        Objects.requireNonNull(VcsGitProvider.fromString(valueOf(git.get("provider"))))
                                .name());
            }
            if (git.get("downloadUrl") != null) {
                packageTypeConfig.put("vcsGitDownloadUrl", valueOf(git.get("downloadUrl")));
            }
        }
    }

    private static void buildRepoTypeConfig(RemoteRepositoryRepoTypeConfig repoTypeConfig,
            Map<String, Object> newValues, CentralConfigDescriptor descriptor) {
        for (Map.Entry<String, Object> newValue : newValues.entrySet()) {
            try {
                switch (newValue.getKey()) {
                    case "maxUniqueSnapshots" -> repoTypeConfig
                            .setMaxUniqueSnapshots(parseInt(valueOf(newValue.getValue())));
                    case "handleReleases" -> repoTypeConfig
                            .setHandleReleases(parseBoolean(valueOf(newValue.getValue())));
                    case "handleSnapshots" -> repoTypeConfig
                            .setHandleSnapshots(parseBoolean(valueOf(newValue.getValue())));
                    case "cdnRedirect" -> setCdnRedirectRepoConfig(repoTypeConfig, newValue.getValue());
                    case "downloadRedirect" -> setDownloadRedirectRepoConfig(repoTypeConfig, newValue.getValue());
                    case "username" -> repoTypeConfig.setUsername(customValueOf(newValue.getValue()));
                    case "password" -> repoTypeConfig.setPassword(customValueOf(newValue.getValue()));
                    case "proxy" -> setProxyRef(descriptor, repoTypeConfig, newValue.getValue());
                    case "disableProxy" -> repoTypeConfig.setDisableProxy(parseBoolean(valueOf(newValue.getValue())));
                    case "propertySets" -> setPropertySetRefs(descriptor, repoTypeConfig, newValue.getValue());
                    case "remoteRepoChecksumPolicyType" -> repoTypeConfig
                            .setChecksumPolicyType(ChecksumPolicyType.fromString(valueOf(newValue.getValue())));
                    case "assumedOfflinePeriodSecs" -> repoTypeConfig
                            .setAssumedOfflinePeriodSecs(parseLong(valueOf(newValue.getValue())));
                    case "retrievalCachePeriodSecs" -> repoTypeConfig
                            .setRetrievalCachePeriodSecs(parseLong(valueOf(newValue.getValue())));
                    case "missedRetrievalCachePeriodSecs" -> repoTypeConfig
                            .setMissedRetrievalCachePeriodSecs(parseLong(valueOf(newValue.getValue())));
                    case "metadataRetrievalTimeoutSecs" -> repoTypeConfig
                            .setMetadataRetrievalTimeoutSecs(parseLong(valueOf(newValue.getValue())));
                    case "socketTimeoutMillis" -> repoTypeConfig
                            .setSocketTimeoutMillis(parseInt(valueOf(newValue.getValue())));
                    case "unusedArtifactsCleanupPeriodHours" -> repoTypeConfig
                            .setUnusedArtifactsCleanupPeriodHours(parseInt(valueOf(newValue.getValue())));
                    case "clientTlsCertificate" ->
                            repoTypeConfig.setClientTlsCertificate(customValueOf(newValue.getValue()));
                    case "queryParams" -> repoTypeConfig.setQueryParams(customValueOf(newValue.getValue()));
                    case "url" -> repoTypeConfig.setUrl(customValueOf(newValue.getValue()));
                    case "localAddress" -> repoTypeConfig.setLocalAddress(customValueOf(newValue.getValue()));
                    case "mismatchingMimeTypesOverrideList" ->
                            repoTypeConfig.setMismatchingMimeTypesOverrideList(valueOf(newValue.getValue()));
                    case "synchronizeProperties" ->
                            repoTypeConfig.setSynchronizeProperties(parseBoolean(valueOf(newValue.getValue())));
                    case "enableCookieManagement" ->
                            repoTypeConfig.setEnableCookieManagement(parseBoolean(valueOf(newValue.getValue())));
                    case "blackedOut" -> repoTypeConfig.setBlackedOut(parseBoolean(valueOf(newValue.getValue())));
                    case "allowAnyHostAuth" -> repoTypeConfig
                            .setAllowAnyHostAuth(parseBoolean(valueOf(newValue.getValue())));
                    case "blockMismatchingMimeTypes" ->
                            repoTypeConfig.setBlockMismatchingMimeTypes(parseBoolean(valueOf(newValue.getValue())));
                    case "bypassHeadRequests" -> repoTypeConfig
                            .setBypassHeadRequests(parseBoolean(valueOf(newValue.getValue())));
                    case "disableUrlNormalization" ->
                            repoTypeConfig.setDisableUrlNormalization(parseBoolean(valueOf(newValue.getValue())));
                    case "listRemoteFolderItems" ->
                            repoTypeConfig.setListRemoteFolderItems(parseBoolean(valueOf(newValue.getValue())));
                    case "customHttpHeaders" -> setCustomHttpHeaders(repoTypeConfig, newValue.getValue());
                    case "offline" -> repoTypeConfig.setOffline(parseBoolean(valueOf(newValue.getValue())));
                    case "enableTokenAuthentication" ->
                            repoTypeConfig.setEnableTokenAuthentication(parseBoolean(valueOf(newValue.getValue())));
                    case "priorityResolution" -> repoTypeConfig
                            .setPriorityResolution(parseBoolean(valueOf(newValue.getValue())));
                    case "shareConfiguration" -> repoTypeConfig
                            .setShareConfiguration(parseBoolean(valueOf(newValue.getValue())));
                    case "storeArtifactsLocally" ->
                            repoTypeConfig.setStoreArtifactsLocally(parseBoolean(valueOf(newValue.getValue())));
                    case "hardFail" -> repoTypeConfig.setHardFail(parseBoolean(valueOf(newValue.getValue())));
                    case "archiveBrowsingEnabled" ->
                            repoTypeConfig.setArchiveBrowsingEnabled(parseBoolean(valueOf(newValue.getValue())));
                    case "sendContext" -> repoTypeConfig.setSendContext(parseBoolean(valueOf(newValue.getValue())));
                    case "passThrough" -> repoTypeConfig.setPassThrough(parseBoolean(valueOf(newValue.getValue())));
                    case "curated" -> repoTypeConfig.setCurated(parseBoolean(valueOf(newValue.getValue())));
                    case "propagateQueryParams" ->
                            repoTypeConfig.setPropagateQueryParams(parseBoolean(valueOf(newValue.getValue())));
                    case "contentSynchronisation" -> setContentSynchronisation(repoTypeConfig, newValue.getValue());
                    case "remoteRepoLayoutRef" ->
                            repoTypeConfig.setRemoteRepoLayoutRef(customValueOf(newValue.getValue()));
                    case "retrieveSha256FromServer" ->
                            repoTypeConfig.setRetrieveSha256FromServer(parseBoolean(valueOf(newValue.getValue())));
                    default -> log.trace("No repo type config for {}", newValue.getKey());
                }
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("No values under " + newValue.getKey());
            }
        }
    }

    private static void setCustomHttpHeaders(RemoteRepositoryRepoTypeConfig repoTypeConfig, Object value) {
        if (value == null) {
            repoTypeConfig.setCustomHttpHeaders(new ArrayList<>());
            return;
        }
        final List<?> rawList;
        if (value instanceof List<?> list) {
            rawList = list;
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                rawList = List.of();
            } else if (map.containsKey("name")) {
                rawList = List.of(map);
            } else {
                rawList = map.entrySet().stream()
                        .sorted(Comparator.comparingInt(e -> {
                            try {
                                return parseInt(e.getKey().toString().trim());
                            } catch (NumberFormatException ex) {
                                return Integer.MAX_VALUE;
                            }
                        }))
                        .map(Map.Entry::getValue)
                        .toList();
                if (rawList.stream().anyMatch(v -> !(v instanceof Map))) {
                    throw new IllegalArgumentException("Invalid customHttpHeaders value: expected a list");
                }
            }
        } else {
            throw new IllegalArgumentException("Invalid customHttpHeaders value: expected a list");
        }
        List<CustomHttpHeader> headers = new ArrayList<>();
        for (Object entry : rawList) {
            if (!(entry instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Invalid customHttpHeaders entry: expected a map");
            }
            String name = map.get("name") != null ? valueOf(map.get("name")) : "";
            String headerValue = map.get("value") != null ? valueOf(map.get("value")) : "";
            Object sens = map.get("sensitive");
            boolean sensitive = sens instanceof Boolean ? (Boolean) sens
                    : (sens != null && parseBoolean(valueOf(sens)));
            headers.add(new CustomHttpHeader(name, headerValue, sensitive));
        }
        CustomHttpHeaderValidator.validate(headers);
        repoTypeConfig.setCustomHttpHeaders(headers);
    }

    private static void setContentSynchronisation(RemoteRepositoryRepoTypeConfig repoTypeConfig, Object value) {
        ContentSynchronisation contentSynchronisation =
                repoTypeConfig.getContentSynchronisation() != null ? repoTypeConfig.getContentSynchronisation() :
                        new ContentSynchronisation();
        Map<String, Object> contentSynchronisationNewValues = (Map<String, Object>) value;
        for (Map.Entry<String, Object> contentSynchronisationNewValue :
                contentSynchronisationNewValues.entrySet()) {
            switch (contentSynchronisationNewValue.getKey()) {
                case ENABLED -> contentSynchronisation
                        .setEnabled(parseBoolean(valueOf(contentSynchronisationNewValue.getValue())));
                case "properties" -> setProperties(contentSynchronisationNewValue, contentSynchronisation);
                case "statistics" -> setStatistics(contentSynchronisationNewValue, contentSynchronisation);
                case "source" -> setSource(contentSynchronisationNewValue, contentSynchronisation);
                default -> log.trace("No content set for {}", contentSynchronisationNewValue.getKey());
            }
        }
    }

    private static void setSource(Map.Entry<String, Object> contentSynchronisationNewValue,
            ContentSynchronisation contentSynchronisation) {
        SourceContent sourceContent = new SourceContent();
        if (contentSynchronisation.getSource() != null) {
            sourceContent = contentSynchronisation.getSource();
        }
        HashMap<?, ?> sourceContentNewValues =
                (HashMap<?, ?>) contentSynchronisationNewValue.getValue();
        if (!sourceContentNewValues.isEmpty()) {
            sourceContent.setOriginAbsenceDetection(
                    parseBoolean(valueOf(sourceContentNewValues.get("originAbsenceDetection"))));
            contentSynchronisation.setSource(sourceContent);
        }
    }

    private static void setStatistics(Map.Entry<String, Object> contentSynchronisationNewValue,
            ContentSynchronisation contentSynchronisation) {
        StatisticsContent statisticsContent = new StatisticsContent();
        if (contentSynchronisation.getStatistics() != null) {
            statisticsContent = contentSynchronisation.getStatistics();
        }
        HashMap<?, ?> statisticsContentNewValues =
                (HashMap<?, ?>) contentSynchronisationNewValue.getValue();
        if (!statisticsContentNewValues.isEmpty()) {
            statisticsContent.setEnabled(parseBoolean(valueOf(statisticsContentNewValues.get(ENABLED))));
            contentSynchronisation.setStatistics(statisticsContent);
        }
    }

    private static void setProperties(Map.Entry<String, Object> contentSynchronisationNewValue,
            ContentSynchronisation contentSynchronisation) {
        PropertiesContent propertiesContent = contentSynchronisation.getProperties() != null ?
                contentSynchronisation.getProperties() : new PropertiesContent();
        HashMap<?, ?> propertiesContentNewValues =
                (HashMap<?, ?>) contentSynchronisationNewValue.getValue();
        if (!propertiesContentNewValues.isEmpty()) {
            propertiesContent.setEnabled(parseBoolean(valueOf(propertiesContentNewValues.get(ENABLED))));
            contentSynchronisation.setProperties(propertiesContent);
        }
    }

    private static void setProxyRef(CentralConfigDescriptor descriptor, RemoteRepositoryRepoTypeConfig
                    repoTypeConfig,
            Object value) {
        String proxyRef = valueOf(value);
        if (descriptor.getProxy(proxyRef) == null) {
            log.debug("Proxy '{}' does not exist in the config descriptor", proxyRef);
        } else {
            repoTypeConfig.setProxyRef(proxyRef);
        }
    }

    private static void addValueToPackageTypeConfigIfNeeded(String
                    key, Map<String, String> packageTypeConfig,
            Map.Entry<?, ?> entry) {
        if (key.equals(entry.getKey())) {
            packageTypeConfig.put(key, customValueOf(entry.getValue()));
        }
    }
}