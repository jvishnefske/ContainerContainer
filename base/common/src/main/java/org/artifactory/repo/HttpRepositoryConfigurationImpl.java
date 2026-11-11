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

package org.artifactory.repo;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.packagetype.configuration.AnsibleConfiguration;
import org.artifactory.repo.packagetype.configuration.BowerConfiguration;
import org.artifactory.repo.packagetype.configuration.CargoConfiguration;
import org.artifactory.repo.packagetype.configuration.CocoaPodsConfiguration;
import org.artifactory.repo.packagetype.configuration.ComposerConfiguration;
import org.artifactory.repo.packagetype.configuration.GitConfiguration;
import org.artifactory.repo.packagetype.configuration.GoConfiguration;
import org.artifactory.repo.packagetype.configuration.HelmConfiguration;
import org.artifactory.repo.packagetype.configuration.HexConfiguration;
import org.artifactory.repo.packagetype.configuration.HuggingFaceMLConfiguration;
import org.artifactory.repo.packagetype.configuration.NuGetConfiguration;
import org.artifactory.repo.packagetype.configuration.PypiConfiguration;
import org.artifactory.repo.packagetype.configuration.TerraformConfiguration;
import org.artifactory.repo.packagetype.configuration.VcsConfiguration;
import org.artifactory.repo.packagetype.configuration.VcsGitConfiguration;
import org.artifactory.repo.remote.ContentSynchronisation;
import org.artifactory.repo.remote.CustomHttpHeader;
import org.artifactory.repo.vcs.VcsGitProvider;
import org.artifactory.repo.vcs.VcsType;
import org.artifactory.util.AnsiblePackageTypeConfigMapper;
import org.artifactory.util.BowerPackageTypeConfigMapper;
import org.artifactory.util.CargoPackageTypeConfigMapper;
import org.artifactory.util.CocoaPodsPackageTypeConfigMapper;
import org.artifactory.util.ComposerPackageTypeConfigMapper;
import org.artifactory.util.GitPackageTypeConfigMapper;
import org.artifactory.util.GoPackageTypeConfigMapper;
import org.artifactory.util.HelmPackageTypeConfigMapper;
import org.artifactory.util.HexPackageTypeConfigMapper;
import org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper;
import org.artifactory.util.NuGetPackageTypeConfigMapper;
import org.artifactory.util.PackageTypeConfigFields;
import org.artifactory.util.PypiPackageTypeConfigMapper;
import org.artifactory.util.RepoLayoutUtils;
import org.artifactory.util.TerraformPackageTypeConfigMapper;
import org.artifactory.util.VcsPackageTypeConfigMapper;
import org.jfrog.common.JsonUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.artifactory.descriptor.repo.RepoType.Ansible;
import static org.artifactory.descriptor.repo.RepoType.Bower;
import static org.artifactory.descriptor.repo.RepoType.Cargo;
import static org.artifactory.descriptor.repo.RepoType.CocoaPods;
import static org.artifactory.descriptor.repo.RepoType.Composer;
import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.descriptor.repo.RepoType.Go;
import static org.artifactory.descriptor.repo.RepoType.Gradle;
import static org.artifactory.descriptor.repo.RepoType.Helm;
import static org.artifactory.descriptor.repo.RepoType.HelmOCI;
import static org.artifactory.descriptor.repo.RepoType.Hex;
import static org.artifactory.descriptor.repo.RepoType.HuggingFaceML;
import static org.artifactory.descriptor.repo.RepoType.Ivy;
import static org.artifactory.descriptor.repo.RepoType.Maven;
import static org.artifactory.descriptor.repo.RepoType.NuGet;
import static org.artifactory.descriptor.repo.RepoType.OCI;
import static org.artifactory.descriptor.repo.RepoType.Pypi;
import static org.artifactory.descriptor.repo.RepoType.SBT;
import static org.artifactory.descriptor.repo.RepoType.Swift;
import static org.artifactory.descriptor.repo.RepoType.Terraform;
import static org.artifactory.descriptor.repo.RepoType.VCS;
import static org.artifactory.descriptor.repo.RepoType.AIEditorExtensions;
import static org.artifactory.descriptor.repo.XrayRepoConfigDescriptor.DEFAULT_DATA_TTL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_METADATA_RETRIEVAL_TIMEOUT_SECS;
import static org.artifactory.util.PackageTypeConfigFields.AUTH_URL;

import static org.artifactory.util.PackageTypeConfigFields.BOWER_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.CHARTS_BASE_URL;
import static org.artifactory.util.PackageTypeConfigFields.COCOA_PODS_CDN_URL;
import static org.artifactory.util.PackageTypeConfigFields.COCOA_PODS_FORCE_MODIFY_INDEX;
import static org.artifactory.util.PackageTypeConfigFields.COCOA_PODS_SPECS_URL;
import static org.artifactory.util.PackageTypeConfigFields.COMPOSER_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_PROJECT_ID;
import static org.artifactory.util.PackageTypeConfigFields.DOWNLOAD_CONTEXT_PATH;
import static org.artifactory.util.PackageTypeConfigFields.EXTERNAL_DEPENDENCIES_ENABLED;
import static org.artifactory.util.PackageTypeConfigFields.EXTERNAL_DEPENDENCIES_PATTERNS;
import static org.artifactory.util.PackageTypeConfigFields.FEED_CONTEXT_PATH;
import static org.artifactory.util.PackageTypeConfigFields.FETCH_JARS_EAGERLY;
import static org.artifactory.util.PackageTypeConfigFields.FETCH_SOURCES_EAGERLY;
import static org.artifactory.util.PackageTypeConfigFields.GITLAB_RESOLVE_SUBGROUPS;
import static org.artifactory.util.PackageTypeConfigFields.GIT_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.HEX_REGISTRY_PUBLIC_KEY;
import static org.artifactory.util.PackageTypeConfigFields.HUGGINGFACE_ML_REPO_LAYOUT;
import static org.artifactory.util.PackageTypeConfigFields.MAX_UNIQUE_TAGS;
import static org.artifactory.util.PackageTypeConfigFields.PYPI_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.PYPI_REPOSITORY_SUFFIX;
import static org.artifactory.util.PackageTypeConfigFields.REJECT_INVALID_JARS;
import static org.artifactory.util.PackageTypeConfigFields.SPECS_REPO_DOWNLOAD_PROVIDER;
import static org.artifactory.util.PackageTypeConfigFields.SUPPRESS_POM_CONSISTENCY_CHECKS;
import static org.artifactory.util.PackageTypeConfigFields.SYMBOL_SERVER_URL;
import static org.artifactory.util.PackageTypeConfigFields.TERRAFORM_PROVIDERS_URL;
import static org.artifactory.util.PackageTypeConfigFields.TERRAFORM_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.V3_FEED_URL;
import static org.artifactory.util.PackageTypeConfigFields.VCS_GIT_DOWNLOAD_URL;
import static org.artifactory.util.PackageTypeConfigFields.VCS_GIT_PROVIDER;
import static org.artifactory.util.PackageTypeConfigFields.VCS_TYPE;


/**
 * Remote Repository configuration
 *
 * @author Tomer Cohen
 * @see org.artifactory.descriptor.repo.HttpRepoDescriptor
 */
@SuppressWarnings("unused") // setters for jackson
@JsonFilter("typeSpecificFilter")
public class HttpRepositoryConfigurationImpl extends RepositoryConfigurationBase
        implements HttpRepositoryConfiguration {
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private String url;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private String username = "";
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private String password = "";
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private String proxy;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean disableProxy;
    @Setter
    @Getter
    @IncludeTypeSpecific(packageType = {Maven, Gradle, Ivy, SBT}, repoType = REMOTE)
    private boolean handleReleases = true;
    @Setter
    @Getter
    @IncludeTypeSpecific(packageType = {Maven, Gradle, Ivy, SBT}, repoType = REMOTE)
    private boolean handleSnapshots = true;
    @Setter
    @Getter
    @IncludeTypeSpecific(packageType = {Maven, Gradle, Ivy, SBT}, repoType = REMOTE)
    private boolean suppressPomConsistencyChecks = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(packageType = {Maven, Gradle, Ivy, SBT}, repoType = REMOTE)
    private String remoteRepoChecksumPolicyType = "";
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean hardFail = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean offline = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean blackedOut = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean storeArtifactsLocally = true;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private int socketTimeoutMillis = 15000;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private String localAddress = "";
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private long retrievalCachePeriodSecs = 7200L;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private long assumedOfflinePeriodSecs = 300L;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private long missedRetrievalCachePeriodSecs = 1800L;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private long metadataRetrievalTimeoutSecs = DEFAULT_METADATA_RETRIEVAL_TIMEOUT_SECS;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private int unusedArtifactsCleanupPeriodHours = 0;
    @Setter
    @Getter
    @IncludeTypeSpecific(packageType = {Maven, Gradle, Ivy, SBT}, repoType = REMOTE)
    private boolean fetchJarsEagerly = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(packageType = {Maven, Gradle, Ivy, SBT}, repoType = REMOTE)
    private boolean fetchSourcesEagerly = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean shareConfiguration = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean synchronizeProperties = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(packageType = {Maven, VCS}, repoType = REMOTE)
    private int maxUniqueSnapshots = 0;
    @Setter
    @Getter
    private int maxUniqueTags = 0;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private List<String> propertySets;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private String remoteRepoLayoutRef;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean archiveBrowsingEnabled = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean listRemoteFolderItems = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(packageType = {Maven, Gradle, Ivy, SBT}, repoType = REMOTE)
    private boolean rejectInvalidJars = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean allowAnyHostAuth = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean enableCookieManagement = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(packageType = {Docker, OCI, HelmOCI}, repoType = REMOTE)
    private boolean enableTokenAuthentication = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private String queryParams;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean propagateQueryParams = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean blockMismatchingMimeTypes = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private String mismatchingMimeTypesOverrideList;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private String clientTlsCertificate;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean bypassHeadRequests = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean disableUrlNormalization = false;
    private BowerConfiguration bowerConfiguration = new BowerConfiguration();
    private PypiConfiguration pyPIConfiguration = new PypiConfiguration();
    private CocoaPodsConfiguration podsConfiguration = new CocoaPodsConfiguration();
    private ComposerConfiguration composerConfiguration = new ComposerConfiguration();
    private TerraformConfiguration terraformConfiguration = new TerraformConfiguration();
    private VcsConfiguration vcsConfiguration = new VcsConfiguration();
    private HelmConfiguration helmConfiguration = new HelmConfiguration();
    private HuggingFaceMLConfiguration huggingFaceConfiguration = new HuggingFaceMLConfiguration();
    @IncludeTypeSpecific(repoType = REMOTE)
    private HexConfiguration hexConfiguration = new HexConfiguration();
    @IncludeTypeSpecific(repoType = REMOTE)
    private GoConfiguration goConfiguration = new GoConfiguration();
    private GitConfiguration gitConfiguration = new GitConfiguration();
    private AnsibleConfiguration ansibleConfiguration = new AnsibleConfiguration();
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private ContentSynchronisation contentSynchronisation;
    private NuGetConfiguration nuGetConfiguration = new NuGetConfiguration();
    @Setter
    @Getter
    @IncludeTypeSpecific(packageType = {Bower, Docker, OCI, CocoaPods, Swift, HelmOCI, Helm, Terraform,
            AIEditorExtensions},
            repoType = REMOTE)
    private boolean externalDependenciesEnabled = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(packageType = {Docker, OCI, CocoaPods, Swift, HelmOCI, Helm, Terraform, AIEditorExtensions},
            repoType = REMOTE)
    private List<String> externalDependenciesPatterns;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean sendContext = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean passThrough = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean curated = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private boolean retrieveSha256FromServer = false;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = REMOTE)
    private List<CustomHttpHeader> customHttpHeaders;

    public HttpRepositoryConfigurationImpl() {
        setRepoLayoutRef(RepoLayoutUtils.MAVEN_2_DEFAULT_NAME);
    }

    public HttpRepositoryConfigurationImpl(RemoteRepoConfig remoteRepoConfig) {
        super(remoteRepoConfig, TYPE);

        setRepoTypeConfig(remoteRepoConfig);
        setPackageTypeConfig(remoteRepoConfig);
    }

    private void setRepoTypeConfig(RemoteRepoConfig remoteRepoConfig) {
        try {
            new URL(remoteRepoConfig.getRepoTypeConfig().getUrl());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(
                    "Remote URL '" + remoteRepoConfig.getRepoTypeConfig().getUrl() + "' is not valid", e);
        }
        this.url = remoteRepoConfig.getRepoTypeConfig().getUrl();
        String localUsername = remoteRepoConfig.getRepoTypeConfig().getUsername();
        if (StringUtils.isNotBlank(localUsername)) {
            setUsername(localUsername);
        }
        String localPassword = remoteRepoConfig.getRepoTypeConfig().getPassword();
        if (StringUtils.isNotBlank(localPassword)) {
            setPassword(localPassword);
        }
        setAllowAnyHostAuth(remoteRepoConfig.getRepoTypeConfig().isAllowAnyHostAuth());
        setEnableTokenAuthentication(remoteRepoConfig.getRepoTypeConfig().isEnableTokenAuthentication());

        if (!remoteRepoConfig.getRepoTypeConfig().isDisableProxy() &&
                remoteRepoConfig.getRepoTypeConfig().getProxyRef() != null) {
            setProxy(remoteRepoConfig.getRepoTypeConfig().getProxyRef());
        }
        setDisableProxy(remoteRepoConfig.getRepoTypeConfig().isDisableProxy());

        setRemoteRepoChecksumPolicyType(remoteRepoConfig.getRepoTypeConfig().getChecksumPolicyType().getMessage());
        setHardFail(remoteRepoConfig.getRepoTypeConfig().isHardFail());
        setOffline(remoteRepoConfig.getRepoTypeConfig().isOffline());
        setStoreArtifactsLocally(remoteRepoConfig.getRepoTypeConfig().isStoreArtifactsLocally());
        setSocketTimeoutMillis(remoteRepoConfig.getRepoTypeConfig().getSocketTimeoutMillis());
        String address = remoteRepoConfig.getRepoTypeConfig().getLocalAddress();
        if (StringUtils.isNotBlank(address)) {
            setLocalAddress(address);
        }
        setEnableCookieManagement(remoteRepoConfig.getRepoTypeConfig().isEnableCookieManagement());
        setBlackedOut(remoteRepoConfig.getRepoTypeConfig().isBlackedOut());
        setHandleReleases(remoteRepoConfig.getRepoTypeConfig().isHandleReleases());
        setHandleSnapshots(remoteRepoConfig.getRepoTypeConfig().isHandleSnapshots());
        setRetrievalCachePeriodSecs(remoteRepoConfig.getRepoTypeConfig().getRetrievalCachePeriodSecs());
        setAssumedOfflinePeriodSecs(remoteRepoConfig.getRepoTypeConfig().getAssumedOfflinePeriodSecs());
        setMissedRetrievalCachePeriodSecs(remoteRepoConfig.getRepoTypeConfig().getMissedRetrievalCachePeriodSecs());
        setMetadataRetrievalTimeoutSecs(remoteRepoConfig.getRepoTypeConfig().getMetadataRetrievalTimeoutSecs());
        setUnusedArtifactsCleanupPeriodHours(
                remoteRepoConfig.getRepoTypeConfig().getUnusedArtifactsCleanupPeriodHours());

        setShareConfiguration(remoteRepoConfig.getRepoTypeConfig().isShareConfiguration());
        setMaxUniqueSnapshots(remoteRepoConfig.getRepoTypeConfig().getMaxUniqueSnapshots());
        setSynchronizeProperties(remoteRepoConfig.getRepoTypeConfig().isSynchronizeProperties());
        setContentSynchronisation(remoteRepoConfig.getRepoTypeConfig().getContentSynchronisation());
        setPropagateQueryParams(remoteRepoConfig.getRepoTypeConfig().isPropagateQueryParams());
        setClientTlsCertificate(remoteRepoConfig.getRepoTypeConfig().getClientTlsCertificate());
        List<String> localPropertySets = remoteRepoConfig.getRepoTypeConfig().getPropertySetRefs();
        if (localPropertySets != null && !localPropertySets.isEmpty()) {
            setPropertySets(localPropertySets);
        } else {
            setPropertySets(Lists.newArrayList());
        }
        String remoteRepoLayout = remoteRepoConfig.getRepoTypeConfig().getRemoteRepoLayoutRef();
        if (remoteRepoLayout != null) {
            setRemoteRepoLayoutRef(remoteRepoLayout);
        }
        setArchiveBrowsingEnabled(remoteRepoConfig.getRepoTypeConfig().isArchiveBrowsingEnabled());
        this.xrayRepoConfig = remoteRepoConfig.getSecurityConfig().getXrayConfig();
        this.downloadRedirectConfig = remoteRepoConfig.getRepoTypeConfig().getDownloadRedirectConfig();
        this.cdnRedirectRepoConfig = remoteRepoConfig.getRepoTypeConfig().getCdnRedirectRepoConfig();
        setPriorityResolution(remoteRepoConfig.getRepoTypeConfig().isPriorityResolution());
        setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        setBlockMismatchingMimeTypes(remoteRepoConfig.getRepoTypeConfig().isBlockMismatchingMimeTypes());
        setMismatchingMimeTypesOverrideList(remoteRepoConfig.getRepoTypeConfig().getMismatchingMimeTypesOverrideList());
        setBypassHeadRequests(remoteRepoConfig.getRepoTypeConfig().isBypassHeadRequests());
        setDisableUrlNormalization(remoteRepoConfig.getRepoTypeConfig().isDisableUrlNormalization());
        setQueryParams(remoteRepoConfig.getRepoTypeConfig().getQueryParams());
        setSendContext(remoteRepoConfig.getRepoTypeConfig().isSendContext());
        setPassThrough(remoteRepoConfig.getRepoTypeConfig().isPassThrough());
        setCurated(remoteRepoConfig.getRepoTypeConfig().isCurated());
        setRetrieveSha256FromServer(remoteRepoConfig.getRepoTypeConfig().isRetrieveSha256FromServer());
        setCustomHttpHeadersFromRepoConfig(remoteRepoConfig.getRepoTypeConfig().getCustomHttpHeaders());
    }

    private void setCustomHttpHeadersFromRepoConfig(List<CustomHttpHeader> headers) {
        if (headers == null || headers.isEmpty()) {
            this.customHttpHeaders = new ArrayList<>();
            return;
        }
        List<CustomHttpHeader> forResponse = headers.stream()
                .map(CustomHttpHeader::copy)
                .collect(Collectors.toCollection(ArrayList::new));
        this.customHttpHeaders = forResponse;
    }

    private void setPackageTypeConfig(RemoteRepoConfig remoteRepoConfig) {
        setMavenConfigIfNeeded(remoteRepoConfig);
        setDockerConfigIfNeeded(remoteRepoConfig);

        this.cargoConfiguration = CargoPackageTypeConfigMapper.INSTANCE.map(remoteRepoConfig.getPackageTypeConfig());
        this.bowerConfiguration = BowerPackageTypeConfigMapper.INSTANCE.map(remoteRepoConfig.getPackageTypeConfig());
        this.podsConfiguration = CocoaPodsPackageTypeConfigMapper.INSTANCE.map(remoteRepoConfig.getPackageTypeConfig());
        this.pyPIConfiguration = PypiPackageTypeConfigMapper.INSTANCE.map(remoteRepoConfig.getPackageTypeConfig());
        this.composerConfiguration =
                ComposerPackageTypeConfigMapper.INSTANCE.map(remoteRepoConfig.getPackageTypeConfig());
        this.terraformConfiguration =
                TerraformPackageTypeConfigMapper.INSTANCE.map(remoteRepoConfig.getPackageTypeConfig());
        this.vcsConfiguration = VcsPackageTypeConfigMapper.INSTANCE.map(remoteRepoConfig.getPackageTypeConfig());
        this.nuGetConfiguration = NuGetPackageTypeConfigMapper.INSTANCE.map(remoteRepoConfig.getPackageTypeConfig());
        this.helmConfiguration = HelmPackageTypeConfigMapper.INSTANCE.map(remoteRepoConfig.getPackageTypeConfig());
        this.huggingFaceConfiguration = HuggingFaceMLPackageTypeConfigMapper.INSTANCE
                .map(remoteRepoConfig.getPackageTypeConfig());
        this.gitConfiguration = GitPackageTypeConfigMapper.INSTANCE.map(remoteRepoConfig.getPackageTypeConfig());
        this.hexConfiguration = HexPackageTypeConfigMapper.INSTANCE.map(remoteRepoConfig.getPackageTypeConfig());
        this.goConfiguration = GoPackageTypeConfigMapper.INSTANCE.map(remoteRepoConfig.getPackageTypeConfig());
        this.ansibleConfiguration =
                AnsiblePackageTypeConfigMapper.INSTANCE.map(remoteRepoConfig.getPackageTypeConfig());

        String dependenciesEnabled =
                remoteRepoConfig.getPackageTypeConfig().get(EXTERNAL_DEPENDENCIES_ENABLED.getKey());
        if (StringUtils.isNotBlank(dependenciesEnabled)) {
            setExternalDependenciesEnabled(Boolean.parseBoolean(dependenciesEnabled));
        }
        String dependenciesPatterns =
                remoteRepoConfig.getPackageTypeConfig().get(EXTERNAL_DEPENDENCIES_PATTERNS.getKey());
        if (StringUtils.isNotBlank(dependenciesPatterns)) {
            setExternalDependenciesPatterns(
                    JsonUtils.getInstance().readValue(dependenciesPatterns, new TypeReference<>() {
                    }));
        }
    }

    private void setDockerConfigIfNeeded(RemoteRepoConfig remoteRepoConfig) {
        if (remoteRepoConfig.getPackageType().isDockerGroup()) {
            String maxUnique = remoteRepoConfig.getPackageTypeConfig().get(MAX_UNIQUE_TAGS.getKey());
            if (StringUtils.isNotBlank(maxUnique)) {
                setMaxUniqueTags(Integer.parseInt(maxUnique));
            }
            String dockerProjectId = remoteRepoConfig.getPackageTypeConfig().get(DOCKER_PROJECT_ID.getKey());
            if (StringUtils.isNotBlank(dockerProjectId)) {
                setDockerProjectId(dockerProjectId);
            }
        }
    }

    private void setMavenConfigIfNeeded(RemoteRepoConfig remoteRepoConfig) {
        if (remoteRepoConfig.getPackageType().isMavenGroup()) {
            String rejectInvalid = remoteRepoConfig.getPackageTypeConfig().get(REJECT_INVALID_JARS.getKey());
            if (StringUtils.isNotBlank(rejectInvalid)) {
                setRejectInvalidJars(Boolean.parseBoolean(rejectInvalid));
            }
            String fetchJars = remoteRepoConfig.getPackageTypeConfig().get(FETCH_JARS_EAGERLY.getKey());
            if (StringUtils.isNotBlank(fetchJars)) {
                setFetchJarsEagerly(Boolean.parseBoolean(fetchJars));
            }
            setSuppressPomConsistencyChecks(Boolean.parseBoolean(
                    remoteRepoConfig.getPackageTypeConfig().get(SUPPRESS_POM_CONSISTENCY_CHECKS.getKey())));
            String fetchSource = remoteRepoConfig.getPackageTypeConfig().get(FETCH_SOURCES_EAGERLY.getKey());
            if (StringUtils.isNotBlank(fetchSource)) {
                setFetchSourcesEagerly(
                        Boolean.parseBoolean(fetchSource));
            }
        }
    }

    @IncludeTypeSpecific(repoType = REMOTE)
    @Override
    public boolean isXrayIndex() {
        return xrayRepoConfig != null && xrayRepoConfig.isEnabled();
    }

    @IncludeTypeSpecific(repoType = REMOTE)
    @Override
    public String getXrayNotAvailableMode() {
        if (xrayRepoConfig != null && xrayRepoConfig.getXrayNotAvailableMode() != null) {
            return xrayRepoConfig.getXrayNotAvailableMode().getValue();
        }
        return null;
    }

    public void setXrayIndex(boolean xrayIndex) {
        getLazyXrayConfig().setEnabled(xrayIndex);
    }

    @IncludeTypeSpecific(repoType = REMOTE)
    @Override
    public int getXrayDataTtl() {
        if (xrayRepoConfig != null) {
            return xrayRepoConfig.getDataTtl();
        }
        return DEFAULT_DATA_TTL;
    }

    public void setXrayDataTtl(int xrayDataTtl) {
        getLazyXrayConfig().setDataTtl(xrayDataTtl);
    }

    @IncludeTypeSpecific(repoType = REMOTE)
    @Override
    public boolean isDownloadRedirect() {
        return downloadRedirectConfig != null && downloadRedirectConfig.isEnabled();
    }

    public void setDownloadRedirect(boolean enableDownloadRedirect) {
        getLazyDownloadRedirectConfig().setEnabled(enableDownloadRedirect);
    }

    @Override
    public boolean isCdnRedirect() {
        return cdnRedirectRepoConfig != null && cdnRedirectRepoConfig.isEnabled();
    }

    public void setCdnRedirect(boolean enableCdnRedirect) {
        getLazyCdnRedirectConfig().setEnabled(enableCdnRedirect);
    }

    @IncludeTypeSpecific(packageType = Cargo, repoType = {REMOTE})
    @Override
    public boolean isCargoAnonymousAccess() {
        return this.cargoConfiguration != null && this.cargoConfiguration.isCargoAnonymousAccess();
    }

    public void setCargoAnonymousAccess(boolean enableCargoAnonymousAccess) {
        getCargoConfiguration().setCargoAnonymousAccess(enableCargoAnonymousAccess);
    }

    /**
     * Unlike other Cargo boolean getters here, a missing {@code cargoConfiguration} (e.g. a create/update request
     * that never touched Cargo-specific fields) must resolve to {@code true} - the sparse HTTP index is the
     * default index type, not the legacy Git index - to match {@link CargoConfiguration}'s own default. Otherwise
     * every such request would silently be treated as requesting the deprecated Git index.
     */
    @IncludeTypeSpecific(packageType = Cargo, repoType = {REMOTE})
    @Override
    public boolean isCargoInternalIndex() {
        return this.cargoConfiguration == null || this.cargoConfiguration.isCargoInternalIndex();
    }

    public void setCargoInternalIndex(boolean cargoInternalIndex) {
        getCargoConfiguration().setCargoInternalIndex(cargoInternalIndex);
    }

    @IncludeTypeSpecific(packageType = Bower, repoType = REMOTE)
    @Override
    public String getBowerRegistryUrl() {
        return this.bowerConfiguration != null ? this.bowerConfiguration.getBowerRegistryUrl() : null;
    }

    @IncludeTypeSpecific(packageType = NuGet, repoType = REMOTE)
    @Override
    public String getDownloadContextPath() {
        return this.nuGetConfiguration != null ? this.nuGetConfiguration.getDownloadContextPath() : null;
    }

    @IncludeTypeSpecific(packageType = NuGet, repoType = REMOTE)
    @Override
    public String getSymbolServerUrl() {
        return this.nuGetConfiguration != null ? this.nuGetConfiguration.getSymbolServerUrl() : null;
    }

    @IncludeTypeSpecific(packageType = NuGet, repoType = REMOTE)
    @Override
    public String getFeedContextPath() {
        return this.nuGetConfiguration != null ? this.nuGetConfiguration.getFeedContextPath() : null;
    }

    @IncludeTypeSpecific(packageType = NuGet, repoType = REMOTE)
    @Override
    public String getV3FeedUrl() {
        return this.nuGetConfiguration != null ? this.nuGetConfiguration.getV3FeedUrl() : null;
    }

    @IncludeTypeSpecific(packageType = Composer, repoType = REMOTE)
    @Override
    public String getComposerRegistryUrl() {
        return this.composerConfiguration != null ? this.composerConfiguration.getComposerRegistryUrl() : null;
    }

    @IncludeTypeSpecific(packageType = Terraform, repoType = REMOTE)
    @Override
    public String getTerraformRegistryUrl() {
        return this.terraformConfiguration != null ? this.terraformConfiguration.getTerraformRegistryUrl() : null;
    }

    @IncludeTypeSpecific(packageType = Terraform, repoType = REMOTE)
    @Override
    public String getTerraformProvidersUrl() {
        return this.terraformConfiguration != null ? this.terraformConfiguration.getTerraformProvidersUrl() : null;
    }

    @IncludeTypeSpecific(packageType = CocoaPods, repoType = REMOTE)
    @Override
    public String getPodsSpecsRepoUrl() {
        return this.podsConfiguration != null ? this.podsConfiguration.getCocoaPodsSpecsRepoUrl() : null;
    }

    @IncludeTypeSpecific(packageType = CocoaPods, repoType = REMOTE)
    @Override
    public String getPodsCdnUrl() {
        return this.podsConfiguration != null ? this.podsConfiguration.getCocoaPodsCdnUrl() : null;
    }

    @IncludeTypeSpecific(packageType = CocoaPods, repoType = REMOTE)
    @Override
    public boolean isPodsForceModifyIndex() {
        return this.podsConfiguration != null && this.podsConfiguration.isCocoaPodsForceModifyIndex();
    }


    @IncludeTypeSpecific(packageType = Pypi, repoType = REMOTE)
    @Override
    public String getPyPIRegistryUrl() {
        return this.pyPIConfiguration != null ? this.pyPIConfiguration.getPyPIRegistryUrl() : null;
    }

    @IncludeTypeSpecific(packageType = Pypi, repoType = REMOTE)
    @Override
    public String getPyPIRepositorySuffix() {
        return this.pyPIConfiguration != null ? this.pyPIConfiguration.getRepositorySuffix() : null;
    }

    @IncludeTypeSpecific(packageType = {VCS, Bower, Go, CocoaPods, Composer}, repoType = REMOTE)
    @Override
    public String getVcsType() {
        return this.vcsConfiguration != null ? this.vcsConfiguration.getType().name() : null;
    }

    @IncludeTypeSpecific(packageType = {VCS, Bower, Go, CocoaPods, Composer}, repoType = REMOTE)
    @Override
    public String getVcsGitProvider() {
        return this.vcsConfiguration != null ? this.vcsConfiguration.getGit().getProvider().name() : null;
    }

    @IncludeTypeSpecific(packageType = {VCS, Bower, Go, CocoaPods, Composer}, repoType = REMOTE)
    @Override
    public String getVcsGitDownloadUrl() {
        return this.vcsConfiguration != null ? this.vcsConfiguration.getGit().getDownloadUrl() : null;
    }

    @IncludeTypeSpecific(packageType = {Go}, repoType = REMOTE)
    @Override
    public boolean isGitLabResolveSubgroups() {
        return this.goConfiguration != null && this.goConfiguration.isGitLabResolveSubgroups();
    }

    @IncludeTypeSpecific(packageType = Helm, repoType = REMOTE)
    @Override
    public String getChartsBaseUrl() {
        return this.helmConfiguration != null ? this.helmConfiguration.getChartsBaseUrl() : null;
    }

    @IncludeTypeSpecific(packageType = Cargo, repoType = REMOTE)
    @Override
    public String getGitRegistryUrl() {
        return this.gitConfiguration != null ? this.gitConfiguration.getRegistryUrl() : null;
    }

    // Keep these setters for jackson
    public void setBowerRegistryUrl(String bowerRegistryUrl) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(BOWER_REGISTRY_URL.getKey(), bowerRegistryUrl);
        }
        this.bowerConfiguration.setBowerRegistryUrl(bowerRegistryUrl);
    }

    public void setPyPIRegistryUrl(String pyPIRegistryUrl) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(PYPI_REGISTRY_URL.getKey(), pyPIRegistryUrl);
        }
        this.pyPIConfiguration.setPyPIRegistryUrl(pyPIRegistryUrl);
    }

    public void setPyPIRepositorySuffix(String repositorySuffix) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(PYPI_REPOSITORY_SUFFIX.getKey(), repositorySuffix);
        }
        this.pyPIConfiguration.setRepositorySuffix(repositorySuffix);
    }

    public void setComposerRegistryUrl(String composerRegistryUrl) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(COMPOSER_REGISTRY_URL.getKey(), composerRegistryUrl);
        }
        this.composerConfiguration.setComposerRegistryUrl(composerRegistryUrl);
    }

    public void setTerraformRegistryUrl(String terraformRegistryUrl) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(TERRAFORM_REGISTRY_URL.getKey(), terraformRegistryUrl);
        }
        this.terraformConfiguration.setTerraformRegistryUrl(terraformRegistryUrl);
    }

    public void setTerraformProvidersUrl(String terraformProvidersUrl) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(TERRAFORM_PROVIDERS_URL.getKey(), terraformProvidersUrl);
        }
        this.terraformConfiguration.setTerraformProvidersUrl(terraformProvidersUrl);
    }

    public void setPodsSpecsRepoUrl(String podsSpecsRepoUrl) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(COCOA_PODS_SPECS_URL.getKey(), podsSpecsRepoUrl);
        }
        this.podsConfiguration.setCocoaPodsSpecsRepoUrl(podsSpecsRepoUrl);
    }

    public void setPodsCdnUrl(String podCdnUrl) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(COCOA_PODS_CDN_URL.getKey(), podCdnUrl);
        }
        this.podsConfiguration.setCocoaPodsCdnUrl(podCdnUrl);
    }

    public void setPodsForceModifyIndex(boolean forceModifyIndex) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig()
                    .put(COCOA_PODS_FORCE_MODIFY_INDEX.getKey(), String.valueOf(forceModifyIndex));
        }
        this.podsConfiguration.setCocoaPodsForceModifyIndex(forceModifyIndex);
    }

    public void setVcsType(String vcsType) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(VCS_TYPE.getKey(), vcsType);
        }
        this.vcsConfiguration.setType(VcsType.valueOf(vcsType));
    }

    public void setVcsGitProvider(String gitProvider) {
        VcsGitProvider provider = VcsGitProvider.valueOf(gitProvider);
        if (RepoType.CocoaPods.name().equalsIgnoreCase(getPackageType()) && repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(SPECS_REPO_DOWNLOAD_PROVIDER.getKey(), gitProvider);
            this.podsConfiguration.setSpecRepoProvider(new VcsGitConfiguration(provider));
        }
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(VCS_GIT_PROVIDER.getKey(), provider.name());
        }
        this.vcsConfiguration.getGit().setProvider(provider);
    }

    public void setVcsGitDownloadUrl(String vcsGitDownloadUrl) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(VCS_GIT_DOWNLOAD_URL.getKey(), vcsGitDownloadUrl);
        }
        this.vcsConfiguration.getGit().setDownloadUrl(vcsGitDownloadUrl);
    }

    public void setGitLabResolveSubgroups(boolean resolveSubgroups) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(GITLAB_RESOLVE_SUBGROUPS.getKey(), String.valueOf(resolveSubgroups));
        }
        this.goConfiguration.setGitLabResolveSubgroups(resolveSubgroups);
    }

    public void setDownloadContextPath(String downloadContextPath) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(DOWNLOAD_CONTEXT_PATH.getKey(), downloadContextPath);
        }
        this.nuGetConfiguration.setDownloadContextPath(downloadContextPath);
    }

    public void setSymbolServerUrl(String symbolServerUrl) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(SYMBOL_SERVER_URL.getKey(), symbolServerUrl);
        }
        this.nuGetConfiguration.setSymbolServerUrl(symbolServerUrl);
    }

    public void setFeedContextPath(String feedContextPath) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(FEED_CONTEXT_PATH.getKey(), feedContextPath);
        }
        this.nuGetConfiguration.setFeedContextPath(feedContextPath);
    }

    public void setV3FeedUrl(String v3FeedUrl) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(V3_FEED_URL.getKey(), v3FeedUrl);
        }
        this.nuGetConfiguration.setV3FeedUrl(v3FeedUrl);
    }

    public void setChartsBaseUrl(String chartsBaseUrl) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(CHARTS_BASE_URL.getKey(), chartsBaseUrl);
        }
        this.helmConfiguration.setChartsBaseUrl(chartsBaseUrl);
    }

    public void setGitRegistryUrl(String gitRegistryUrl) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(GIT_REGISTRY_URL.getKey(), gitRegistryUrl);
        }
        this.gitConfiguration.setRegistryUrl(gitRegistryUrl);
    }

    @IncludeTypeSpecific(packageType = Hex, repoType = REMOTE)
    @Override
    public String getHexPublicKey() {
        return this.hexConfiguration != null ? this.hexConfiguration.getHexPublicKey() :
                PackageTypeConfigFields.HEX_REGISTRY_PUBLIC_KEY.getDefaultValue();
    }

    public void setHexPublicKey(String hexPublicKey) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(HEX_REGISTRY_PUBLIC_KEY.getKey(), hexPublicKey);
        }
        this.hexConfiguration.setHexPublicKey(hexPublicKey);
    }

    @IncludeTypeSpecific(packageType = HuggingFaceML, repoType = REMOTE)
    @Override
    public boolean isMlRepoLayout() {
        return this.huggingFaceConfiguration != null && this.huggingFaceConfiguration.isMlRepoLayout();
    }

    @Override
    public void setMlRepoLayout(boolean mlRepoLayout) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(HUGGINGFACE_ML_REPO_LAYOUT.getKey(), String.valueOf(mlRepoLayout));
        }
        this.huggingFaceConfiguration.setMlRepoLayout(mlRepoLayout);
    }

    @IncludeTypeSpecific(packageType = Ansible, repoType = REMOTE)
    @Override
    public String getAuthUrl() {
        return ansibleConfiguration.getAuthUrl();
    }

    public void setAuthUrl(String authUrl) {
        if (repoConfig != null) {
            repoConfig.getPackageTypeConfig().put(AUTH_URL.getKey(), authUrl);
        }
        ansibleConfiguration.setAuthUrl(authUrl);
    }
}
