/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.descriptor.repo;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlIDREF;
import jakarta.xml.bind.annotation.XmlType;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.repo.vcs.VcsGitProvider;
import org.artifactory.descriptor.repo.vcs.VcsType;
import org.artifactory.model.BaseConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.SecurityConfig;
import org.artifactory.repo.remote.ContentSynchronisation;
import org.artifactory.repo.remote.PropertiesContent;
import org.artifactory.repo.remote.SourceContent;
import org.artifactory.repo.remote.StatisticsContent;
import org.jfrog.common.config.diff.DiffReference;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.artifactory.util.ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_ENABLED;
import static org.artifactory.util.ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS;
import static org.artifactory.util.PackageTypeConfigFields.AUTH_URL;

import static org.artifactory.util.PackageTypeConfigFields.COCOA_PODS_CDN_URL;
import static org.artifactory.util.PackageTypeConfigFields.COCOA_PODS_FORCE_MODIFY_INDEX;
import static org.artifactory.util.PackageTypeConfigFields.ENABLE_NUGET_NORMALIZED_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.GITLAB_RESOLVE_SUBGROUPS;
import static org.artifactory.util.PackageTypeConfigFields.HEX_REGISTRY_PUBLIC_KEY;
import static org.artifactory.util.PackageTypeConfigFields.HEX_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.HUGGINGFACE_ML_REPO_LAYOUT;
import static org.artifactory.util.VcsPackageTypeConfigMapper.VCS_GIT_DOWNLOAD_URL;
import static org.artifactory.util.VcsPackageTypeConfigMapper.VCS_GIT_PROVIDER;
import static org.artifactory.util.VcsPackageTypeConfigMapper.VCS_TYPE;

/**
 * @author Fred Simon
 */
@XmlType(name = "RemoteRepoType",
        propOrder = {"username", "password", "allowAnyHostAuth", "socketTimeoutMillis", "enableCookieManagement",
                "enableTokenAuthentication", "localAddress", "proxy", "disableProxy", "queryParams",
                "propagateQueryParams", "clientTlsCertificate", "disableUrlNormalization", "retrieveSha256FromServer",
                "customHttpHeaders"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
@Setter
@Getter
@Slf4j
public class HttpRepoDescriptor extends RemoteRepoDescriptor implements ProxyRepoDescriptor {

    @NotUnicode
    private String username;

    @NotUnicode
    private String password;

    private boolean allowAnyHostAuth;

    @XmlElement(defaultValue = "15000")
    private int socketTimeoutMillis = 15000;//Default socket timeout

    private boolean enableCookieManagement;

    private boolean enableTokenAuthentication;

    private String localAddress;

    @XmlIDREF
    @XmlElement(name = "proxyRef")
    @DiffReference
    @Valid
    private ProxyDescriptor proxy;

    private boolean disableProxy;

    @NotUnicode
    private String queryParams;

    private boolean propagateQueryParams;

    private boolean retrieveSha256FromServer;

    @XmlElement(name = "clientTlsCertificate")
    @NotUnicode
    private String clientTlsCertificate;

    @XmlElement(defaultValue = "false")
    private boolean disableUrlNormalization;

    @XmlElementWrapper(name = "customHttpHeaders")
    @XmlElement(name = "customHttpHeader", required = false)
    private List<CustomHttpHeaderDescriptor> customHttpHeaders = new ArrayList<>();

    public HttpRepoDescriptor() {
    }

    public HttpRepoDescriptor(RemoteRepoConfig repositoryConfig, List<RepoLayout> repoLayouts,
            List<ProxyDescriptor> proxies, List<PropertySet> propertySets, List<KeyPairDescriptor> keyPairs) {
        setKey(repositoryConfig.getKey());
        setType(repositoryConfig.getPackageType());
        setBaseConfig(repositoryConfig.getBaseConfig(), repoLayouts);
        setRepoTypeConfig(repositoryConfig.getRepoTypeConfig(), repoLayouts, proxies, propertySets);
        setSecurityConfig(repositoryConfig.getSecurityConfig(), keyPairs);
        setPackageTypeConfig(repositoryConfig.getPackageTypeConfig(), repositoryConfig.getPackageType());
    }

    @Override
    public RemoteRepoConfig toRepositoryConfig() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create(getKey(), getType());
        buildBaseConfig(remoteRepoConfig.getBaseConfig());
        buildRepoTypeConfig(remoteRepoConfig.getRepoTypeConfig());
        buildSecurityConfig(remoteRepoConfig.getSecurityConfig());
        buildPackageTypeConfig(getType(), remoteRepoConfig.getPackageTypeConfig());
        return remoteRepoConfig;
    }

    public static HttpRepoDescriptor fromRepositoryConfig(CentralConfigDescriptor centralConfigDescriptor,
            RemoteRepoConfig sourceRepoConfig) {
        return new HttpRepoDescriptor(sourceRepoConfig, centralConfigDescriptor.getRepoLayouts(),
                centralConfigDescriptor.getProxies(), centralConfigDescriptor.getPropertySets(),
                centralConfigDescriptor.getKeyPairs());
    }


    public String getQueryParams() {
        return StringUtils.removeStart(queryParams, "?");
    }

    @Override
    public void processUrl() {
        final String[] credentials = extractCredentialsFromUrl(getUrl());
        if (credentials != null) {
            setUsername(credentials[0]);
            setPassword(credentials[1]);
            setUrl(getUrl().replace(getUsername() + ":" + getPassword() + "@", ""));
        }
    }

    String[] extractCredentialsFromUrl(String url) {
        return Optional.ofNullable(url)
                .map(l -> {
                    try {
                        return new URL(l);
                    } catch (MalformedURLException e) {
                        log.warn("Can not parse URL: {}", url);
                        return null;
                    }
                })
                .map(URL::getUserInfo)
                .map(l -> l.split(":"))
                .filter(l -> l.length > 1)
                .orElse(null);
    }

    private void setBaseConfig(BaseConfig baseConfig, List<RepoLayout> repoLayouts) {
        setDescription(baseConfig.getDescription());
        setNotes(baseConfig.getNotes());
        for (RepoLayout repoLayout : repoLayouts) {
            if (repoLayout.getName().equals(baseConfig.getRepoLayoutRef())) {
                setRepoLayout(repoLayout);
            }
        }
        setIncludesPattern(baseConfig.getIncludesPattern());
        setExcludesPattern(baseConfig.getExcludesPattern());
        setGridTopologyId(baseConfig.getFederationConfig().getGridTopologyId());
    }

    private void buildBaseConfig(BaseConfig baseConfig) {
        baseConfig.setDescription(getDescription());
        baseConfig.setNotes(getNotes());
        if (getRepoLayout() != null) {
            baseConfig.setRepoLayoutRef(getRepoLayout().getName());
        }
        baseConfig.setIncludesPattern(getIncludesPattern());
        baseConfig.setExcludesPattern(getExcludesPattern());
        baseConfig.setGridTopologyId(getGridTopologyId());
    }

    private void setRepoTypeConfig(RemoteRepositoryRepoTypeConfig remoteConfig,
            List<RepoLayout> repoLayouts, List<ProxyDescriptor> proxies,
            List<PropertySet> propertySets) {
        setHandleReleases(remoteConfig.isHandleReleases());
        setHandleSnapshots(remoteConfig.isHandleSnapshots());
        setMaxUniqueSnapshots(remoteConfig.getMaxUniqueSnapshots());
        setCdnRedirect(remoteConfig);
        setDownloadRedirect(remoteConfig);
        setUsername(remoteConfig.getUsername());
        setPassword(remoteConfig.getPassword());
        setLocalAddress(remoteConfig.getLocalAddress());
        setRepoLayoutConfig(remoteConfig, repoLayouts);
        setProxies(remoteConfig, proxies);
        setDisableProxy(remoteConfig.isDisableProxy());
        setPropertySetsConfig(remoteConfig, propertySets);
        setChecksumPolicyType(ChecksumPolicyTypeDescriptor.valueOf(remoteConfig.getChecksumPolicyType().name()));
        setAssumedOfflinePeriodSecs(remoteConfig.getAssumedOfflinePeriodSecs());
        setRetrievalCachePeriodSecs(remoteConfig.getRetrievalCachePeriodSecs());
        setMissedRetrievalCachePeriodSecs(remoteConfig.getMissedRetrievalCachePeriodSecs());
        setMetadataRetrievalTimeoutSecs(remoteConfig.getMetadataRetrievalTimeoutSecs());
        setSocketTimeoutMillis(remoteConfig.getSocketTimeoutMillis());
        setUnusedArtifactsCleanupPeriodHours(remoteConfig.getUnusedArtifactsCleanupPeriodHours());
        setClientTlsCertificate(remoteConfig.getClientTlsCertificate());
        setContentSynchronisation(remoteConfig);
        setQueryParams(remoteConfig.getQueryParams());
        setUrl(remoteConfig.getUrl());
        setLocalAddress(remoteConfig.getLocalAddress());
        setMismatchingMimeTypesOverrideList(remoteConfig.getMismatchingMimeTypesOverrideList());
        setSynchronizeProperties(remoteConfig.isSynchronizeProperties());
        setShareConfiguration(remoteConfig.isShareConfiguration());
        setStoreArtifactsLocally(remoteConfig.isStoreArtifactsLocally());
        setHardFail(remoteConfig.isHardFail());
        setEnableCookieManagement(remoteConfig.isEnableCookieManagement());
        setBlackedOut(remoteConfig.isBlackedOut());
        setAllowAnyHostAuth(remoteConfig.isAllowAnyHostAuth());
        setArchiveBrowsingEnabled(remoteConfig.isArchiveBrowsingEnabled());
        setBlockMismatchingMimeTypes(remoteConfig.isBlockMismatchingMimeTypes());
        setBypassHeadRequests(remoteConfig.isBypassHeadRequests());
        setDisableUrlNormalization(remoteConfig.isDisableUrlNormalization());
        setListRemoteFolderItems(remoteConfig.isListRemoteFolderItems());
        setOffline(remoteConfig.isOffline());
        setPropagateQueryParams(remoteConfig.isPropagateQueryParams());
        setEnableTokenAuthentication(remoteConfig.isEnableTokenAuthentication());
        setPriorityResolution(remoteConfig.isPriorityResolution());
        setSendContext(remoteConfig.isSendContext());
        setPassThrough(remoteConfig.isPassThrough());
        setCurated(remoteConfig.isCurated());
        setRetrieveSha256FromServer(remoteConfig.isRetrieveSha256FromServer());
        if (remoteConfig.getCustomHttpHeaders() == null || remoteConfig.getCustomHttpHeaders().isEmpty()) {
            setCustomHttpHeaders(new ArrayList<>());
        } else {
            setCustomHttpHeaders(remoteConfig.getCustomHttpHeaders().stream()
                    .map(header -> new CustomHttpHeaderDescriptor(
                            header.getName(), header.getValue(), header.isSensitive()))
                    .collect(Collectors.toCollection(ArrayList::new)));
        }
    }

    private void setRepoLayoutConfig(RemoteRepositoryRepoTypeConfig remoteConfig, List<RepoLayout> repoLayouts) {
        for (RepoLayout repoLayout : repoLayouts) {
            if (repoLayout.getName().equals(remoteConfig.getRemoteRepoLayoutRef())) {
                setRemoteRepoLayout(repoLayout);
            }
        }
    }

    private void setCdnRedirect(RemoteRepositoryRepoTypeConfig remoteConfig) {
        org.artifactory.repo.CdnRedirectRepoConfig cdnRedirectRepoConfig = remoteConfig.getCdnRedirectRepoConfig();
        if (cdnRedirectRepoConfig != null) {
            CdnRedirectRepoConfigDescriptor cdnRedirectRepoConfigDescriptor =
                    new CdnRedirectRepoConfigDescriptor();
            cdnRedirectRepoConfigDescriptor.setEnabled(cdnRedirectRepoConfig.isEnabled());
            setCdnRedirectConfig(cdnRedirectRepoConfigDescriptor);
        }
    }

    private void setDownloadRedirect(RemoteRepositoryRepoTypeConfig remoteConfig) {
        org.artifactory.repo.DownloadRedirectRepoConfig downloadRedirectConfig =
                remoteConfig.getDownloadRedirectConfig();
        if (downloadRedirectConfig != null) {
            DownloadRedirectRepoConfigDescriptor downloadRedirectDescriptor =
                    new DownloadRedirectRepoConfigDescriptor();
            downloadRedirectDescriptor.setEnabled(downloadRedirectConfig.isEnabled());
            setDownloadRedirectConfig(downloadRedirectDescriptor);
        }
    }

    private void setPropertySetsConfig(RemoteRepositoryRepoTypeConfig remoteConfig, List<PropertySet> propertySets) {
        ArrayList<PropertySet> newPropertySets = new ArrayList<>();
        for (String propertySetRef : remoteConfig.getPropertySetRefs()) {
            for (PropertySet propertySet : propertySets) {
                if (propertySet.getName().equals(propertySetRef)) {
                    newPropertySets.add(propertySet);
                }
            }
        }
        setPropertySets(newPropertySets);
    }

    private void setProxies(RemoteRepositoryRepoTypeConfig remoteConfig, List<ProxyDescriptor> proxies) {
        for (ProxyDescriptor proxyDescriptor : proxies) {
            if (proxyDescriptor.getKey().equals(remoteConfig.getProxyRef())) {
                setProxy(proxyDescriptor);
            }
        }
    }

    private void setContentSynchronisation(RemoteRepositoryRepoTypeConfig remoteConfig) {
        ContentSynchronisation contentSynchronisation = remoteConfig.getContentSynchronisation();
        if (contentSynchronisation != null) {
            org.artifactory.descriptor.delegation.ContentSynchronisationDescriptor contentSynchronisationDescriptor =
                    new org.artifactory.descriptor.delegation.ContentSynchronisationDescriptor();
            contentSynchronisationDescriptor.setEnabled(contentSynchronisation.isEnabled());
            contentSynchronisationDescriptor.getStatistics()
                    .setEnabled(remoteConfig.getContentSynchronisation().getStatistics().isEnabled());
            contentSynchronisationDescriptor.getProperties()
                    .setEnabled(remoteConfig.getContentSynchronisation().getProperties().isEnabled());
            contentSynchronisationDescriptor.getSource()
                    .setOriginAbsenceDetection(
                            remoteConfig.getContentSynchronisation().getSource().isOriginAbsenceDetection());
            setContentSynchronisation(contentSynchronisationDescriptor);
        }
    }

    private void buildRepoTypeConfig(@Nonnull RemoteRepositoryRepoTypeConfig remoteConfig) {
        remoteConfig.setHandleReleases(isHandleReleases());
        remoteConfig.setHandleSnapshots(isHandleSnapshots());
        remoteConfig.setMaxUniqueSnapshots(getMaxUniqueSnapshots());
        buildCdnRedirectConfig(remoteConfig);
        buildDownloadRedirectConfig(remoteConfig);
        remoteConfig.setUsername(getUsername());
        remoteConfig.setPassword(getPassword());
        remoteConfig.setLocalAddress(getLocalAddress());
        if (getRemoteRepoLayout() != null) {
            remoteConfig.setRemoteRepoLayoutRef(getRemoteRepoLayout().getName());
        }
        if (getProxy() != null) {
            remoteConfig.setProxyRef(getProxy().getKey());
        }
        remoteConfig.setDisableProxy(isDisableProxy());
        List<String> propertySetRefs = new ArrayList<>();
        getPropertySets().forEach(p -> propertySetRefs.add(p.getName()));
        remoteConfig.setPropertySetRefs(propertySetRefs);
        remoteConfig
                .setChecksumPolicyType(org.artifactory.repo.ChecksumPolicyType.valueOf(getChecksumPolicyType().name()));
        remoteConfig.setAssumedOfflinePeriodSecs(getAssumedOfflinePeriodSecs());
        remoteConfig.setRetrievalCachePeriodSecs(getRetrievalCachePeriodSecs());
        remoteConfig.setMissedRetrievalCachePeriodSecs(getMissedRetrievalCachePeriodSecs());
        remoteConfig.setMetadataRetrievalTimeoutSecs(getMetadataRetrievalTimeoutSecs());
        remoteConfig.setSocketTimeoutMillis(getSocketTimeoutMillis());
        remoteConfig.setUnusedArtifactsCleanupPeriodHours(getUnusedArtifactsCleanupPeriodHours());
        remoteConfig.setClientTlsCertificate(getClientTlsCertificate());

        buildRemoteContentSyncronisationConfig(remoteConfig);
        remoteConfig.setQueryParams(getQueryParams());
        remoteConfig.setUrl(getUrl());
        remoteConfig.setLocalAddress(getLocalAddress());
        remoteConfig.setMismatchingMimeTypesOverrideList(getMismatchingMimeTypesOverrideList());
        remoteConfig.setSynchronizeProperties(isSynchronizeProperties());
        remoteConfig.setShareConfiguration(isShareConfiguration());
        remoteConfig.setStoreArtifactsLocally(isStoreArtifactsLocally());
        remoteConfig.setHardFail(isHardFail());
        remoteConfig.setEnableCookieManagement(isEnableCookieManagement());
        remoteConfig.setBlackedOut(isBlackedOut());
        remoteConfig.setAllowAnyHostAuth(isAllowAnyHostAuth());
        remoteConfig.setArchiveBrowsingEnabled(isArchiveBrowsingEnabled());
        remoteConfig.setBlockMismatchingMimeTypes(isBlockMismatchingMimeTypes());
        remoteConfig.setBypassHeadRequests(isBypassHeadRequests());
        remoteConfig.setDisableUrlNormalization(isDisableUrlNormalization());
        remoteConfig.setListRemoteFolderItems(isListRemoteFolderItems());
        remoteConfig.setOffline(isOffline());
        remoteConfig.setPropagateQueryParams(isPropagateQueryParams());
        remoteConfig.setEnableTokenAuthentication(isEnableTokenAuthentication());
        remoteConfig.setPriorityResolution(isPriorityResolution());
        remoteConfig.setSendContext(isSendContext());
        remoteConfig.setPassThrough(isPassThrough());
        remoteConfig.setCurated(isCurated());
        remoteConfig.setRetrieveSha256FromServer(isRetrieveSha256FromServer());
        if (getCustomHttpHeaders() == null || getCustomHttpHeaders().isEmpty()) {
            remoteConfig.setCustomHttpHeaders(new ArrayList<>());
        } else {
            remoteConfig.setCustomHttpHeaders(getCustomHttpHeaders().stream()
                    .map(header -> new org.artifactory.repo.remote.CustomHttpHeader(
                            header.getName(), header.getValue(), header.isSensitive()))
                    .collect(Collectors.toCollection(ArrayList::new)));
        }
    }

    private void buildDownloadRedirectConfig(@Nonnull RemoteRepositoryRepoTypeConfig remoteConfig) {
        if (getDownloadRedirectConfig() != null) {
            org.artifactory.repo.DownloadRedirectRepoConfig downloadRedirectConfig =
                    new org.artifactory.repo.DownloadRedirectRepoConfig();
            downloadRedirectConfig.setEnabled(getDownloadRedirectConfig().isEnabled());
            remoteConfig.setDownloadRedirectConfig(downloadRedirectConfig);
        }
    }

    private void buildCdnRedirectConfig(@Nonnull RemoteRepositoryRepoTypeConfig remoteConfig) {
        if (getCdnRedirectRepoConfig() != null) {
            org.artifactory.repo.CdnRedirectRepoConfig cdnRedirectRepoConfig =
                    new org.artifactory.repo.CdnRedirectRepoConfig();
            cdnRedirectRepoConfig.setEnabled(getCdnRedirectRepoConfig().isEnabled());
            remoteConfig.setCdnRedirectRepoConfig(cdnRedirectRepoConfig);
        }
    }

    private void buildRemoteContentSyncronisationConfig(@Nonnull RemoteRepositoryRepoTypeConfig remoteConfig) {
        if (getContentSynchronisation() != null) {
            ContentSynchronisation contentSynchronisation = new ContentSynchronisation();
            contentSynchronisation.setEnabled(getContentSynchronisation().isEnabled());
            if (getContentSynchronisation().getProperties() != null) {
                PropertiesContent properties = new PropertiesContent();
                properties.setEnabled(getContentSynchronisation().getProperties().isEnabled());
                contentSynchronisation.setProperties(properties);
            }
            if (getContentSynchronisation().getSource() != null) {
                SourceContent source = new SourceContent();
                source.setOriginAbsenceDetection(getContentSynchronisation().getSource().isOriginAbsenceDetection());
                contentSynchronisation.setSource(source);
            }
            if (getContentSynchronisation().getStatistics() != null) {
                StatisticsContent statistics = new StatisticsContent();
                statistics.setEnabled(getContentSynchronisation().getStatistics().isEnabled());
                contentSynchronisation.setStatistics(statistics);
            }
            remoteConfig.setContentSynchronisation(contentSynchronisation);
        }
    }

    @VisibleForTesting
    void setPackageTypeConfig(Map<String, String> packageTypeConfig,
            RepoType packageType) {
        if (packageType.isMavenGroup()) {
            setMavenGroupTypeConfig(packageTypeConfig, packageType);
        } else {
            switch (packageType) {
                case Bower -> setBowerTypeConfig(packageTypeConfig);
                case Cargo -> setCargoTypeConfig(packageTypeConfig);
                case CocoaPods -> setPodsTypeConfig(packageTypeConfig);
                case Composer -> setComposerTypeConfig(packageTypeConfig);
                case Conan -> setConanTypeConfig(packageTypeConfig);
                case Debian -> setDdebSupported(Boolean.parseBoolean(packageTypeConfig.get("ddebSupported")));
                case Go -> setGoTypeConfig(packageTypeConfig);
                case Helm -> setHelmTypeConfig(packageTypeConfig);
                case NuGet -> setNugetTypeConfig(packageTypeConfig);
                case OCI -> setOciTypeConfig(packageTypeConfig);
                case Pypi -> setPypiTypeConfig(packageTypeConfig);
                case Swift, AIEditorExtensions ->
                        setExternalDependencies(externalDependenciesToDescriptor(packageTypeConfig));
                case Terraform -> setTerraformTypeConfig(packageTypeConfig);
                case VCS -> setVcsTypeConfig(packageTypeConfig);
                case Hex -> setHexTypeConfig(packageTypeConfig);
                case HuggingFaceML -> setHuggingFaceMLTypeConfig(packageTypeConfig);
                case Ansible -> setAnsibleTypeConfig(packageTypeConfig);
                default -> log.trace("no package type config for {}", packageType);
            }
        }
    }

    private void setConanTypeConfig(Map<String, String> packageTypeConfig) {
        setForceConanAuthentication(
                Boolean.parseBoolean(packageTypeConfig.get("forceConanAuthentication")));
    }

    private void setGoTypeConfig(Map<String, String> packageTypeConfig) {
        if (packageTypeConfig.containsKey(VCS_TYPE)) {
            setVcsTypeConfig(packageTypeConfig);
        }
        setExternalDependencies(externalDependenciesToDescriptor(packageTypeConfig));

        GoConfiguration goConfiguration = new GoConfiguration();
        goConfiguration.setGitLabResolveSubgroups(
                Boolean.parseBoolean(packageTypeConfig.get(GITLAB_RESOLVE_SUBGROUPS.getKey())));
        setGo(goConfiguration);
    }

    private void setMavenGroupTypeConfig(Map<String, String> packageTypeConfig, RepoType packageType) {
        setSuppressPomConsistencyChecks(
                Boolean.parseBoolean(packageTypeConfig.get("suppressPomConsistencyChecks")));
        setFetchJarsEagerly(
                Boolean.parseBoolean(packageTypeConfig.get("fetchJarsEagerly")));
        setFetchSourcesEagerly(
                Boolean.parseBoolean(packageTypeConfig.get("fetchSourcesEagerly")));
        setRejectInvalidJars(
                Boolean.parseBoolean(packageTypeConfig.get("rejectInvalidJars")));
        if (RepoType.P2.equals(packageType)) {
            setP2OriginalUrl(packageTypeConfig.get("p2OriginalUrl"));
            setForceP2Authentication(Boolean.parseBoolean(packageTypeConfig.get("forceP2Authentication")));
        }
    }

    private void setBowerTypeConfig(Map<String, String> packageTypeConfig) {
        BowerConfiguration bowerConfiguration = new BowerConfiguration();
        bowerConfiguration.setBowerRegistryUrl(packageTypeConfig.get("bowerRegistryUrl"));
        if (packageTypeConfig.containsKey(VCS_TYPE)) {
            VcsConfiguration vcs = new VcsConfiguration();
            vcs.setGit(vcsGitConfigurationToDescriptor(packageTypeConfig));
            vcs.setType(VcsType.valueOf(packageTypeConfig.get(VCS_TYPE)));
            setVcs(vcs);
        }
        setBower(bowerConfiguration);
    }

    private void setTerraformTypeConfig(Map<String, String> packageTypeConfig) {
        TerraformConfiguration terraformConfiguration = new TerraformConfiguration();
        Optional.ofNullable(packageTypeConfig.get("terraformProvidersUrl"))
                .ifPresent(terraformConfiguration::setTerraformProvidersUrl);
        Optional.ofNullable(packageTypeConfig.get("terraformRegistryUrl"))
                .ifPresent(terraformConfiguration::setTerraformRegistryUrl);
        setTerraform(terraformConfiguration);
        if (packageTypeConfig.containsKey(VCS_TYPE)) {
            VcsConfiguration vcs = new VcsConfiguration();
            vcs.setGit(vcsGitConfigurationToDescriptor(packageTypeConfig));
            vcs.setType(VcsType.valueOf(packageTypeConfig.get(VCS_TYPE)));
            setVcs(vcs);
        }
        setExternalDependencies(externalDependenciesToDescriptor(packageTypeConfig));
    }

    private void setVcsTypeConfig(Map<String, String> packageTypeConfig) {
        VcsConfiguration vcs = new VcsConfiguration();
        vcs.setGit(vcsGitConfigurationToDescriptor(packageTypeConfig));
        Optional.ofNullable(packageTypeConfig.get(VCS_TYPE))
                .ifPresent(type -> vcs.setType(VcsType.valueOf(type)));
        setVcs(vcs);
    }

    private void setPypiTypeConfig(Map<String, String> packageTypeConfig) {
        PypiConfiguration pypiConfiguration = new PypiConfiguration();
        Optional.ofNullable(packageTypeConfig.get("pyPIRegistryUrl"))
                .ifPresent(pypiConfiguration::setPyPIRegistryUrl);
        Optional.ofNullable(packageTypeConfig.get("packagesContextPath"))
                .ifPresent(pypiConfiguration::setPackagesContextPath);
        Optional.ofNullable(packageTypeConfig.get("repositorySuffix"))
                .ifPresent(pypiConfiguration::setRepositorySuffix);
        Optional.ofNullable(packageTypeConfig.get("indexContextPath"))
                .ifPresent(pypiConfiguration::setIndexContextPath);
        setPypi(pypiConfiguration);
    }

    private void setNugetTypeConfig(Map<String, String> packageTypeConfig) {
        NuGetConfiguration configuration = new NuGetConfiguration();
        Optional.ofNullable(packageTypeConfig.get("v3FeedUrl"))
                .ifPresent(configuration::setV3FeedUrl);
        Optional.ofNullable(packageTypeConfig.get("feedContextPath"))
                .ifPresent(configuration::setFeedContextPath);
        Optional.ofNullable(packageTypeConfig.get("downloadContextPath"))
                .ifPresent(configuration::setDownloadContextPath);
        Optional.ofNullable(packageTypeConfig.get("symbolServerUrl"))
                .ifPresent(configuration::setSymbolServerUrl);
        Optional.ofNullable(packageTypeConfig.get(ENABLE_NUGET_NORMALIZED_VERSION.getKey()))
                .map(Boolean::parseBoolean)
                .ifPresent(this::setEnableNormalizedVersion);
        setForceNugetAuthentication(
                Boolean.parseBoolean(packageTypeConfig.get("forceNugetAuthentication")));
        setNuget(configuration);
    }

    private void setHelmTypeConfig(Map<String, String> packageTypeConfig) {
        HelmConfiguration configuration = new HelmConfiguration();
        configuration
                .setChartsBaseUrl(packageTypeConfig.get("chartsBaseUrl"));
        setHelm(configuration);
        setExternalDependencies(externalDependenciesToDescriptor(packageTypeConfig));
    }

    private void setOciTypeConfig(Map<String, String> packageTypeConfig) {
        Optional.ofNullable(packageTypeConfig.get("dockerApiVersion"))
                .ifPresent(this::setDockerApiVersion);
        setBlockPushingSchema1(Boolean.parseBoolean(packageTypeConfig.get("blockPushingSchema1")));
        Optional.ofNullable(packageTypeConfig.get("maxUniqueTags"))
                .map(Integer::parseInt)
                .ifPresent(this::setMaxUniqueTags);
        Optional.ofNullable(packageTypeConfig.get("dockerProjectId"))
                .ifPresent(this::setDockerProjectId);
        setExternalDependencies(externalDependenciesToDescriptor(packageTypeConfig));
    }

    private void setComposerTypeConfig(Map<String, String> packageTypeConfig) {
        ComposerConfiguration composerConfiguration = new ComposerConfiguration();
        composerConfiguration.setComposerRegistryUrl(packageTypeConfig.get("composerRegistryUrl"));
        if (packageTypeConfig.containsKey(VCS_TYPE)) {
            VcsConfiguration vcs = new VcsConfiguration();
            vcs.setGit(vcsGitConfigurationToDescriptor(packageTypeConfig));
            vcs.setType(VcsType.valueOf(packageTypeConfig.get(VCS_TYPE)));
            setVcs(vcs);
        }
        setComposer(composerConfiguration);
    }

    private void setHexTypeConfig(Map<String, String> packageTypeConfig) {
        HexConfiguration hexConfiguration = new HexConfiguration();
        Optional.ofNullable(packageTypeConfig.get(HEX_REGISTRY_URL.getKey()))
                .ifPresent(hexConfiguration::setHexRepositoryUrl);
        Optional.ofNullable(packageTypeConfig.get(HEX_REGISTRY_PUBLIC_KEY.getKey()))
                .ifPresent(hexConfiguration::setHexPublicKey);
        setHex(hexConfiguration);
    }

    private void setHuggingFaceMLTypeConfig(Map<String, String> packageTypeConfig) {
        HuggingFaceMLRemoteConfiguration hfConfiguration = new HuggingFaceMLRemoteConfiguration();
        Optional.ofNullable(packageTypeConfig.get(HUGGINGFACE_ML_REPO_LAYOUT.getKey()))
                .map(Boolean::parseBoolean)
                .ifPresent(hfConfiguration::setMlRepoLayout);
        setHuggingFace(hfConfiguration);
    }

    private void setAnsibleTypeConfig(Map<String, String> packageTypeConfig) {
        AnsibleRemoteConfiguration ansibleConfig = new AnsibleRemoteConfiguration();
        Optional.ofNullable(packageTypeConfig.get(AUTH_URL.getKey()))
                .ifPresent(ansibleConfig::setAuthUrl);
        setAnsible(ansibleConfig);
    }

    private void setPodsTypeConfig(Map<String, String> packageTypeConfig) {
        CocoaPodsConfiguration cocoaPodsConfiguration = new CocoaPodsConfiguration();
        updatePodsSpecsRepoDownload(packageTypeConfig, cocoaPodsConfiguration);
        Optional.ofNullable(packageTypeConfig.get("cocoaPodsForceModifyIndex"))
                .ifPresent(r -> cocoaPodsConfiguration.setCocoaPodsForceModifyIndex(Boolean.parseBoolean(r)));
        cocoaPodsConfiguration.setCocoaPodsSpecsRepoUrl(packageTypeConfig.get("cocoaPodsSpecsRepoUrl"));

        String cocoapodsCdnUrl = packageTypeConfig.get(COCOA_PODS_CDN_URL.getKey());
        if (cocoapodsCdnUrl != null) {
            cocoaPodsConfiguration.setCocoaPodsCdnUrl(cocoapodsCdnUrl);
        }

        setCocoaPods(cocoaPodsConfiguration);
        if (packageTypeConfig.containsKey(VCS_TYPE)) {
            VcsConfiguration vcs = new VcsConfiguration();
            vcs.setGit(vcsGitConfigurationToDescriptor(packageTypeConfig));
            vcs.setType(VcsType.valueOf(packageTypeConfig.get(VCS_TYPE)));
            setVcs(vcs);
        }
        setExternalDependencies(externalDependenciesToDescriptor(packageTypeConfig));
    }

    private void setCargoTypeConfig(Map<String, String> packageTypeConfig) {
        CargoConfiguration cargoConfiguration = new CargoConfiguration();
        cargoConfiguration
                .setCargoAnonymousAccess(Boolean.parseBoolean(packageTypeConfig.get("cargoAnonymousAccess")));
        cargoConfiguration
                .setCargoInternalIndex(Boolean.parseBoolean(packageTypeConfig.get("cargoInternalIndex")));
        setCargoConfig(cargoConfiguration);
        Optional.ofNullable(packageTypeConfig.get("gitRegistryUrl")).ifPresent(url -> {
            GitConfiguration git = new GitConfiguration();
            git.setRegistryUrl(url);
            setGit(git);
        });
    }

    private static void updatePodsSpecsRepoDownload(Map<String, String> packageTypeConfig,
            CocoaPodsConfiguration cocoaPodsConfiguration) {
        Optional.ofNullable(packageTypeConfig.get("specsRepoDownloadProvider"))
                .ifPresent(specsRepoDownloadProvider -> {
                    VcsGitConfiguration specRepoProvider = new VcsGitConfiguration();
                    Optional.ofNullable(packageTypeConfig.get("specsRepoDownloadUrl"))
                            .ifPresent(specRepoProvider::setDownloadUrl);
                    specRepoProvider.setProvider(
                            VcsGitProvider.valueOf(specsRepoDownloadProvider));
                    cocoaPodsConfiguration.setSpecRepoProvider(specRepoProvider);
                });
    }

    void buildPackageTypeConfig(RepoType packageType,
            Map<String, String> packageTypeConfig) {
        if (packageType.isMavenGroup()) {
            buildMavenTypeConfig(packageType, packageTypeConfig);
        } else if (packageType.isDockerGroup()) {
            buildOciTypeConfig(packageTypeConfig);
        } else {
            switch (packageType) {
                case Debian -> buildDebianTypeConfig(packageTypeConfig);
                case Cargo -> buildCargoTypeConfig(packageTypeConfig);
                case Bower -> buildBowerTypeConfig(packageTypeConfig);
                case CocoaPods -> buildPodsTypeConfig(packageTypeConfig);
                case Composer -> buildComposerTypeConfig(packageTypeConfig);
                case Swift -> buildSwiftTypeConfig(packageTypeConfig);
                case Helm -> buildHelmTypeConfig(packageTypeConfig);
                case NuGet -> buildNugetTypeConfig(packageTypeConfig);
                case Pypi -> buildPypiTypeConfig(packageTypeConfig);
                case VCS -> buildVcsTypeConfig(packageTypeConfig);
                case Terraform -> buildTerraformTypeConfig(packageTypeConfig);
                case Go -> buildGoTypeConfig(packageTypeConfig);
                case Conan -> buildConanTypeConfig(packageTypeConfig);
                case Hex -> buildHexTypeConfig(packageTypeConfig);
                case HuggingFaceML -> buildHuggingFaceMLTypeConfig(packageTypeConfig);
                case Ansible -> buildAnsibleTypeConfig(packageTypeConfig);
                case AIEditorExtensions -> buildAIEditorExtensionsTypeConfig(packageTypeConfig);
                default -> log.trace("No build package type config set for {}", packageType);
            }
        }
    }

    private void buildConanTypeConfig(Map<String, String> packageTypeConfig) {
        packageTypeConfig.put("forceConanAuthentication", String.valueOf(isForceConanAuthentication()));
    }

    private void buildSwiftTypeConfig(Map<String, String> packageTypeConfig) {
        externalDependenciesToConfig(packageTypeConfig, getExternalDependencies());
    }

    private void buildAIEditorExtensionsTypeConfig(Map<String, String> packageTypeConfig) {
        externalDependenciesToConfig(packageTypeConfig, getExternalDependencies());
    }

    private void buildDebianTypeConfig(Map<String, String> packageTypeConfig) {
        packageTypeConfig.put("ddebSupported", String.valueOf(isDdebSupported()));
    }

    private void buildGoTypeConfig(Map<String, String> packageTypeConfig) {
        if (getVcs() != null) {
            packageTypeConfig.put(VCS_TYPE, getVcs().getType().name());
            packageTypeConfig.put(VCS_GIT_PROVIDER, getVcs().getGit().getProvider().name());
            Optional.ofNullable(getVcs().getGit().getDownloadUrl())
                    .ifPresent(value -> packageTypeConfig.put(VCS_GIT_DOWNLOAD_URL, value));
        }
        externalDependenciesToConfig(packageTypeConfig, getExternalDependencies());
        if (getGo() != null) {
            packageTypeConfig.put(GITLAB_RESOLVE_SUBGROUPS.getKey(),
                    String.valueOf(getGo().isGitLabResolveSubgroups()));
        }
    }

    private void buildTerraformTypeConfig(Map<String, String> packageTypeConfig) {
        if (getTerraform() != null) {
            Optional.ofNullable(getTerraform().getTerraformProvidersUrl())
                    .ifPresent(value -> packageTypeConfig.put("terraformProvidersUrl", value));
            Optional.ofNullable(getTerraform().getTerraformRegistryUrl())
                    .ifPresent(value -> packageTypeConfig.put("terraformRegistryUrl", value));
            if (getVcs() != null) {
                packageTypeConfig.put(VCS_TYPE, getVcs().getType().name());
                packageTypeConfig.put(VCS_GIT_PROVIDER, getVcs().getGit().getProvider().name());
                Optional.ofNullable(getVcs().getGit().getDownloadUrl())
                        .ifPresent(value -> packageTypeConfig.put(VCS_GIT_DOWNLOAD_URL, value));
            }
            externalDependenciesToConfig(packageTypeConfig, getExternalDependencies());
        }
    }

    private void buildVcsTypeConfig(Map<String, String> packageTypeConfig) {
        if (getVcs() != null) {
            if (getVcs().getGit() != null) {
                packageTypeConfig.put(VCS_GIT_PROVIDER, getVcs().getGit().getProvider().name());
                Optional.ofNullable(getVcs().getGit().getDownloadUrl())
                        .ifPresent(value -> packageTypeConfig.put(VCS_GIT_DOWNLOAD_URL, value));
            }
            packageTypeConfig.put(VCS_TYPE, getVcs().getType().name());
        }
    }

    private void buildPypiTypeConfig(Map<String, String> packageTypeConfig) {
        if (getPypi() != null) {
            Optional.ofNullable(getPypi().getPyPIRegistryUrl())
                    .ifPresent(value -> packageTypeConfig.put("pyPIRegistryUrl", value));
            Optional.ofNullable(getPypi().getPackagesContextPath())
                    .ifPresent(value -> packageTypeConfig.put("packagesContextPath", value));
            Optional.ofNullable(getPypi().getRepositorySuffix())
                    .ifPresent(value -> packageTypeConfig.put("repositorySuffix", value));
            Optional.ofNullable(getPypi().getIndexContextPath())
                    .ifPresent(value -> packageTypeConfig.put("indexContextPath", value));
        }
    }

    private void buildHexTypeConfig(Map<String, String> packageTypeConfig) {
        if (null != getHex()) {
            Optional.ofNullable(getHex().getHexPublicKey()).ifPresent(
                    value -> packageTypeConfig.put(HEX_REGISTRY_PUBLIC_KEY.getKey(), value));
            Optional.ofNullable(getHex().getHexRepositoryUrl()).ifPresent(
                    value -> packageTypeConfig.put(HEX_REGISTRY_URL.getKey(), value));
        }
    }

    private void buildHuggingFaceMLTypeConfig(Map<String, String> packageTypeConfig) {
        if (null != getHuggingFace()) {
            Optional.ofNullable(getHuggingFace().isMlRepoLayout()).ifPresent(
                    value -> packageTypeConfig.put(HUGGINGFACE_ML_REPO_LAYOUT.getKey(), String.valueOf(value)));
        }
    }

    private void buildAnsibleTypeConfig(Map<String, String> packageTypeConfig) {
        if (getAnsible() != null) {
            Optional.ofNullable(getAnsible().getAuthUrl())
                    .ifPresent(value -> packageTypeConfig.put(AUTH_URL.getKey(), value));
        }
    }

    private void buildNugetTypeConfig(Map<String, String> packageTypeConfig) {
        if (getNuget() != null) {
            Optional.ofNullable(getNuget().getV3FeedUrl())
                    .ifPresent(value -> packageTypeConfig.put("v3FeedUrl", value));
            Optional.ofNullable(getNuget().getFeedContextPath())
                    .ifPresent(value -> packageTypeConfig.put("feedContextPath", value));
            Optional.ofNullable(getNuget().getDownloadContextPath())
                    .ifPresent(value -> packageTypeConfig.put("downloadContextPath", value));
            Optional.ofNullable(getNuget().getSymbolServerUrl())
                    .ifPresent(value -> packageTypeConfig.put("symbolServerUrl", value));
        }
        packageTypeConfig.put("forceNugetAuthentication", String.valueOf(isForceNugetAuthentication()));
        packageTypeConfig.put(ENABLE_NUGET_NORMALIZED_VERSION.getKey(), String.valueOf(isEnableNormalizedVersion()));
    }

    private void buildHelmTypeConfig(Map<String, String> packageTypeConfig) {
        HelmConfiguration helmConfig = getHelm();
        if (helmConfig != null && helmConfig.getChartsBaseUrl() != null) {
            packageTypeConfig.put("chartsBaseUrl", String.valueOf(getHelm().getChartsBaseUrl()));
        }
        externalDependenciesToConfig(packageTypeConfig, getExternalDependencies());
    }

    private void buildOciTypeConfig(Map<String, String> packageTypeConfig) {
        packageTypeConfig.put("dockerApiVersion", getDockerApiVersion().name());
        packageTypeConfig.put("blockPushingSchema1", String.valueOf(isBlockPushingSchema1()));
        packageTypeConfig.put("maxUniqueTags", String.valueOf(getMaxUniqueTags()));
        packageTypeConfig.put("dockerProjectId", getDockerProjectId());
        externalDependenciesToConfig(packageTypeConfig, getExternalDependencies());
    }

    private void buildComposerTypeConfig(Map<String, String> packageTypeConfig) {
        if (getComposer() != null) {
            packageTypeConfig.put("composerRegistryUrl", getComposer().getComposerRegistryUrl());
        }
        if (getVcs() != null) {
            packageTypeConfig.put(VCS_TYPE, getVcs().getType().name());
            packageTypeConfig.put(VCS_GIT_PROVIDER, getVcs().getGit().getProvider().name());
            if (getVcs().getGit().getDownloadUrl() != null) {
                packageTypeConfig.put(VCS_GIT_DOWNLOAD_URL, getVcs().getGit().getDownloadUrl());
            }
        }
    }

    private void buildBowerTypeConfig(Map<String, String> packageTypeConfig) {
        if (getBower() != null) {
            packageTypeConfig.put("bowerRegistryUrl", getBower().getBowerRegistryUrl());
        }
        if (getVcs() != null) {
            packageTypeConfig.put(VCS_TYPE, getVcs().getType().name());
            packageTypeConfig.put(VCS_GIT_PROVIDER, getVcs().getGit().getProvider().name());
            if (getVcs().getGit().getDownloadUrl() != null) {
                packageTypeConfig.put(VCS_GIT_DOWNLOAD_URL, getVcs().getGit().getDownloadUrl());
            }
        }
    }

    private void buildCargoTypeConfig(Map<String, String> packageTypeConfig) {
        packageTypeConfig.put("cargoAnonymousAccess", String.valueOf(isCargoAnonymousAccess()));
        packageTypeConfig.put("cargoInternalIndex", String.valueOf(isCargoInternalIndex()));
        if (getGit() != null && getGit().getRegistryUrl() != null) {
            packageTypeConfig.put("gitRegistryUrl", getGit().getRegistryUrl());
        }
    }

    private void buildMavenTypeConfig(RepoType packageType, Map<String, String> packageTypeConfig) {
        packageTypeConfig.put("suppressPomConsistencyChecks", String.valueOf(isSuppressPomConsistencyChecks()));
        packageTypeConfig.put("fetchJarsEagerly", String.valueOf(isFetchJarsEagerly()));
        packageTypeConfig.put("fetchSourcesEagerly", String.valueOf(isFetchSourcesEagerly()));
        packageTypeConfig.put("rejectInvalidJars", String.valueOf(isRejectInvalidJars()));
        if (RepoType.P2.equals(packageType) && getP2OriginalUrl() != null) {
            packageTypeConfig.put("p2OriginalUrl", getP2OriginalUrl());
            packageTypeConfig.put("forceP2Authentication", String.valueOf(isForceP2Authentication()));
        }
    }

    private void buildPodsTypeConfig(Map<String, String> packageTypeConfig) {
        CocoaPodsConfiguration cocoaPodsConfiguration = getCocoaPods();
        if (cocoaPodsConfiguration != null) {
            packageTypeConfig.put("cocoaPodsSpecsRepoUrl", cocoaPodsConfiguration.getCocoaPodsSpecsRepoUrl());
            VcsGitConfiguration specRepoProvider = cocoaPodsConfiguration.getSpecRepoProvider();
            if (specRepoProvider != null) {
                if (specRepoProvider.getDownloadUrl() != null) {
                    packageTypeConfig.put("specsRepoDownloadUrl", specRepoProvider.getDownloadUrl());
                }
                packageTypeConfig.put("specsRepoDownloadProvider", specRepoProvider.getProvider().name());
            }
            packageTypeConfig.put(COCOA_PODS_CDN_URL.getKey(),
                    cocoaPodsConfiguration.getCocoaPodsCdnUrl());
            packageTypeConfig.put(COCOA_PODS_FORCE_MODIFY_INDEX.getKey(),
                    String.valueOf(cocoaPodsConfiguration.isCocoaPodsForceModifyIndex()));
        }
        if (getVcs() != null) {
            packageTypeConfig.put(VCS_TYPE, getVcs().getType().name());
            packageTypeConfig.put(VCS_GIT_PROVIDER, getVcs().getGit().getProvider().name());
            if (getVcs().getGit().getDownloadUrl() != null) {
                packageTypeConfig.put(VCS_GIT_DOWNLOAD_URL, getVcs().getGit().getDownloadUrl());
            }
        }
        externalDependenciesToConfig(packageTypeConfig, getExternalDependencies());
    }

    private void setSecurityConfig(SecurityConfig securityConfig,
            List<KeyPairDescriptor> keyPairs) {
        if (securityConfig.getXrayConfig() != null) {
            XrayRepoConfigDescriptor xrayRepoConfig = new XrayRepoConfigDescriptor();
            xrayRepoConfig.setDataTtl(securityConfig.getXrayConfig().getDataTtl());
            xrayRepoConfig.setEnabled(securityConfig.getXrayConfig().isEnabled());
            xrayRepoConfig.setXrayNotAvailableMode(securityConfig.getXrayConfig().getXrayNotAvailableMode());
            setXrayConfig(xrayRepoConfig);
        }
        setSignedUrlTtl(securityConfig.getSignedUrlTtl());

        for (KeyPairDescriptor keyPair : keyPairs) {
            if (keyPair.getKey().equals(securityConfig.getPrimaryKeyPairRef())) {
                setPrimaryKeyPairDescriptor(keyPair);
            }
            if (keyPair.getKey().equals(securityConfig.getSecondaryKeyPairRef())) {
                setSecondaryKeyPairDescriptor(keyPair);
            }
        }
    }

    private void buildSecurityConfig(@Nonnull SecurityConfig securityConfig) {
        if (getXrayConfig() != null) {
            org.artifactory.model.XrayRepoConfig xrayConfig =
                    new org.artifactory.model.XrayRepoConfig();
            XrayRepoConfigDescriptor xrayConfigDescriptor = getXrayConfig();
            xrayConfig.setDataTtl(xrayConfigDescriptor.getDataTtl());
            xrayConfig.setEnabled(xrayConfigDescriptor.isEnabled());
            xrayConfig.setXrayNotAvailableMode(xrayConfigDescriptor.getXrayNotAvailableMode());
            securityConfig.setXrayConfig(xrayConfig);
        }
        if (getPrimaryKeyPairDescriptor() != null) {
            securityConfig.setPrimaryKeyPairRef(getPrimaryKeyPairDescriptor().getKey());
        }
        if (getSecondaryKeyPairDescriptor() != null) {
            securityConfig.setSecondaryKeyPairRef(getSecondaryKeyPairDescriptor().getKey());
        }
        securityConfig.setSignedUrlTtl(getSignedUrlTtl());
    }

    @Nonnull
    private VcsGitConfiguration vcsGitConfigurationToDescriptor(Map<String, String> packageTypeConfig) {
        VcsGitConfiguration vcsGitConfiguration = new VcsGitConfiguration();
        Optional.ofNullable(packageTypeConfig.get(VCS_GIT_PROVIDER))
                .ifPresent(provider -> vcsGitConfiguration.setProvider(VcsGitProvider.valueOf(provider)));
        Optional.ofNullable(packageTypeConfig.get(VCS_GIT_DOWNLOAD_URL))
                .ifPresent(vcsGitConfiguration::setDownloadUrl);
        return vcsGitConfiguration;
    }

    private ExternalDependenciesConfig externalDependenciesToDescriptor(Map<String, String> packageTypeConfig) {
        ExternalDependenciesConfig externalDependenciesConfig = null;
        if (packageTypeConfig.containsKey(EXTERNAL_DEPENDENCIES_ENABLED)) {
            externalDependenciesConfig = new ExternalDependenciesConfig();
            externalDependenciesConfig.setEnabled(
                    Boolean.parseBoolean(packageTypeConfig.get(EXTERNAL_DEPENDENCIES_ENABLED)));
            List<String> patterns =
                    RepositoryConfigSource.jsonStringToList(packageTypeConfig, EXTERNAL_DEPENDENCIES_PATTERNS);
            externalDependenciesConfig.setPatterns(patterns);
        }
        return externalDependenciesConfig;
    }

    private void externalDependenciesToConfig(Map<String, String> packageTypeConfig,
            ExternalDependenciesConfig externalDependencies) {
        if (externalDependencies != null) {
            packageTypeConfig.put(EXTERNAL_DEPENDENCIES_ENABLED, String.valueOf(externalDependencies.isEnabled()));
            RepositoryConfigSource.entityToFlatMapKeys(externalDependencies.getPatterns(),
                    "externalDependenciesPatterns", packageTypeConfig);
        } else {
            packageTypeConfig.remove(EXTERNAL_DEPENDENCIES_ENABLED);
            packageTypeConfig.remove(EXTERNAL_DEPENDENCIES_PATTERNS);
            packageTypeConfig.remove("externalDependenciesRemoteRepoRef");
        }
    }
}