/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.rest.common.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.addon.CoreAddons;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.VersionInfo;
import org.artifactory.api.jackson.JacksonFactory;
import org.artifactory.api.jackson.JacksonReader;
import org.artifactory.api.repo.ResearchService;
import org.artifactory.api.repo.UpstreamRepoKind;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.rest.search.result.VersionRestResult;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.features.VersionFeature;
import org.artifactory.features.matrix.SmartRepoVersionFeatures;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.repo.HttpRepositoryConfiguration;
import org.artifactory.repo.HttpRepositoryConfigurationImpl;
import org.artifactory.repo.config.RepoConfigDefaultValues;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.util.AolUtils;
import org.artifactory.util.ArtifactoryVersionUtil;
import org.artifactory.util.ClientCertificateUtils;
import org.artifactory.util.HttpClientConfigurator;
import org.jfrog.client.util.KeyStoreProvider;
import org.jfrog.client.util.PathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.artifactory.request.UrlVerifier;

import static org.artifactory.request.ArtifactoryRequest.ARTIFACTORY_ORIGINATED;
import static org.artifactory.util.CollectionUtils.notNullOrEmpty;

/**
 * Service used to discover capabilities of another artifactory
 *
 * @author michaelp
 */
@Slf4j
@Component
public class ResearchServiceImpl implements ResearchService {
    static final Set<RepoType> ALLOWED_VIRTUAL_REPO_TYPES = Set.of(RepoType.Go, RepoType.Helm);
    private static final String ARTIFACTORY_REPOSITORIES_PATH = "/api/repositories";
    private static final String ARTIFACTORY_APP_PATH = "/artifactory";
    private static final String API_PATH = "/api/";
    private static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private SmartRepoVersionFeatures smartRepoVersionFeatures;
    private CentralConfigService configService;
    private AddonsManager addonsManager;
    private HttpClientConfiguratorProvider httpClientConfiguratorProvider;

    private Set<String> defaultUrls = Collections.emptySet();
    private Set<String> blacklistUrls = Collections.emptySet();

    @PostConstruct
    void initUrlLists() {
        Set<String> urls = RepoConfigDefaultValues.DEFAULT_URLS_LIST.stream()
                .map(ResearchServiceImpl::normalizeUrl)
                .collect(Collectors.toCollection(HashSet::new));
        urls.addAll(urls.stream()
                .filter(url -> url.startsWith("https://"))
                .map(url -> url.replace("https://", "http://"))
                .collect(Collectors.toSet()));
        defaultUrls = urls;

        String blacklistValue =
                ConstantValues.syncPropertiesBlacklistUrls.getString();
        if (StringUtils.isBlank(blacklistValue)) {
            blacklistUrls = Collections.emptySet();
        } else {
            blacklistUrls = Arrays.stream(blacklistValue.split(","))
                    .filter(StringUtils::isNotBlank)
                    .map(url -> url.replace("\"", ""))
                    .map(ResearchServiceImpl::normalizeUrl)
                    .collect(Collectors.toSet());
        }
    }

    @Autowired
    public void setSmartRepoVersionFeatures(SmartRepoVersionFeatures smartRepoVersionFeatures) {
        this.smartRepoVersionFeatures = smartRepoVersionFeatures;
    }

    @Autowired
    public void setConfigService(CentralConfigService configService) {
        this.configService = configService;
    }

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Autowired
    public void setHttpClientConfiguratorProvider(HttpClientConfiguratorProvider httpClientConfiguratorProvider) {
        this.httpClientConfiguratorProvider = httpClientConfiguratorProvider;
    }

    public ArtifactoryCapabilities getArtifactoryCapabilities(HttpRepositoryConfigurationImpl configuration) {
        try (CloseableHttpClient client = createHttpClient(configuration)) {
            return getArtifactoryCapabilities(configuration.getUrl(), client);
        } catch (IOException ioe) {
            log.warn("Error creating research client to url {} : {}", configuration.getUrl(), ioe.getMessage());
            log.debug(ioe.getMessage(), ioe);
        }
        return ArtifactoryCapabilities.NOT_ARTIFACTORY;
    }

    public ArtifactoryCapabilities getArtifactoryCapabilities(RemoteRepoConfig remoteRepoConfig) {
        try (CloseableHttpClient client = createHttpClient(remoteRepoConfig)) {
            return getArtifactoryCapabilities(remoteRepoConfig.getRepoTypeConfig().getUrl(), client);
        } catch (IOException ioe) {
            log.warn("Error creating research client to url {} : {}", remoteRepoConfig.getRepoTypeConfig().getUrl(),
                    ioe.getMessage());
            log.debug(ioe.getMessage(), ioe);
        }
        return ArtifactoryCapabilities.NOT_ARTIFACTORY;

    }

    public ArtifactoryCapabilities getArtifactoryCapabilities(String url, CloseableHttpClient client) {
        URI uri = getUri(url);
        return uri == null ? ArtifactoryCapabilities.NOT_ARTIFACTORY : getArtifactoryCapabilities(client, uri);
    }

    @Override
    public UpstreamRepoKind getUpstreamRepoKind(RemoteRepoConfig remoteRepoConfig) {
        URI uri = getUri(remoteRepoConfig.getRepoTypeConfig().getUrl());
        if (uri == null) {
            return UpstreamRepoKind.NOT_CONFIGURED;
        }
        try (CloseableHttpClient client = createHttpClient(remoteRepoConfig)) {
            ArtifactoryCapabilities artifactoryCapabilities = getArtifactoryCapabilities(client, uri);
            return getUpstreamRepoKind(artifactoryCapabilities);
        } catch (NotFoundException nfe) {
            log.warn("Error creating research client to url {} : {}", remoteRepoConfig.getRepoTypeConfig().getUrl(),
                    nfe.getMessage());
            return UpstreamRepoKind.NOT_AVAILABLE;
        } catch (IOException ioe) {
            log.warn("Error creating research client to url {} : {}", remoteRepoConfig.getRepoTypeConfig().getUrl(),
                    ioe.getMessage());
            log.debug(ioe.getMessage(), ioe);
        }
        return UpstreamRepoKind.UNKNOWN;
    }

    public UpstreamRepoKind getUpstreamRepoKind(ArtifactoryCapabilities artifactoryCapabilities) {
        ResearchResponse researchResponse = artifactoryCapabilities.getResearchResponse();

        RepoInfo repoInfo = artifactoryCapabilities.getRepoInfo();
        switch (repoInfo) {
            case ALLOWED_SMART -> {
                return handleAllowedSmart(researchResponse);
            }
            case NOT_ALLOWED_SMART -> {
                return UpstreamRepoKind.COMMON;
            }
            case UNAUTHORIZED -> {
                return UpstreamRepoKind.UNAUTHORIZED;
            }
            case NOT_AVAILABLE -> {
                return UpstreamRepoKind.NOT_AVAILABLE;
            }
            case UNKNOWN -> {
                return !researchResponse.isArtifactory() ? UpstreamRepoKind.NOT_ARTIFACTORY : UpstreamRepoKind.UNKNOWN;
            }
            default -> {
                return UpstreamRepoKind.UNKNOWN;
            }
        }
    }

    private UpstreamRepoKind handleAllowedSmart(ResearchResponse researchResponse) {
        List<VersionFeature> features = researchResponse.getFeatures();
        boolean isSmartRepoSupported = researchResponse.getVersion() != null
                && notNullOrEmpty(features);
        return isSmartRepoSupported ? UpstreamRepoKind.SMART : UpstreamRepoKind.COMMON;
    }


    @Override
    public boolean isRepoConfiguredToSyncProperties(RemoteRepoConfig repoConfig) {
        org.artifactory.repo.remote.ContentSynchronisation contentSynchronisation =
                repoConfig.getRepoTypeConfig().getContentSynchronisation();
        return repoConfig.getRepoTypeConfig().isSynchronizeProperties() ||
                (contentSynchronisation != null
                        && contentSynchronisation.isEnabled()
                        && contentSynchronisation.getProperties().isEnabled()
                );
    }

    @Override
    public boolean isKnownPublicRegistryUrl(String upstreamUrl) {
        if (StringUtils.isBlank(upstreamUrl)) {
            return false;
        }
        return getDefaultUrls().contains(normalizeUrl(upstreamUrl));
    }

    @Override
    public boolean isUpstreamUrlBlockedForPropertySync(String upstreamUrl) {
        if (StringUtils.isBlank(upstreamUrl)) {
            return false;
        }
        String normalized = normalizeUrl(upstreamUrl);
        return getDefaultUrls().contains(normalized) || getBlacklistUrls().contains(normalized);
    }

    private Set<String> getBlacklistUrls() {
        return blacklistUrls;
    }

    @VisibleForTesting
    Set<String> getDefaultUrls() {
        return defaultUrls;
    }

    /**
     * Strips trailing slashes and surrounding whitespace so that
     * URL variants like {@code "https://host/"} and
     * {@code "https://host"} resolve to the same canonical form.
     *
     * @param url the raw URL, may be {@code null}
     * @return the normalized URL, or {@code null} if input was
     *         {@code null}
     */
    @VisibleForTesting
    static String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Fetches artifactory capabilities (if target is artifactory)
     *
     * @param client http client to use
     * @param uri    remote uri to test against
     * @return {@link VersionInfo} if target is artifactory or null
     */
    private ArtifactoryCapabilities getArtifactoryCapabilities(CloseableHttpClient client, URI uri) {
        String requestUrl = produceVersionUrl(uri);
        CloseableHttpResponse response = null;
        ResearchResponse researchResponse = ResearchResponse.notArtifactory();
        RepoInfo repoInfo = RepoInfo.UNKNOWN;
        try {
            response = ArtifactoryVersionUtil.getArtifactoryServiceVersion(client, requestUrl,
                    Collections.singletonMap(ARTIFACTORY_ORIGINATED,
                            addonsManager.addonByType(HaCommonAddon.class).getHostId()));
            if (response != null) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == HttpStatus.SC_OK) {
                    ArtifactoryCapabilitiesResult result = processSuccessfulVersionResponse(
                            response, client, uri);
                    researchResponse = result.researchResponse;
                    repoInfo = result.repoInfo;
                } else if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
                    researchResponse = ResearchResponse.artifactory();
                    repoInfo = RepoInfo.UNAUTHORIZED;
                }
            }
        } catch (IOException | UncheckedIOException | NotFoundException e) {
            repoInfo = RepoInfo.NOT_AVAILABLE;
            log.warn("Checking remote Artifactory version has failed: {}.", e.getMessage());
            log.debug("", e);
        } catch (IllegalArgumentException e) {
            log.warn("Checking remote Artifactory version has failed: {}.", e.getMessage());
            log.debug("", e);
        } finally {
            IOUtils.closeQuietly(response);
        }
        logRepoInfo(repoInfo);
        return new ArtifactoryCapabilities(researchResponse, repoInfo);
    }

    private ArtifactoryCapabilitiesResult processSuccessfulVersionResponse(
            CloseableHttpResponse response, CloseableHttpClient client, URI uri) throws IOException {
        String returnedInfo = EntityUtils.toString(response.getEntity());
        if (StringUtils.isBlank(returnedInfo)) {
            return new ArtifactoryCapabilitiesResult(ResearchResponse.notArtifactory(), RepoInfo.UNKNOWN);
        }
        VersionRestResult vrr = parseVersionRestResult(returnedInfo);
        if (vrr == null || StringUtils.isBlank(vrr.getVersion())) {
            log.debug("Unsupported version: {}", vrr);
            return new ArtifactoryCapabilitiesResult(ResearchResponse.notArtifactory(), RepoInfo.UNKNOWN);
        }
        if (!validateLicense(vrr, getArtifactoryId(response))) {
            // We successfully detected it's Artifactory (got valid version),
            // but license validation failed - mark as Artifactory but keep repoInfo as UNKNOWN
            log.debug("License validation failed for Artifactory instance, " +
                    "unable to determine repository type");
            return new ArtifactoryCapabilitiesResult(ResearchResponse.artifactory(), RepoInfo.UNKNOWN);
        }
        VersionInfo versionInfo = vrr.toVersionInfo();
        ResearchResponse researchResponse = ResearchResponse.artifactoryMeta(
                true,
                versionInfo,
                smartRepoVersionFeatures.getFeaturesByVersion(versionInfo)
        );
        RepoInfo repoInfo = getRepoInfo(client, uri, PathUtils.getLastPathElement(uri.getPath()));
        return new ArtifactoryCapabilitiesResult(researchResponse, repoInfo);
    }

    private static class ArtifactoryCapabilitiesResult {
        final ResearchResponse researchResponse;
        final RepoInfo repoInfo;

        ArtifactoryCapabilitiesResult(ResearchResponse researchResponse, RepoInfo repoInfo) {
            this.researchResponse = researchResponse;
            this.repoInfo = repoInfo;
        }
    }

    private void logRepoInfo(RepoInfo repoInfo) {
        switch (repoInfo) {
            case ALLOWED_SMART ->
                    log.debug("Repo is artifactory allowed repository and has supported version for SmartRepo");
            case NOT_ALLOWED_SMART ->
                    log.debug("Virtual repository is supported in this version of SmartRepo only for repo types {}",
                            ALLOWED_VIRTUAL_REPO_TYPES);
            case UNAUTHORIZED ->
                    /*
                    we were unable to check repoType
                    what may happen if config doesn't
                    have credentials or has insufficient permissions
                    (api requires it even if anonymous login is on)
                    */
                    log.warn("We were unable to check repoType, it may happen if config doesn't have credentials or " +
                            "user permissions insufficient");
            case UNKNOWN -> log.warn("We were unable to check repoType, maybe it does not exist");
            default -> {return;}
        }
    }

    /**
     * Fetches ARTIFACTORY_ID header from {@link CloseableHttpResponse}
     *
     * @return ArtifactoryId if present or null
     */
    @Nullable
    private String getArtifactoryId(CloseableHttpResponse response) {
        assert response != null : "HttpResponse cannot be empty";
        Header artifactoryIdHeader = response.getFirstHeader(ArtifactoryResponse.ARTIFACTORY_ID);
        if (artifactoryIdHeader != null && !StringUtils.isBlank(artifactoryIdHeader.getValue())) {
            return artifactoryIdHeader.getValue();
        }
        return null;
    }

    /**
     * Unmarshal VersionRestResult from string response
     *
     * @return {@link VersionRestResult}
     * @throws IOException if conversion fails
     */
    private VersionRestResult parseVersionRestResult(String versionRestResult) throws IOException {
        try (JsonParser jsonParser = JacksonFactory.createJsonParser(versionRestResult.getBytes())) {
            return mapper.readValue(jsonParser, new TypeReference<>() {
            });
        }
    }

    /**
     * add originated header to request
     *
     * @param request - http servlet request
     */
    private void addOriginatedHeader(HttpRequestBase request) {
        request.addHeader(ARTIFACTORY_ORIGINATED, addonsManager.addonByType(HaCommonAddon.class).getHostId());
    }

    /**
     * Produces url to be used against target host
     *
     * @param uri original URI
     * @return url to be used
     */
    String produceVersionUrl(URI uri) {
        return uri.getScheme() +
                "://" +
                uri.getHost() +
                (uri.getPort() != -1 ? ":" + uri.getPort() : "") +
                getServiceName(uri);

    }

    /**
     * Fetches service name (if exist) from the original URI
     * e.g: http{s}://serverName{:port}/artifactory/repoName/ -> "/artifactory"
     * e.g: http{s}://serverName{:port}/artifactory/api/npm/npm-remote/ -> "/artifactory"
     * e.g: http{s}://serverName{:port}/api/npm/npm-remote/ -> ""
     * e.g: http{s}://serverName{:port}/serviceName/repoName/ -> "/serviceName"
     * e.g: http{s}://serverName{:port}/repoName/ -> ""
     *
     * @return service name
     */
    private String getServiceName(@Nonnull URI uri) {
        String path = uri.getPath();
        if (path.startsWith(ARTIFACTORY_APP_PATH + "/")) {
            //inside artifactory context
            return ARTIFACTORY_APP_PATH;
        }
        // Check for JFrog Bridge URL pattern: /bridge-xxx/artifactory/... (path starts with /bridge-)
        String bridgeServiceName = extractBridgeServiceName(path);
        if (bridgeServiceName != null) {
            return bridgeServiceName;
        }
        //path might not starts with "/artifactory/" when artifactory service is behind reverse proxy
        //either with root url or other prefix (serviceName)
        //in those cases, need to extract correct serviceName if exist
        String[] parts = path.split("/");
        if (parts.length == 4 && path.startsWith(API_PATH)) {
            //found smart-repo pattern on root url (pattern is /api/repoType/repoName/)
            //e.g: http://artifactoryServer/api/docker/docker-remote
            return "";
        }
        if (parts.length >= 2) {
            if (!(parts.length == 2 && path.startsWith("/") && path.endsWith("/"))) {
                return "/" + PathUtils.getFirstPathElement(path); // .../serviceName/repoName
            }
        }
        return ""; // repoName
    }

    /**
     * Extracts service name from JFrog Bridge URL pattern.
     * Bridge URLs use path {@code /bridge-&lt;instance&gt;/artifactory/...} (full path must start with
     * {@code /bridge-}), e.g. {@code http://localhost:&lt;routerPort&gt;/bridge-xxx/artifactory/...}.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code /bridge-xyz/artifactory/npm-remote/} -> {@code /bridge-xyz/artifactory}</li>
     *   <li>{@code /bridge-simple/artifactory/repo/} -> {@code /bridge-simple/artifactory}</li>
     *   <li>{@code /bridge-xxx/repo/} -> {@code null} (no {@code /artifactory/} segment)</li>
     *   <li>{@code /api/docker/docker-remote/} -> {@code null} (not a Bridge path)</li>
     * </ul>
     *
     * @param path the URL path
     * @return the service name including /artifactory, or null if not a Bridge URL
     */
    @VisibleForTesting
    @Nullable
    String extractBridgeServiceName(@Nonnull String path) {
        // Path must match Bridge route from the start (see UrlVerifier.isBridgePath)
        if (!UrlVerifier.isBridgePath(path)) {
            return null;
        }
        // Find /artifactory/ in the path - it must come after /bridge-
        int artifactoryIndex = path.indexOf(ARTIFACTORY_APP_PATH + "/");
        if (artifactoryIndex > 0) {
            // Extract everything up to and including /artifactory
            return path.substring(0, artifactoryIndex + ARTIFACTORY_APP_PATH.length());
        }
        return null;
    }

    private URI getUri(String url) {
        if (StringUtils.isBlank(url)) {
            log.debug("Url is a mandatory (query) parameter.");
            return null;
        }
        try {
            return URI.create(url);
        } catch (IllegalArgumentException e) {
            log.debug("Url is malformed.");
            return null;
        }
    }

    /**
     * Validates that remote Artifactory uses different license and it is PRO license
     *
     * @param versionRestResult remote license
     * @param artifactoryId     remote artifactory id
     * @return true if both constraints are true otherwise false
     */
    @VisibleForTesting
    boolean validateLicense(VersionRestResult versionRestResult, String artifactoryId) {
        CoreAddons coreAddons = addonsManager.addonByType(CoreAddons.class);
        String targetLicense = versionRestResult.getLicense();
        boolean isDifferentLicense = coreAddons.validateTargetHasDifferentLicense(targetLicense, artifactoryId);
        boolean isOssLicensed = addonsManager.isOssLicensed(targetLicense);
        ArtifactoryRunningMode artifactoryRunningMode = addonsManager.getArtifactoryRunningMode();

        if (isDifferentLicense) {
            // if source has Edge license target should have either Edge or EntPlus license
            if (addonsManager.isEntitled(EntitlementType.EDGE_MIXED) &&
                    !addonsManager.isValidTargetRepositoryForEdgeNode(versionRestResult)) {
                log.warn(
                        "License validation against target repository has failed - only Edge or Enterprise Plus " +
                                "licenses " +
                                "are valid for artifactory Edge, SmartRepo capabilities won't be enabled");
                return false;
            }
            if (!isOssLicensed || artifactoryRunningMode.isJCR()) {
                return true;
            }
            log.warn("License PRO validation against target repository has failed, " +
                    "SmartRepo capabilities won't be enabled");
        } else if (AolUtils.isAol()) {
            return true;
        } else {
            log.warn("License uniqueness validation against target repository " +
                    "has failed, SmartRepo capabilities won't be enabled");
        }
        return false;
    }

    @VisibleForTesting
    protected RepoInfo getRepoInfo(CloseableHttpClient client, URI uri, String repoKey) {

        assert client != null : "HttpClient cannot be empty";

        CloseableHttpResponse response = null;
        String requestUrl = produceRepoInfoUrl(uri, repoKey);
        HttpGet getMethod = new HttpGet(requestUrl);
        addOriginatedHeader(getMethod);
        try {
            response = client.execute(getMethod);
            if (response != null) {
                int statusCode = response.getStatusLine().getStatusCode();
                RepoInfo statusCodeRepoInfo = getRepoInfoFromStatusCode(statusCode);
                if (statusCodeRepoInfo != null) {
                    log.warn("Repo info request for repo '{}' using url '{}' failed with status code: {}",
                            repoKey, requestUrl, statusCode);
                    return statusCodeRepoInfo;
                }
                if (statusCode != HttpStatus.SC_OK) {
                    log.warn("Cannot fetch '{}' metadata, cause: {}", repoKey, statusCode);
                }
                if (statusCode == HttpStatus.SC_OK) {
                    RepoInfo repoInfo = processSuccessfulRepoInfoResponse(response, repoKey);
                    if (repoInfo != null) {
                        return repoInfo;
                    }
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            log.warn(
                    "Checking remote artifactory type has failed: {}.",
                    e.getMessage()
            );
        } finally {
            IOUtils.closeQuietly(response);
        }
        return RepoInfo.UNKNOWN;
    }

    @Nullable
    private RepoInfo getRepoInfoFromStatusCode(int statusCode) {
        if (statusCode == HttpStatus.SC_UNAUTHORIZED || statusCode == HttpStatus.SC_FORBIDDEN) {
            return RepoInfo.UNAUTHORIZED;
        }
        if (statusCode == HttpStatus.SC_TOO_MANY_REQUESTS || statusCode == HttpStatus.SC_SERVICE_UNAVAILABLE) {
            return RepoInfo.NOT_AVAILABLE;
        }
        return null;
    }

    @Nullable
    private RepoInfo processSuccessfulRepoInfoResponse(CloseableHttpResponse response, String repoKey)
            throws IOException {
        HttpRepositoryConfiguration httpRepositoryConfiguration =
                JacksonReader.streamAsClass(
                        response.getEntity().getContent(),
                        HttpRepositoryConfigurationImpl.class
                );
        if (httpRepositoryConfiguration == null) {
            log.debug("Cannot fetch '{}' metadata, no response received", repoKey);
            return null;
        }
        if (httpRepositoryConfiguration.getType().equals("virtual")) {
            return processVirtualRepositoryInfo(httpRepositoryConfiguration, repoKey);
        }
        log.debug("Found real repository '{}'", repoKey);
        return RepoInfo.ALLOWED_SMART;
    }

    private RepoInfo processVirtualRepositoryInfo(HttpRepositoryConfiguration httpRepositoryConfiguration,
            String repoKey) {
        String responsePackageType = httpRepositoryConfiguration.getPackageType();
        RepoType responseRepoType = RepoType.fromType(responsePackageType);
        if (ALLOWED_VIRTUAL_REPO_TYPES.contains(responseRepoType)) {
            log.debug("Found virtual repository '{}' but with allowed repo type '{}'",
                    repoKey, responseRepoType);
            return RepoInfo.ALLOWED_SMART;
        }
        log.debug("Found virtual repository '{}' with repo type '{}' which is not allowed", repoKey,
                responseRepoType);
        return RepoInfo.NOT_ALLOWED_SMART;
    }

    /**
     * Generates repository info URL
     *
     * @param uri default target URI
     * @return url
     */
    private String produceRepoInfoUrl(URI uri, String repoKey) {
        return uri.getScheme() +
                "://" +
                uri.getHost() +
                (uri.getPort() != -1 ? ":" + uri.getPort() : "") +
                getServiceName(uri) +
                ARTIFACTORY_REPOSITORIES_PATH +
                "/" +
                repoKey;
    }

    @VisibleForTesting
    CloseableHttpClient createHttpClient(RemoteRepoConfig remoteRepoConfig) {
        HttpClientConfigurator configurator = httpClientConfiguratorProvider.getHttpClientConfigurator();
        RemoteRepositoryRepoTypeConfig repoTypeConfig = remoteRepoConfig.getRepoTypeConfig();
        configurator.hostFromUrl(repoTypeConfig.getUrl())
                .connectionTimeout(repoTypeConfig.getSocketTimeoutMillis())
                .socketTimeout(repoTypeConfig.getSocketTimeoutMillis())
                .staleCheckingEnabled(true)
                .retry(0, false)
                .localAddress(repoTypeConfig.getLocalAddress())
                .authentication(
                        repoTypeConfig.getUsername(),
                        CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(),
                                repoTypeConfig.getPassword()),
                        repoTypeConfig.isAllowAnyHostAuth())
                .enableCookieManagement(repoTypeConfig.isEnableCookieManagement());
        if (!repoTypeConfig.isDisableProxy()) {
            String proxyRef = repoTypeConfig.getProxyRef();
            configureProxy(configurator, proxyRef, remoteRepoConfig.getKey());
        }
        configureClientCertificate(
                configurator, repoTypeConfig.getClientTlsCertificate());
        return configurator.build();
    }

    @VisibleForTesting
    CloseableHttpClient createHttpClient(HttpRepositoryConfigurationImpl configuration) {
        HttpClientConfigurator configurator = httpClientConfiguratorProvider.getHttpClientConfigurator();
        configurator.hostFromUrl(configuration.getUrl())
                .connectionTimeout(configuration.getSocketTimeoutMillis())
                .socketTimeout(configuration.getSocketTimeoutMillis())
                .staleCheckingEnabled(true)
                .retry(0, false)
                .localAddress(configuration.getLocalAddress())
                .authentication(
                        configuration.getUsername(),
                        CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), configuration.getPassword()),
                        configuration.isAllowAnyHostAuth())
                .enableCookieManagement(configuration.isEnableCookieManagement());
        if (!configuration.isDisableProxy()) {
            String proxyRef = configuration.getProxy();
            configureProxy(configurator, proxyRef, configuration.getKey());
        }
        configureClientCertificate(
                configurator, configuration.getClientTlsCertificate());
        return configurator.build();
    }

    private void configureProxy(HttpClientConfigurator configurator, String proxyRef, String key) {
        CentralConfigDescriptor descriptor = configService.getDescriptor();
        boolean hasExplicitProxyRef = StringUtils.isNotBlank(proxyRef);
        ProxyDescriptor proxy = hasExplicitProxyRef
                ? descriptor.getProxy(proxyRef)
                : descriptor.getDefaultProxy();
        if (hasExplicitProxyRef && proxy == null) {
            log.debug("Repository '{}' contains a reference to proxy '{}' which does not exist on the config " +
                            "descriptor; using default proxy for validation to align with saved configuration",
                    key, proxyRef);
            proxy = descriptor.getDefaultProxy();
        }
        configurator.proxy(proxy);
    }

    private void configureClientCertificate(
            HttpClientConfigurator configurator, String certificateToUse) {
        if (StringUtils.isNotBlank(certificateToUse)) {
            KeyStoreProvider keyStoreProvider =
                    ClientCertificateUtils.getKeyStoreProvider();
            if (keyStoreProvider != null) {
                configurator.clientCertKeyStoreProvider(keyStoreProvider);
                configurator.clientCertAlias(
                        ArtifactWebstartAddon.SSL_CERT_ALIAS_PREFIX
                                + certificateToUse);
            } else {
                log.warn("Client TLS certificate '{}' is configured"
                                + " but the keystore is not available;"
                                + " mTLS authentication will not be used",
                        certificateToUse);
            }
        }
    }

    public enum RepoInfo {
        ALLOWED_SMART,
        NOT_ALLOWED_SMART,
        UNAUTHORIZED,
        NOT_AVAILABLE,
        UNKNOWN
    }

    @Data
    public static class ArtifactoryCapabilities {
        static final ArtifactoryCapabilities NOT_ARTIFACTORY =
                new ArtifactoryCapabilities(ResearchResponse.notArtifactory(), RepoInfo.UNKNOWN);
        private final ResearchResponse researchResponse;
        private final RepoInfo repoInfo;
    }

    /**
     * ResearchResponse
     */
    public static class ResearchResponse {

        @Getter
        private final boolean isArtifactory;
        private final VersionInfo versionInfo;
        @Getter
        private final List<VersionFeature> features;
        @Getter
        @Setter
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String fixedUrl;

        /**
         * Produces response with artifactory=false
         */
        private ResearchResponse() {
            this.isArtifactory = false;
            this.versionInfo = null;
            this.features = Lists.newLinkedList();
        }

        /**
         * Produces response with metadata describing remote
         * artifactory instance
         */
        private ResearchResponse(boolean isArtifactory, VersionInfo versionInfo, List<VersionFeature> features) {
            this.isArtifactory = isArtifactory;
            this.versionInfo = versionInfo;
            this.features = features;
            this.fixedUrl = null;
        }

        /**
         * Produces response with artifactory=false
         *
         * @return {@link ResearchResponse}
         */
        public static ResearchResponse notArtifactory() {
            return new ResearchResponse();
        }

        /**
         * Produces response with metadata describing remote
         * artifactory instance
         *
         * @return {@link ResearchResponse}
         */
        public static ResearchResponse artifactoryMeta(boolean isArtifactory, VersionInfo versionInfo,
                List<VersionFeature> features) {
            return new ResearchResponse(isArtifactory, versionInfo, features);
        }

        /**
         * Produces response without any metadata, but implies artifactory=true
         *
         * @return {@link ResearchResponse}
         */
        public static ResearchResponse artifactory() {
            return new ResearchResponse(true, null, Lists.newLinkedList());
        }

        /**
         * @return remote artifactory version
         */
        public VersionInfo getVersion() {
            return versionInfo;
        }
    }
}