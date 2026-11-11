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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.jfrog.sysconf.SysConfig;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.HttpMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.LayoutsCoreAddon;
import org.artifactory.addon.curation.CurationCachedPackageStatus;
import org.artifactory.addon.curation.CurationPackageStatus;
import org.artifactory.addon.curation.packages.PackagesCurationCompliantVersionService;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.RemoteRequestCtx;
import org.artifactory.addon.plugin.download.AfterRemoteDownloadAction;
import org.artifactory.addon.plugin.download.BeforeRemoteDownloadAction;
import org.artifactory.addon.plugin.download.BeforeRemoteInfoRequestAction;
import org.artifactory.addon.smartrepo.EdgeSmartRepoAddon;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.addon.worker.WorkersAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.ResearchService;
import org.artifactory.api.repo.UpstreamRepoKind;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.rest.constant.ArtifactRestConstants;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.delegation.ContentSynchronisationDescriptor;
import org.artifactory.descriptor.property.Property;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.download.RateLimiterException;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.exception.WorkerStopException;
import org.artifactory.exceptions.IllegalUrlPathException;
import org.artifactory.fs.RepoResource;
import org.artifactory.http.SrcTenantIdHeaderInterceptor;
import org.artifactory.io.NullResourceStreamHandle;
import org.artifactory.repo.multipart.PreChecksummedResourceStreamHandle;
import org.artifactory.io.RemoteResourceStreamHandle;
import org.artifactory.md.Properties;
import org.artifactory.metrics.ExternalClientsServiceMetrics;
import org.artifactory.mime.MavenNaming;
import org.artifactory.mime.NamingUtils;
import org.artifactory.util.CustomHttpHeaderUtils;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.repo.http.CloseableHttpExecute;
import org.artifactory.repo.http.DenyListCacheHolderService;
import org.artifactory.repo.http.research.HttpRepoResearchRequestResponseInterceptor;
import org.artifactory.repo.multipart.ParallelDownloadService;
import com.jfrog.storage.sonic.DownloadResult;
import org.artifactory.repo.remote.browse.HtmlRepositoryBrowser;
import org.artifactory.repo.remote.browse.HttpExecutor;
import org.artifactory.repo.remote.browse.RemoteItem;
import org.artifactory.repo.remote.browse.RemoteRepositoryBrowser;
import org.artifactory.repo.remote.browse.S3RepositoryBrowser;
import org.artifactory.repo.remote.interceptor.RemoteRepoRequestResponseInterceptor;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.HeadersMultiMap;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.NullRequestContext;
import org.artifactory.request.RemoteRequestException;
import org.artifactory.request.RepoRequests;
import org.artifactory.request.Request;
import org.artifactory.request.RequestContext;
import org.artifactory.request.UrlVerifier;
import org.artifactory.resource.BlockedRepoResource;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.schedule.monitor.OnlineMonitorService;
import org.artifactory.service.SmartRepoService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.service.OutboundRequestLogService;
import org.artifactory.throtlling.generic.rrl.service.IRequestRateLimiterService;
import org.artifactory.traffic.OutboundRequestLogger;
import org.artifactory.traffic.TrafficService;
import org.artifactory.unifiedevent.UnifiedEventServiceImpl;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.util.AolUtils;
import org.artifactory.util.BasicHttpUtils;
import org.artifactory.util.ClientCertificateUtils;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.GitPackageTypeConfigMapper;
import org.artifactory.util.HttpClientConfigurator;
import org.artifactory.util.HttpClientUtils;
import org.artifactory.util.HttpUtils;
import org.artifactory.util.RepoLayoutUtils;
import org.artifactory.util.bearer.ArtifactoryBearerScheme;
import org.jfrog.client.http.CloseableHttpClientDecorator;
import org.jfrog.client.http.JFrogDefaultRedirectStrategy;
import org.jfrog.client.http.auth.PreemptiveBearerTokenAuthInterceptor;
import org.jfrog.client.util.DenylistLoader;
import org.jfrog.client.util.DenylistValidator;
import org.jfrog.client.util.KeyStoreProvider;
import org.jfrog.client.util.PathUtils;
import org.jfrog.common.JsonUtils;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.common.iostreams.in.BandwidthMonitorInputStream;
import org.jfrog.storage.common.StorageUnit;
import org.jfrog.worker.proto.execution.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Boolean.parseBoolean;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.HttpHeaders.ACCEPT_ENCODING;
import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.api.repo.UpstreamRepoKind.NOT_AVAILABLE;
import static org.artifactory.api.request.ArtifactoryHeaders.CURATION_ORIGIN;
import static org.artifactory.api.request.ArtifactoryHeaders.ORIGIN_REMOTE_PATH;
import static org.artifactory.api.request.ArtifactoryHeaders.XRAY_ORIGIN;
import static org.artifactory.api.request.ArtifactoryResponse.ARTIFACTORY_ID;
import static org.artifactory.repo.db.DbStoringRepoMixin.ORIGIN_REMOTE_PATH_PROP_KEY;
import static org.artifactory.request.ArtifactoryRequest.ARTIFACTORY_ORIGINATED;
import static org.artifactory.request.ArtifactoryRequest.ORIGIN_ARTIFACTORY;
import static org.artifactory.request.ArtifactoryRequest.PARAM_FOLDER_REDIRECT_ASSERTION;
import static org.artifactory.request.ArtifactoryRequest.PARAM_REPLACE_HEAD_IN_RETRIEVE_INFO_WITH_GET;
import static org.artifactory.util.RepoPathUtils.addTrailingSlashIfNeeded;
import static org.jfrog.common.ExceptionLogUtils.debugOrWarn;

public class HttpRepo extends RemoteRepoBase<HttpRepoDescriptor, RemoteRepoConfig>
        implements CloseableHttpExecute {
    private static final Logger log = LoggerFactory.getLogger(HttpRepo.class);

    @VisibleForTesting
    record HttpRequestExecutionContext(String fullUrl, String urlWithParams, HttpRequestBase method,
                                       String methodType, HttpClientContext httpClientContext,
                                       HeadersMultiMap headers, boolean useRange) {
    }

    public static final String GITHUB_HOST = "github.com";
    public static final String GITHUB_API_HOST = "api.github.com";
    public static final String GHE_HOST = ".ghe.com";
    public static final String GITHUB_RAW_HOST = "raw.githubusercontent.com";
    public static final String BITBUCKET_HOST = "bitbucket.org";
    public static final String BITBUCKET_API_HOST = "api.bitbucket.org";
    public static final String GITLAB_HOST = "gitlab.com";
    public static final String GITLAB_API_HOST = "gitlab.com/api/v4";
    private static final String QUERY_SEPARATOR = "&";
    private static final String QUERY_START = "?";
    private static final String KEY_VALUE_SEPARATOR = "=";
    private static final String EMPTY = "";
    private static final boolean REQUEST_SENT_RETRY_ENABLED = false;
    private static final int RETRY_COUNT = 1;
    private static final String LOCAL_REPO_RESPONSE_INDICATOR = "local";
    private static final String LOCAL_PREFIX = LOCAL_REPO_RESPONSE_INDICATOR + ":";

    @Nullable
    private volatile CloseableHttpClient client;
    private RemoteRepositoryBrowser remoteBrowser;
    private LayoutsCoreAddon layoutsCoreAddon;
    private AddonsManager addonsManager;
    private IRequestRateLimiterService rateLimiterService;
    private final ResearchService researchService;
    @Nullable
    private UnifiedEventServiceImpl unifiedEventService;
    private boolean handleGzipResponse;

    private Boolean s3Repository;
    private Supplier<UpstreamRepoKind> upstreamRepoKindSupplier = createUpstreamRepoKindSupplier();
    private final boolean needToNormalize;
    private boolean usesAuthentication;

    public HttpRepo(HttpRepoDescriptor descriptor, RemoteRepoConfig repoConfig,
            InternalRepositoryService repositoryService,
            AddonsManager addonsManager,
            ResearchService researchService, RemoteRepo<?, ?> oldRemoteRepo,
            OnlineMonitorService onlineMonitorService, CentralConfigService configService) {
        this(descriptor, repoConfig, repositoryService, addonsManager, researchService, oldRemoteRepo,
                onlineMonitorService, configService, null);
    }

    public HttpRepo(HttpRepoDescriptor descriptor, RemoteRepoConfig repoConfig,
            InternalRepositoryService repositoryService,
            AddonsManager addonsManager,
            ResearchService researchService, RemoteRepo<?, ?> oldRemoteRepo,
            OnlineMonitorService onlineMonitorService, CentralConfigService configService,
            IRequestRateLimiterService rateLimiterService) {
        this(descriptor, repoConfig, repositoryService, addonsManager, researchService, oldRemoteRepo,
                onlineMonitorService, configService, rateLimiterService, null);
    }

    public HttpRepo(HttpRepoDescriptor descriptor, RemoteRepoConfig repoConfig,
            InternalRepositoryService repositoryService,
            AddonsManager addonsManager,
            ResearchService researchService, RemoteRepo<?, ?> oldRemoteRepo,
            OnlineMonitorService onlineMonitorService, CentralConfigService configService,
            IRequestRateLimiterService rateLimiterService,
            @Nullable PackagesCurationCompliantVersionService curationCompliantVersionService) {
        super(descriptor, repoConfig, repositoryService, oldRemoteRepo, onlineMonitorService,
                configService, curationCompliantVersionService);
        this.researchService = researchService;
        this.addonsManager = addonsManager;
        layoutsCoreAddon = addonsManager.addonByType(LayoutsCoreAddon.class);
        needToNormalize = isNeedToNormalize(repoConfig);
        this.configService = configService;
        this.rateLimiterService = rateLimiterService;
    }

    protected boolean isNeedToNormalize() {
        return needToNormalize;
    }

    @Override
    public void close() {
        if (client instanceof CloseableHttpClientDecorator) {
            ((CloseableHttpClientDecorator) client).onClose();
        }
    }

    /**
     * Initializes the remote repository.
     * <p>
     * DOES NOT reinitialize the client. It should only be called once, after the repository has been created.
     * </p>
     */
    @Override
    public void init(boolean existenceValidated) {
        super.init(existenceValidated);
        // TODO: This flag should be in the remote repo descriptor
        handleGzipResponse = ConstantValues.httpAcceptEncodingGzip.getBoolean();
    }

    private synchronized void initRemoteRepositoryBrowser() {
        if (remoteBrowser != null) {
            return; // already initialized
        }
        HttpExecutor clientExec = this::executeMethod;
        String url = PathUtils.addTrailingSlash(getUrl());
        s3Repository = S3RepositoryBrowser
                .isS3Repository(url, getHttpClient(), getKey(), getUsername(), usesAuthentication);
        if (s3Repository) {
            log.debug("Repository {} caches S3 repository", getKey());
            remoteBrowser = new S3RepositoryBrowser(clientExec, this);
        } else {
            remoteBrowser = new HtmlRepositoryBrowser(clientExec);
        }
    }

    @Override
    public void destroy() {
        cleanupResources();
        super.destroy();
        if (client != null) {
            IOUtils.closeQuietly(client, null);
        }
    }

    @Override
    public void cleanupResources() {
        stopOfflineCheckThread();
    }

    public String getUsername() {
        return getRepoConfig().getRepoTypeConfig().getUsername();
    }

    public String getPassword() {
        return getRepoConfig().getRepoTypeConfig().getPassword();
    }

    public boolean isAllowAnyHostAuth() {
        return getRepoConfig().getRepoTypeConfig().isAllowAnyHostAuth();
    }

    public boolean isEnableTokenAuthentication() {
        return getRepoConfig().getRepoTypeConfig().isEnableTokenAuthentication();
    }

    public boolean isEnableCookieManagement() {
        return getRepoConfig().getRepoTypeConfig().isEnableCookieManagement();
    }

    public int getSocketTimeoutMillis() {
        return getRepoConfig().getRepoTypeConfig().getSocketTimeoutMillis();
    }

    public String getLocalAddress() {
        return getRepoConfig().getRepoTypeConfig().getLocalAddress();
    }

    public ProxyDescriptor getProxy() {
        ProxyDescriptor proxyDescriptor = getDescriptor().getProxy();
        return proxyDescriptor != null ? proxyDescriptor : configService.getDescriptor().getDefaultProxy();
    }

    public boolean isDisableProxy() {
        return getDescriptor().isDisableProxy();
    }

    @Override
    public ResourceStreamHandle conditionalRetrieveResource(String relPath, boolean forceRemoteDownload)
            throws IOException {
        //repo1 does not respect conditional get so the following is irrelevant for now.
        /*
        Date modifiedSince;
        if (modifiedSince != null) {
            //Add the if modified since
            String formattedDate = DateUtil.formatDate(modifiedSince);
            method.setRequestHeader("If-Modified-Since", formattedDate);
        }
        if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
            return new NullResourceStreamHandle();
        }
        */
        //Need to do a conditional get by hand - testing a head result last modified date against the current file
        if (!forceRemoteDownload && isStoreArtifactsLocally()) {
            RepoResource cachedResource = getCachedResource(relPath);
            if (cachedResource.isFound()) {
                if (cachedResource.isExpired()) {
                    //Send HEAD
                    RepoResource resource = retrieveInfo(relPath, false/*The relPath refers to files (.gz)*/, null);
                    if (resource.isFound()) {
                        if (cachedResource.getLastModified() > resource.getLastModified()) {
                            return new NullResourceStreamHandle();
                        }
                    }
                } else {
                    return new NullResourceStreamHandle();
                }
            }
        }
        //Do GET
        return downloadResource(relPath);
    }

    @Override
    public ResourceStreamHandle downloadResource(String relPath) throws IOException {
        return downloadResource(relPath, new NullRequestContext(getRepoPath(relPath)));
    }

    private UnifiedEventServiceImpl getUnifiedEventService() {
        unifiedEventService = ContextHelper.get().beanForType(UnifiedEventServiceImpl.class);
        return unifiedEventService;
    }

    @VisibleForTesting
    void sendUnifiedEvent(SupportedUnifiedEvents eventType, RemoteRequestCtx remoteRequestCtx,
            Request request, RepoPath repoPath) {
        if (request == null) {
            log.debug("Skipping {} event - no request available", eventType);
            return;
        }
        try {
            log.debug("Sending {} event via unifiedEventService for {}", eventType, request);
            DispatcherResponse response = getUnifiedEventService().sendArtifactEvent(eventType,
                    (ArtifactoryRequest) request, repoPath);
            log.debug("Received {} response from unifiedEventService: {}", eventType, response);
            if (response != null && response.data() != null && response.data().headers() != null
                    && !response.data().headers().isEmpty()) {
                remoteRequestCtx.getHeaders().putAll(response.data().headers());
            }
        } catch (UnifiedEventException e) {
            if (e.statusCode == HttpStatus.SC_CONFLICT) {
                throw new WorkerStopException(e.getMessage());
            }
            throw e;
        }
    }

    @Override
    public ResourceStreamHandle downloadResource(final String relPath, final RequestContext requestContext)
            throws IOException {
        assert !isOffline() : "Should never be called in offline mode";

        String pathForUrl = convertRequestPathIfNeeded(relPath);
        if (!relPath.equals(pathForUrl)) {
            RepoRequests.logToContext("Remote resource path was translated (%s) due to repository " +
                    "layout differences", pathForUrl);
        }

        Request request = requestContext.getRequest();
        pathForUrl = resolvePathForUrl(pathForUrl, request);

        RepoRequests.logToContext("Appending matrix params to remote request URL");
        pathForUrl += buildRequestMatrixParams(requestContext.getProperties());
        final String fullUrl = appendAndGetUrl(convertRequestPathIfNeeded(pathForUrl), requestContext);
        String encodedUrl = encodeIfNeeded(fullUrl, requestContext);
        String urlWithParams = addQueryParams(encodedUrl, requestContext);

        RepoRequests.logToContext("Using remote request URL - %s", urlWithParams);

        final RepoPath repoPath = InternalRepoPathFactory.create(getKey(), pathForUrl);
        final PluginsAddon pluginAddon = addonsManager.addonByType(PluginsAddon.class);
        final Request requestForPlugins = requestContext.getRequest();
        RepoRequests.logToContext("Executing any BeforeRemoteDownload user plugins that may exist");
        RemoteRequestCtx remoteRequestCtx = new RemoteRequestCtx();
        pluginAddon.execPluginActions(BeforeRemoteDownloadAction.class, remoteRequestCtx, requestForPlugins, repoPath);
        sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_REMOTE_DOWNLOAD, remoteRequestCtx, requestForPlugins, repoPath);
        HttpGet method = new HttpGet(urlWithParams);
        HeadersMultiMap headers = whiteListHeaders(requestContext)
                .updateHeaders(remoteRequestCtx.getHeaders());
        notifyInterceptorsOnBeforeRemoteHttpMethodExecution(method, request, headers,
                RepoPathFactory.create(repoPath.getRepoKey(), relPath));

        if (ConstantValues.remoteConcurrentDownloadsEnabled.getBoolean() && isStoreArtifactsLocally()) {
            ResourceStreamHandle parallelHandle = doParallelDownload(urlWithParams, relPath, requestContext, repoPath,
                    requestForPlugins, pluginAddon, headers);
            if (parallelHandle != null) {
                return parallelHandle;
            }
        }

        // Standard download
        if (log.isTraceEnabled()) {
            RepoRequests.logToContext("Executing GET request to %s  {Accept [ %s ]}",
                    urlWithParams, headers.getAcceptHeadersMediaTypeWithoutOptions().collect(Collectors.toSet()));
        } else {
            RepoRequests.logToContext("Executing GET request to %s", urlWithParams);
        }
        final CloseableHttpResponse response = executeMethod(method, headers);

        StatusLine statusLine = response.getStatusLine();
        int statusCode = statusLine.getStatusCode();
        if (statusCode == HttpStatus.SC_NOT_FOUND) {
            //Not found
            IOUtils.closeQuietly(response);
            RepoRequests.logToContext("Received response status %s - throwing exception", statusLine);
            throw new RemoteRequestException("Unable to find " + urlWithParams, statusCode,
                    statusLine.getReasonPhrase());
        }
        if (statusCode != HttpStatus.SC_OK) {
            IOUtils.closeQuietly(response);
            RepoRequests.logToContext("Received response status %s - throwing exception", statusLine);
            throw new RemoteRequestException("Error fetching " + urlWithParams, statusCode,
                    statusLine.getReasonPhrase());
        }
        blockMismatchingMimeTypes(requestContext.getRequest(), response, fullUrl);
        //Found
        long contentLength = HttpUtils.getContentLength(response);
        logDownloading(urlWithParams, contentLength);
        RepoRequests.logToContext("Downloading content");

        final InputStream is = response.getEntity().getContent();
        verifyContentEncoding(response);
        return new TrafficAwareRemoteResourceStreamHandle(response, urlWithParams, requestForPlugins, pluginAddon,
                repoPath, is);
    }

    private ResourceStreamHandle doParallelDownload(String url, String relPath,
            RequestContext requestContext, RepoPath repoPath, Request request, PluginsAddon pluginAddon,
            HeadersMultiMap headers) {
        ParallelDownloadService service = getParallelDownloadService();
        if (service == null) {
            return null;
        }
        if (!isParallelDownloadEligible(service, relPath, requestContext)) {
            return null;
        }

        HttpGet prepared = new HttpGet(url);
        preExecuteSteps(prepared, headers);
        String preparedUrl = prepared.getURI().toString();

        return tryParallelDownload(service, prepared, preparedUrl, requestContext, request, pluginAddon, repoPath);
    }

    /**
     * Checks whether parallel download should be used for the given request.
     * Returns false if the eligibility check fails or throws an exception (fallback to standard download).
     */
    private boolean isParallelDownloadEligible(ParallelDownloadService service, String relPath,
            RequestContext requestContext) {
        try {
            return service.shouldUseParallelDownload(requestContext, getKey(),
                    getDescriptor().getType(), () -> retrieveInfo(relPath, false, requestContext));
        } catch (Exception e) {
            debugOrWarn(log, "Could not determine parallel download eligibility for '" + relPath
                    + "', falling back to standard download: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Attempts to execute a parallel (multipart) download using a pre-prepared request.
     * Returns null on failure, allowing the caller to fall back to standard download.
     */
    @Nullable
    private ResourceStreamHandle tryParallelDownload(ParallelDownloadService service, HttpGet prepared,
            String preparedUrl, RequestContext requestContext, Request request, PluginsAddon pluginAddon,
            RepoPath repoPath) {
        try {
            HeadersMultiMap multipartHeaders = headersMultiMapFrom(prepared);
            DownloadResult result = service.downloadResourceMultipart(
                    prepared.getURI(), requestContext, getHttpClient(), multipartHeaders);
            if (result != null) {
                TrafficAwareRemoteResourceStreamHandle delegate =
                        new TrafficAwareRemoteResourceStreamHandle(
                                preparedUrl, request, pluginAddon, repoPath, InputStream.nullInputStream(),
                                result.getRemoteIp());
                return new PreChecksummedResourceStreamHandle(result, delegate);
            }
        } catch (Exception e) {
            debugOrWarn(log, "Parallel multipart download failed for '" + preparedUrl
                    + "', falling back to standard download: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Wraps array of {@link Header} from Apache HttpClient into our {@link HeadersMultiMap} to pass to the parallel
     * download service.
     * We won't use array of {@link Header} directly because {@link HeadersMultiMap} is case-insensitive and allows
     * multiple values per name.
     */
    @VisibleForTesting
    static HeadersMultiMap headersMultiMapFrom(HttpRequestBase request) {
        HeadersMultiMap target = new HeadersMultiMap();
        for (Header h : request.getAllHeaders()) {
            if (h != null && h.getName() != null && h.getValue() != null) {
                target.addHeader(h.getName(), h.getValue());
            }
        }
        return target;
    }

    private ParallelDownloadService getParallelDownloadService() {
        try {
            return ContextHelper.get().beanForType(ParallelDownloadService.class);
        } catch (Exception e) {
            log.debug("ParallelDownloadService bean not available: {}", e.getMessage(), e);
            return null;
        }
    }

    static String encodeIfNeeded(String fullUrl, RequestContext requestContext) {
        boolean skipEncoding = false;
        if (requestContext != null) {
            String skipEncodingParam = requestContext.getRequest().getParameter(ArtifactoryRequest.PARAM_SKIP_ENCODING);
            skipEncoding = isNotBlank(skipEncodingParam) && Boolean.parseBoolean(skipEncodingParam);
        }

        String url = skipEncoding ? fullUrl : HttpUtils.encodeUrl(fullUrl);
        url = HttpUtils.escapePlusSymbol(url);
        return url;
    }

    /**
     * Collects InternalArtifactoryRequest headers (explicitly allowed for delegation) to the remote server,
     *
     * @see org.artifactory.api.request.InternalArtifactoryRequest#getDelegationAllowedHeaders()
     */
    @VisibleForTesting
    HeadersMultiMap whiteListHeaders(RequestContext requestContext) {
        HeadersMultiMap headers = new HeadersMultiMap();
        if (requestContext != null) {
            Request request = requestContext.getRequest();
            if (request instanceof InternalArtifactoryRequest) {
                ((InternalArtifactoryRequest) request).getDelegationAllowedHeaders().forEach(
                        key -> headers.addAll(key, requestContext.getRequest().getHeaderValues(key)));
            }
        }
        return headers;
    }

    private void verifyContentEncoding(HttpResponse response) throws IOException {
        if (!ConstantValues.httpAcceptEncodingGzip.getBoolean() && response.getEntity() != null) {
            Header[] contentEncodings = response.getHeaders(HttpHeaders.CONTENT_ENCODING);
            for (Header contentEncoding : contentEncodings) {
                if ("gzip".equalsIgnoreCase(contentEncoding.getValue())) {
                    throw new IOException("Received gzip encoded stream while gzip compressions is disabled");
                }
            }
        }
    }

    private void logDownloading(String fullUrl, long contentLength) {
        if (NamingUtils.isChecksum(fullUrl)) {
            log.debug("{} downloading {} {} ", this, fullUrl,
                    contentLength >= 0 ? StorageUnit.toReadableString(contentLength) : "Unknown content length");
        } else {
            log.info("{} downloading {} {} ", this, fullUrl,
                    contentLength >= 0 ? StorageUnit.toReadableString(contentLength) : "Unknown content length");
        }
    }

    private void logDownloaded(String fullUrl, int status, BandwidthMonitorInputStream bmis) {
        String statusMsg = status == 200 ? "" : "status='" + (status > 0 ? status : "unknown") + "' ";
        String summary =
                statusMsg + StorageUnit.toReadableString(bmis.getTotalBytesRead()) + " at " +
                        StorageUnit.format(StorageUnit.KB.fromBytes(bmis.getBytesPerSec())) + " KB/sec";
        if (NamingUtils.isChecksum(fullUrl)) {
            log.debug("{} downloaded  {} {}", this, fullUrl, summary);
        } else {
            log.info("{} downloaded  {} {}", this, fullUrl, summary);
        }
    }

    /**
     * Executes an HTTP method using the repository client and returns the http response. This method allows to override
     * some of the default headers, note that the ARTIFACTORY_ORIGINATED, the ORIGIN_ARTIFACTORY and the ACCEPT_ENCODING
     * can't be overridden The caller to this class is responsible to close the response.
     *
     * @param method       Method to execute
     * @param extraHeaders Extra headers to add to the remote server request
     * @return The http response.
     * @throws IOException If the repository is offline or if any error occurs during the execution
     */
    public CloseableHttpResponse executeMethod(HttpRequestBase method, HeadersMultiMap extraHeaders)
            throws IOException {
        return this.executeMethod(method, null, extraHeaders);
    }

    /**
     * Executes an HTTP method using the repository client and returns the http response. The caller to this class is
     * responsible to close the response.
     *
     * @param method Method to execute
     * @return The http response.
     * @throws IOException If the repository is offline or if any error occurs during the execution
     */
    @Override
    public CloseableHttpResponse executeMethod(HttpRequestBase method) throws IOException {
        return executeMethod(method, null, null);
    }

    /**
     * Executes an HTTP method using the repository client and returns the http response. The caller to this class is
     * responsible to close the response.
     *
     * @param method  Method to execute
     * @param context The request context for execution state
     * @return The http response.
     * @throws IOException If the repository is offline or if any error occurs during the execution
     */
    public CloseableHttpResponse executeMethod(HttpRequestBase method, @Nullable HttpContext context)
            throws IOException {
        return executeMethod(method, context, null);
    }

    /**
     * Executes an HTTP method using the repository client and returns the http response. This method allows to override
     * some of the default headers, note that the ARTIFACTORY_ORIGINATED, the ORIGIN_ARTIFACTORY and the ACCEPT_ENCODING
     * can't be overridden The caller to this class is responsible to close the response.
     *
     * @param method       Method to execute
     * @param context      The request context for execution state
     * @param extraHeaders Extra headers to add to the remote server request
     * @return The http response.
     * @throws IOException If the repository is offline or if any error occurs during the execution
     */
    private CloseableHttpResponse doExecuteMethod(HttpRequestBase method, @Nullable HttpContext context,
            HeadersMultiMap extraHeaders) throws IOException {
        preExecuteSteps(method, extraHeaders);
        long requestStartTime = System.currentTimeMillis();
        try {
            return getHttpClient().execute(method, context);
        } catch (IOException ioe) {
            // The RemoteRepoRequestResponseInterceptor only runs on the response path, so on
            // IOException (timeout, connection refused, etc.) the 'remote' latency is otherwise
            // lost for both the 'waited.remote' request-log field and the
            // 'http.external.requests{target="remote"}' metric. Record it here explicitly.
            ExternalClientsServiceMetrics.recordRequest("remote", System.currentTimeMillis() - requestStartTime);
            OutboundRequestLogger.logFailingRequestWithoutResponse(getKey(), getUsername(), method, requestStartTime,
                    usesAuthentication, ioe);
            enqueueFailedOutbound(method, requestStartTime, ioe);
            putOffline();
            throw ioe;
        }
    }

    protected CloseableHttpResponse doExecuteMethod(HttpRequestBase method, @Nullable HttpContext context,
            HeadersMultiMap extraHeaders, CloseableHttpClient client) throws IOException {
        preExecuteSteps(method, extraHeaders);
        return client.execute(method, context);
    }

    private void enqueueFailedOutbound(HttpRequestBase method, long requestStartTime, IOException ioe) {
        doEnqueueFailedOutbound(outboundLogService(), getKey(), method, requestStartTime, ioe);
    }

    static void doEnqueueFailedOutbound(OutboundRequestLogService svc, String repoKey,
            HttpRequestBase method, long requestStartTime, IOException ioe) {
        try {
            if (svc == null || !svc.isFeatureEnabled()) {
                return;
            }
            long duration = System.currentTimeMillis() - requestStartTime;
            int statusCode = (ioe instanceof ConnectTimeoutException || ioe instanceof SocketTimeoutException)
                    ? HttpStatus.SC_REQUEST_TIMEOUT : HttpStatus.SC_INTERNAL_SERVER_ERROR;
            svc.enqueueOutbound(OutboundRequestLogRow.builder()
                    .id(0L)
                    .requestTime(requestStartTime)
                    .traceId(MDC.get("uber-trace-id"))
                    .repoKey(repoKey)
                    .httpMethod(method.getMethod().toUpperCase(Locale.ROOT))
                    .requestUrl(method.getURI().toString())
                    .statusCode(statusCode)
                    .durationMs(duration)
                    .build());
        } catch (Exception e) {
            log.debug("Failed to enqueue failed outbound request log entry", e);
        }
    }

    private static OutboundRequestLogService outboundLogService() {
        try {
            return ContextHelper.get().beanForType(OutboundRequestLogService.class);
        } catch (Exception e) {
            log.warn("agentic_api.request_logs.outbound_service_unavailable reason={}", e.getMessage());
            return null;
        }
    }

    private void preExecuteSteps(HttpRequestBase method, HeadersMultiMap extraHeaders) {
        URI outboundUri = method.getURI();
        if (outboundUri != null) {
            checkSsrfGuard(outboundUri.toString(), true);
        }
        interceptRequestIfNotSmartRepo(getKey());
        addDefaultHeadersAndQueryParams(method, extraHeaders);
        doRequestRateLimit(method);
    }

    protected void doRequestRateLimit(HttpRequestBase method) throws RateLimiterException {
        if (rateLimiterService != null) {
            rateLimiterService.acquirePermit(getUrl(), method.getMethod().equals(HttpMethod.HEAD));
            return;
        }
        log.warn("Rate limiter service is null");
    }

    private void interceptRequestIfNotSmartRepo(String repoKey) {
        UpstreamRepoKind repoKind =
                addonsManager.addonByType(EdgeSmartRepoAddon.class).updateUpstreamRepoKind(repoKey);

        if (repoKind == NOT_AVAILABLE) {
            String message = repoKind.getValidationMessage();
            RepoRequests.logToContext("Remote repository currently not available - (%s)", message);
            return;
        }

        if (repoKind.notSmart()) {
            String message = repoKind.getValidationMessage();
            RepoRequests.logToContext("Download denied - (%s)", message);
            throw new ForbiddenException(message);
        }
    }

    /**
     * Executes an HTTP method using the repository client and returns the http response. This method allows to override
     * some of the default headers, note that the ARTIFACTORY_ORIGINATED, the ORIGIN_ARTIFACTORY and the ACCEPT_ENCODING
     * can't be overridden The caller to this class is responsible to close the response.
     *
     * @param method       Method to execute
     * @param context      The request context for execution state
     * @param extraHeaders Extra headers to add to the remote server request
     * @return The http response.
     * @throws IOException If the repository is offline or if any error occurs during the execution
     */
    public final CloseableHttpResponse executeMethod(HttpRequestBase method, @Nullable HttpContext context,
            HeadersMultiMap extraHeaders) throws IOException {
        return interceptResponse(doExecuteMethod(method, context, extraHeaders));
    }

    @VisibleForTesting
    String retrieveOriginRemotePathHeader(String path, @Nullable RequestContext requestContext) {
        if (isOffline()) {
            log.debug("{} Origin remote path header request skipped in offline mode", path);
            return "";
        }
        HttpRequestExecutionContext context = createMethodExecutionContext(path, requestContext);
        CloseableHttpResponse response = null;
        try {
            response = executeMethod(context.method(), context.httpClientContext(), context.headers());
            Header firstHeader = response.getFirstHeader(ORIGIN_REMOTE_PATH);
            String value = firstHeader == null ? "" : firstHeader.getValue();
            log.debug("{} Origin remote path header: {}", path, value);
            return value;
        } catch (Exception e) {
            String exceptionMessage = HttpClientUtils.getFilteredErrorMessage(e);
            log.error("{} Origin remote path header request failed : {}", path, exceptionMessage);
            return "";
        } finally {
            consumeStream(response);
            IOUtils.closeQuietly(response);
        }
    }

    @Override
    public RepoResource retrieveInfo(String path, boolean folder, @Nullable RequestContext requestContext) {
        assert !isOffline() : "Should never be called in offline mode";

        HttpRequestExecutionContext context = createMethodExecutionContext(path, requestContext);

        RepoRequests.logToContext("Executing %s request to %s", context.methodType(), context.urlWithParams());
        CloseableHttpResponse response = null;
        try {
            final PluginsAddon pluginAddon = addonsManager.addonByType(PluginsAddon.class);
            RemoteRequestCtx remoteRequestCtx = new RemoteRequestCtx();
            Request request = requestContext != null ? requestContext.getRequest() : null;
            RepoPath repoPath = InternalRepoPathFactory.create(this.getKey(), path, folder);
            pluginAddon.execPluginActions(BeforeRemoteInfoRequestAction.class, remoteRequestCtx, request, repoPath);
            sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_REMOTE_INFO, remoteRequestCtx, request, repoPath);
            context.headers().updateHeaders(remoteRequestCtx.getHeaders());

            notifyInterceptorsOnBeforeRemoteHttpMethodExecution(context.method(), request, context.headers(), repoPath);

            response = executeMethod(context.method(), context.httpClientContext(), context.headers());
            if (requestContext != null) {
                repoPath = requestContext.alterPath(response, repoPath);
            }
            if (context.useRange() && shouldLogRangeNotSupported(response)) {
                log.trace("Range requests are not supported {} {}", context.method().getMethod(),
                        context.method().getURI());
            }
            return handleGetInfoResponse(repoPath, context.method(), context.fullUrl(), response,
                    context.httpClientContext(), requestContext);
        } catch (IOException e) {
            String exceptionMessage = HttpClientUtils.getFilteredErrorMessage(e);
            RepoRequests.logToContext("Failed to execute %s request: %s", context.methodType(), exceptionMessage);
            throw new RuntimeException(
                    "Failed retrieving resource from " + context.urlWithParams() + ": " + exceptionMessage, e);
        } finally {
            consumeStream(response);
            IOUtils.closeQuietly(response);
        }
    }

    @VisibleForTesting
    HttpRequestExecutionContext createMethodExecutionContext(String path, RequestContext requestContext) {
        boolean replaceHeadWithGet = shouldReplaceHeadWithGet(requestContext);
        String fullUrl = assembleRetrieveInfoUrl(path, requestContext);
        String encodedUrl = encodeIfNeeded(fullUrl, requestContext);
        //TODO: [by yl] Ideally do this in #assembleRetrieveInfoUrl, but encodedUrl full URL brute force encoding
        //breaks pre-encoded query params
        String urlWithParams = addQueryParams(encodedUrl, requestContext);

        HttpRequestBase method;
        String methodType = "HEAD";
        if (replaceHeadWithGet) {
            log.debug("Param " + PARAM_REPLACE_HEAD_IN_RETRIEVE_INFO_WITH_GET + " found in request" +
                    " context, switching HEAD with GET request");
            methodType = "GET";
            method = new HttpGet(urlWithParams);
        } else {
            method = new HttpHead(urlWithParams);
        }
        HttpClientContext httpClientContext = new HttpClientContext();
        HeadersMultiMap headers = whiteListHeaders(requestContext);
        boolean useRange = ConstantValues.useRangeForReplacedGet.getBoolean() && replaceHeadWithGet;
        // We need only headers, adding range header to not retrieve the whole content.
        if (useRange) {
            headers.addHeader(HttpHeaders.RANGE, "bytes=0-0");
        }
        return new HttpRequestExecutionContext(fullUrl, urlWithParams, method, methodType, httpClientContext, headers,
                useRange);
    }

    @VisibleForTesting
    void runWorkers(Action action, RemoteRequestCtx ctx, Request request, RepoPath responseRepoPath) {
        addonsManager.addonByType(WorkersAddon.class)
                .runWorkerAutoInjectedSecurityAndRepoConfig(action, ctx, request, responseRepoPath);
    }

    @VisibleForTesting
    void runWorkersAsync(Action action, RemoteRequestCtx ctx, Request request, RepoPath responseRepoPath) {
        addonsManager.addonByType(WorkersAddon.class)
                .runWorkerAsyncAutoInjectedSecurityAndRepoConfig(action, ctx, request, responseRepoPath);
    }

    @VisibleForTesting
    void runWorker(SupportedUnifiedEvents event, Object... args) {
        addonsManager.addonByType(WorkersAddon.class).run(event, args);
    }

    @Override
    protected RepoResource getInfoFromCache(InternalRequestContext context) {
        return localCacheRepo.getInfo(context);
    }

    /**
     * Synchronize properties can be configured in 2 ways:
     * - The legacy flag
     * {@link RemoteRepoDescriptor#isSynchronizeProperties()}
     * - Smart remote property sync
     * {@link RemoteRepoDescriptor#getContentSynchronisation()}
     * and {@link ContentSynchronisationDescriptor#getProperties()}
     *
     * Even if one of these (or both) is configured, outgoing
     * property queries are blocked for known non-Artifactory
     * upstream URLs via
     * {@link ResearchService#isUpstreamUrlBlockedForPropertySync}.
     */
    @Override
    public boolean isSynchronizeProperties() {
        RemoteRepoConfig repoConfig = getRepoConfig();
        if (!researchService.isRepoConfiguredToSyncProperties(repoConfig)) {
            return false;
        }
        String upstreamUrl = repoConfig.getRepoTypeConfig().getUrl();
        if (researchService.isUpstreamUrlBlockedForPropertySync(upstreamUrl)) {
            if (log.isDebugEnabled()) {
                log.debug("Blocked url detected for repo '{}', sync properties disabled.", repoConfig.getKey());
            }
            return false;
        }
        return true;
    }

    public UpstreamRepoKind getUpstreamRepoKind() {
        return upstreamRepoKindSupplier.get();
    }

    private Supplier<UpstreamRepoKind> createUpstreamRepoKindSupplier() {
        return Suppliers.memoizeWithExpiration(
                () -> researchService.getUpstreamRepoKind(getRepoConfig()),
                ConstantValues.httpUpstreamRepositoryStatusExpirationSeconds.getLong(),
                TimeUnit.SECONDS);
    }

    private boolean shouldReplaceHeadWithGet(@Nullable RequestContext requestContext) {
        boolean replaceHeadWithGet = false;
        if (requestContext != null && requestContext.getRequest() != null) {
            replaceHeadWithGet = parseBoolean(
                    requestContext.getRequest().getParameter(PARAM_REPLACE_HEAD_IN_RETRIEVE_INFO_WITH_GET));
        }
        RemoteRepositoryRepoTypeConfig repoTypeConfig = getRepoConfig().getRepoTypeConfig();
        boolean bypassHeadRequests = repoTypeConfig.isBypassHeadRequests();
        boolean curationRequest = ConstantValues.curationRemoteValidate.getBoolean() &&
                (repoTypeConfig.isSendContext() || repoTypeConfig.isPassThrough());
        return replaceHeadWithGet || bypassHeadRequests || curationRequest;
    }

    private void consumeStream(CloseableHttpResponse response) {
        double maxSizeToConsume = StorageUnit.MB.
                toBytes(ConstantValues.remoteDownloadInVainConsumeLimitInMegaBytes.getInt());
        if (response != null && response.getEntity() != null) {
            if (response.getEntity().getContentLength() <= maxSizeToConsume) {
                EntityUtils.consumeQuietly(response.getEntity());
            }
        }
    }

    @VisibleForTesting
    String addQueryParams(String fullUrl, @Nullable RequestContext context) {
        if (context == null || !shouldPropagateQueryParams()) {
            return fullUrl;
        }

        RepoRequests.logToContext("Appending query params to remote request URL");
        Map<String, String[]> params = context.getRequest().getParameters();

        if (!hasQueryParamsToPropagate(params)) {
            return fullUrl;
        }

        String queryParamsString = buildQueryParamsString(params);
        queryParamsString = adjustPropagatedQueryStringForBridgeFullUrl(fullUrl, queryParamsString);
        return fullUrl + queryParamsString;
    }

    /**
     * When {@code fullUrl} is a JFrog Bridge URL and already has a query string (e.g. {@code ?propertiesXml} on
     * storage API property requests), propagated parameters must be prefixed with {@code &} instead of {@code ?}.
     */
    @VisibleForTesting
    String adjustPropagatedQueryStringForBridgeFullUrl(String fullUrl, String queryParamsString) {
        if (UrlVerifier.isBridgeUrl(fullUrl) && fullUrl.indexOf('?') >= 0
                && queryParamsString.startsWith(QUERY_START)) {
            return QUERY_SEPARATOR + queryParamsString.substring(QUERY_START.length());
        }
        return queryParamsString;
    }

    private boolean shouldPropagateQueryParams() {
        return getRepoConfig().getRepoTypeConfig().isPropagateQueryParams();
    }

    /**
     * Checks if the given map of query parameters contains any parameters to propagate, ignoring the
     * {@link ArtifactRestConstants#TRACE_PARAM} parameter.
     *
     * @param params the map of query parameters
     * @return true if there are query parameters to propagate, false otherwise
     */
    private boolean hasQueryParamsToPropagate(Map<String, String[]> params) {
        if (params.isEmpty()) {
            return false;
        }
        if (params.size() == 1) {
            return !params.containsKey(ArtifactRestConstants.TRACE_PARAM);
        }
        return true;
    }

    private String buildQueryParamsString(Map<String, String[]> params) {
        return params.entrySet().stream()
                .filter(entry -> !ArtifactRestConstants.TRACE_PARAM.equals(entry.getKey()))
                .flatMap(entry -> Arrays.stream(entry.getValue())
                        .map(paramValue -> formatQueryParam(entry.getKey(), paramValue)))
                .collect(Collectors.joining(QUERY_SEPARATOR, QUERY_START, EMPTY));
    }

    private String formatQueryParam(String key, String value) {
        return key + KEY_VALUE_SEPARATOR + (value != null ? urlEncode(value) : EMPTY);
    }

    protected String assembleRetrieveInfoUrl(String path, RequestContext context) {
        String pathForUrl = convertRequestPathIfNeeded(path);
        if (!path.equals(pathForUrl)) {
            RepoRequests.logToContext("Remote resource path was translated (%s) due to repository " +
                    "layout differences", pathForUrl);
        }
        boolean validContext = context != null;
        if (validContext) {
            Request request = context.getRequest();
            pathForUrl = resolvePathForUrl(pathForUrl, request);
        } else {
            validatePathAllowed(pathForUrl);
        }

        String fullUrl = appendAndGetUrl(pathForUrl, context);
        if (validContext) {
            RepoRequests.logToContext("Appending matrix params to remote request URL");
            Properties properties = context.getProperties();
            fullUrl += buildRequestMatrixParams(properties);
        }
        RepoRequests.logToContext("Using remote request URL - %s", fullUrl);
        return fullUrl;
    }

    String resolvePathForUrl(String pathForUrl, Request request) {
        String alternativeUrl = resolveAlternativeUrl(request);
        if (alternativeUrl != null) {
            return alternativeUrl;
        }
        String trailingSlash = "/";
        if (ConstantValues.directoryRequestQueryParamsEnabled.getBoolean() &&
                request instanceof ArtifactoryRequest artifactoryRequest
                && artifactoryRequest.isDirectoryRequest()
                && !pathForUrl.endsWith(trailingSlash)) {
            pathForUrl += trailingSlash;
        }
        validatePathAllowed(pathForUrl);
        return pathForUrl;
    }

    /**
     * Returns the {@code alternativeRemoteDownloadUrl} from the request when the request is non-null,
     * the URL is present, and the query-param rewrite is permitted for the caller. Returns {@code null}
     * when the original path should be used instead.
     *
     * @throws IllegalUrlPathException if the SSRF guard is active and blocks the alternative URL.
     */
    private String resolveAlternativeUrl(Request request) {
        if (request == null) {
            return null;
        }
        String alternativeRemoteDownloadUrl =
                request.getParameter(ArtifactoryHeaders.PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL);
        if (isBlank(alternativeRemoteDownloadUrl)) {
            return null;
        }
        if (!isAllowedQueryParamRewrite(request)) {
            log.warn("Alternative remote download url {} ignored", alternativeRemoteDownloadUrl);
            return null;
        }
        checkSsrfGuard(alternativeRemoteDownloadUrl, false);
        RepoRequests.logToContext("Request contains alternative remote resource path ({}=%s)",
                ArtifactoryHeaders.PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL, alternativeRemoteDownloadUrl);
        return alternativeRemoteDownloadUrl;
    }

    /**
     * Outbound SSRF guard. With {@code floorOnly=false} (alternative-download-url path) it applies the full
     * {@link UrlVerifier#isOutboundFetchBlocked} policy; with {@code floorOnly=true} (the per-request choke point in
     * {@link #preExecuteSteps}) it applies only the loopback + JFrog-internal floor so private-range mirrors are not
     * disrupted. Governed by {@code httpRepoOutboundSsrfGuard*} flags (shadow mode logs without blocking).
     */
    private void checkSsrfGuard(String url, boolean floorOnly) {
        boolean guardEnabled = ConstantValues.httpRepoOutboundSsrfGuardEnabled.getBoolean();
        boolean shadowMode = ConstantValues.httpRepoOutboundSsrfGuardShadowMode.getBoolean();
        if (!guardEnabled && !shadowMode) {
            return;
        }
        ArtifactoryContext context = ContextHelper.get();
        if (context == null) {
            return;
        }
        UrlVerifier urlVerifier = context.beanForType(UrlVerifier.class);
        boolean blocked = floorOnly
                ? urlVerifier.isOutboundTargetBlocked(url, getKey())
                : urlVerifier.isOutboundFetchBlocked(url, getKey());
        if (!blocked) {
            return;
        }
        if (guardEnabled) {
            log.warn("Blocking outbound fetch for repo '{}' — SSRF guard rejected URL: {}", getKey(), url);
            throw new IllegalUrlPathException("Outbound fetch URL blocked by SSRF guard: " + url);
        }
        log.warn("[SSRF-guard shadow] Would have blocked outbound fetch for repo '{}' — URL: {}", getKey(), url);
    }

    /**
     * Notice: for use with HEAD method, no content is expected in the response. Process the remote repository's
     * response and construct a repository resource.
     *
     * @param repoPath    of requested resource
     * @param method      executed {@link org.apache.http.client.methods.HttpHead} from which to process the
     *                    response.
     * @param downloadUrl Original download url (not encoded, without url params) for mime type verification
     * @param response    The response to the get info request
     */
    protected RepoResource handleGetInfoResponse(RepoPath repoPath, HttpRequestBase method, String downloadUrl,
            CloseableHttpResponse response, @Nullable HttpClientContext context, RequestContext requestContext) {
        int statusCode = response.getStatusLine().getStatusCode();

        BlockedRepoResource blockedRepoResource = assertNotBlockedByCurationOrXray(repoPath, response, statusCode);
        if (blockedRepoResource != null) {
            return blockedRepoResource;
        }
        if (shouldReturnUnfoundResource(repoPath, response, context, requestContext, statusCode)) {
            return returnUnfoundResource(repoPath, response);
        }

        long contentLength = HttpUtils.getContentLength(response);
        if (contentLength != -1) {
            RepoRequests.logToContext("Found remote resource with content length - %s", contentLength);
        }

        // if status is 204 and length is not 0 then the remote server is doing something wrong
        if (statusCode == HttpStatus.SC_NO_CONTENT && contentLength > 0) {
            // send back unfound resource with 404 status
            RepoRequests.logToContext("Received status {} (message: %s) on remote info request - returning unfound " +
                    "resource", statusCode, response.getStatusLine().getReasonPhrase());
            return new UnfoundRepoResource(repoPath, response.getStatusLine().getReasonPhrase());
        }
        try {
            blockMismatchingMimeTypes(requestContext != null ? requestContext.getRequest() : null, response,
                    downloadUrl);
        } catch (RemoteRequestException rre) {
            log.debug("", rre);
            //Mismatching mime types detected and blocked
            return addRejectedResourceToMissedCache(repoPath, rre.getMessage(), rre.getRemoteReturnCode());
        }

        String originRemotePath = getOriginRemotePath(downloadUrl, response);
        response.setHeader(ORIGIN_REMOTE_PATH, originRemotePath);

        CurationCachedPackageStatus packageStatus =
                checkBlockedByCuration(requestContext != null ? requestContext.getRequest() : null, repoPath,
                        originRemotePath, response.getAllHeaders(), false);
        if (!packageStatus.status().permitted()) {
            return new BlockedRepoResource(repoPath, packageStatus.status().message(),
                    BlockedRepoResource.BlockedType.CURATION);
        }

        Set<ChecksumInfo> checksums = HttpUtils.getChecksums(response);
        if (!ConstantValues.saveGetResource.getBoolean()) {
            retrieveChecksumsFromInterceptors(repoPath, response, checksums);
        }

        String originalPath = repoPath.getPath();
        String filename = getFilename(method, originalPath);
        if (isNotBlank(filename)) {
            RepoRequests.logToContext("Found remote resource with filename header - %s", filename);
            if (NamingUtils.isMetadata(originalPath)) {
                String originalPathStrippedOfMetadata = NamingUtils.getMetadataParentPath(originalPath);
                String originalPathWithMetadataNameFromHeader =
                        NamingUtils.getMetadataPath(originalPathStrippedOfMetadata, filename);
                repoPath = InternalRepoPathFactory.create(repoPath.getRepoKey(),
                        originalPathWithMetadataNameFromHeader);
            } else {
                repoPath = InternalRepoPathFactory.create(repoPath.getParent(), filename);
            }
        }

        return createRemoteResourceFromResponse(repoPath, response, checksums, packageStatus.payload());
    }

    private String getOriginRemotePath(String downloadUrl, CloseableHttpResponse response) {
        String originRemotePath = downloadUrl;
        if (getSmartRepoService().isSmartRepo(this.getKey())) {
            Header firstHeader = response.getFirstHeader(ORIGIN_REMOTE_PATH);
            String value = firstHeader == null ? "" : firstHeader.getValue();
            originRemotePath = normalizeOriginRemotePath(value, downloadUrl);
            if (isBlank(value)) {
                log.warn("Origin remote path is empty for download url {}", downloadUrl);
            }
        }
        return originRemotePath;
    }

    private BlockedRepoResource assertNotBlockedByCurationOrXray(RepoPath repoPath, CloseableHttpResponse response,
            int statusCode) {
        BlockedRepoResource curationBlock = assertNotBlockedByCuration(repoPath, response, statusCode);
        return curationBlock != null ? curationBlock : assertNotBlockedByXray(repoPath, response, statusCode);
    }

    private BlockedRepoResource assertNotBlockedByXray(RepoPath repoPath, CloseableHttpResponse response,
            int statusCode) {
        if (statusCode == HttpStatus.SC_FORBIDDEN && ConstantValues.xrayRemoteValidate.getBoolean()
                && response.containsHeader(XRAY_ORIGIN)) {
            return new BlockedRepoResource(repoPath, "Blocked by Xray", BlockedRepoResource.BlockedType.XRAY);
        }
        return null;
    }

    @Nullable
    @VisibleForTesting
    BlockedRepoResource assertNotBlockedByCuration(RepoPath repoPath, CloseableHttpResponse response,
            int statusCode) {
        if (statusCode == HttpStatus.SC_FORBIDDEN && response.containsHeader(ARTIFACTORY_ID)) {
            try {
                if (ConstantValues.curationRemoteValidate.getBoolean()) {
                    String content = extractContent(response);
                    BlockedRepoResource blockedRepoResource =
                            extractBlockedRepoResourceBasedOnMessage(repoPath, content);
                    if (blockedRepoResource != null) {
                        return blockedRepoResource;
                    }
                    if (ConstantValues.curationBlockedBasedOnHeader.getBoolean()) {
                        return extractBlockedRepoResource(repoPath, response, content);
                    }
                }
            } catch (Exception e) {
                log.debug("Unable to verify forbidden status, continuing...", e);
            }
        }
        return null;
    }

    private static String extractContent(CloseableHttpResponse response) throws IOException {
        String content = null;
        if (response.getEntity() != null && response.getEntity().getContent() != null) {
            content = IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset());
        }
        return content;
    }

    @VisibleForTesting
    BlockedRepoResource extractBlockedRepoResource(RepoPath repoPath, CloseableHttpResponse response,
            String content) {
        String message = extractCurationMessage(response, content);
        if (hasCurationOriginHeader(response)) {
            return new BlockedRepoResource(repoPath, message, BlockedRepoResource.BlockedType.CURATION);
        }
        return null;
    }

    private String extractCurationMessage(CloseableHttpResponse response, String content) {
        String message = null;
        JsonNode messageNode = null;
        if (content != null) {
            messageNode = extractMessageNodeFromContent(content);
            message = extractMessageFromNode(messageNode);
        }
        if (message == null && response.getAllHeaders() != null) {
            message = extractCurationMessageFromHeaders(response);
            if (message == null) {
                message = extractMessageIfCurationOriginHeaderExists(response, messageNode);
            }
        }
        return message;
    }

    private JsonNode extractMessageNodeFromContent(String content) {
        if (content == null) {
            return null;
        }
        JsonNode messageNode = null;
        JsonNode root = JsonUtils.getInstance().readTree(content);
        JsonNode errors = root.get("errors");
        if (errors != null && errors.isArray() && !errors.isEmpty()) {
            final JsonNode firstError = errors.get(0);
            messageNode = firstError.get("message") == null
                    ? firstError.get("detail")
                    : firstError.get("message");
        }
        return messageNode;
    }

    private String extractMessageFromNode(JsonNode messageNode) {
        if (messageNode != null) {
            final String messageText = messageNode.asText();
            if (messageText.toLowerCase().contains("curat")) {
                return messageText;
            }
        }
        return null;
    }

    private String extractCurationMessageFromHeaders(CloseableHttpResponse response) {
        String msgInHeader = Arrays.stream(response.getAllHeaders())
                .map(NameValuePair::getValue)
                .filter(value -> value != null && value.toLowerCase().contains(" curat"))
                .findFirst()
                .orElse(null);
        if (msgInHeader != null) {
            log.debug("Curation message found in header: {}", msgInHeader);
        }
        return msgInHeader;
    }

    private String extractMessageIfCurationOriginHeaderExists(CloseableHttpResponse response,
            JsonNode messageNode) {
        if (hasCurationOriginHeader(response) && messageNode != null) {
            return messageNode.asText();
        }
        return null;
    }

    @Nullable
    BlockedRepoResource extractBlockedRepoResourceBasedOnMessage(RepoPath repoPath, String content) {
        final JsonNode errorMessageNode = extractMessageNodeFromContent(content);
        if (errorMessageNode != null) {
            final String messageText = errorMessageNode.asText();
            if (messageText.toLowerCase().contains("curat")) {
                return new BlockedRepoResource(repoPath, messageText, BlockedRepoResource.BlockedType.CURATION);
            }
        } else {
            log.debug("Curation error message not found in response for repo path {}. " +
                    "Response content: {}", repoPath.toPath(), content);
        }
        return null;
    }

    private boolean hasCurationOriginHeader(CloseableHttpResponse response) {
        return response.containsHeader(CURATION_ORIGIN)
                && parseBoolean(response.getFirstHeader(CURATION_ORIGIN).getValue());
    }

    @Nonnull
    protected UnfoundRepoResource returnUnfoundResource(RepoPath repoPath, CloseableHttpResponse response) {
        return new UnfoundRepoResource(repoPath, response.getStatusLine().getReasonPhrase());
    }

    private void validatePathAllowed(String path) {
        if (HttpUtils.isValidUrl(path)) {
            log.warn("Download URL contains forbidden redirect to: {}", path);
            throw new IllegalUrlPathException("Download URL contains forbidden redirect to: " + path);
        }
        log.trace("Valid path, doesn't contains URL {}", path);
    }

    @VisibleForTesting
    boolean shouldReturnUnfoundResource(RepoPath repoPath, CloseableHttpResponse response,
            @Nullable HttpClientContext context, RequestContext requestContext, int statusCode) {
        if (statusCode == HttpStatus.SC_NOT_FOUND) {
            RepoRequests.logToContext("Received status 404 (message: %s) on remote info request - " +
                    "returning unfound resource", response.getStatusLine().getReasonPhrase());
            return true;
        }

        if (!isDisableFolderRedirectAssertion(requestContext)
                && !isFileDownloadResponse(response)
                && isRedirectToFolder(context)) {
            response.setReasonPhrase("The path should be a directory not a file");
            log.info("Remote info request was redirected to a directory. Repo: {} Path: {}", repoPath.getRepoKey(),
                    repoPath.getPath());
            return true;
        }

        boolean useRangeEnabled = ConstantValues.useRangeForReplacedGet.getBoolean();
        boolean successResponse = statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_NO_CONTENT;
        boolean rangeSuccessResponse = successResponse || statusCode == HttpStatus.SC_PARTIAL_CONTENT ||
                statusCode == HttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE;
        if (successResponse) {
            return false;
        }
        if (useRangeEnabled && rangeSuccessResponse) {
            return false;
        }

        RepoRequests.logToContext("Received status {} (message: %s) on remote info request - " +
                "returning unfound resource", statusCode, response.getStatusLine().getReasonPhrase());
        return true;
    }

    private boolean isDisableFolderRedirectAssertion(RequestContext context) {
        if (context != null) {
            String disableFolderRedirectAssertion = context.getRequest().getParameter(PARAM_FOLDER_REDIRECT_ASSERTION);
            if (isNotBlank(disableFolderRedirectAssertion) && Boolean.parseBoolean(disableFolderRedirectAssertion)) {
                // Do not perform in case of parameter provided
                RepoRequests.logToContext("Folder redirect assertion is disabled for internal download request");
                return true;
            }
        }
        return false;
    }

    private boolean isRedirectToFolder(HttpClientContext context) {
        if (context != null) {
            // if redirected, check that the last redirect URL doesn't end with '/' (we assume those are directory
            //paths)
            List<URI> redirects = context.getRedirectLocations();
            if (CollectionUtils.notNullOrEmpty(redirects)) {
                URI lastDestination = redirects.get(redirects.size() - 1);
                return lastDestination != null && lastDestination.getPath().endsWith("/");
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when {@code response} advertises an explicit file download via the
     * standard {@code Content-Disposition: attachment} header. Used by
     * {@link #shouldReturnUnfoundResource} to bypass the trailing-slash folder heuristic for
     * legitimate redirect-served downloads (RTDEV-65069).
     * <p>
     * Matches the RFC 6266 {@code disposition-type} token case-insensitively and tolerates
     * the {@code "attachment; filename=..."} form that real-world servers send.
     */
    private boolean isFileDownloadResponse(CloseableHttpResponse response) {
        if (response == null) {
            return false;
        }
        Header contentDisposition = response.getFirstHeader("Content-Disposition");
        if (contentDisposition == null || contentDisposition.getValue() == null) {
            return false;
        }
        String value = contentDisposition.getValue().trim();
        int semicolon = value.indexOf(';');
        String token = (semicolon >= 0 ? value.substring(0, semicolon) : value).trim();
        return "attachment".equalsIgnoreCase(token);
    }

    protected final CloseableHttpClient createHttpClient(int maxTotalConnections, boolean isEnableTokenAuthentication,
            boolean isNormalize, String mbeanSuffix) {
        String password = CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), getPassword());
        HttpClientConfigurator configurator = new HttpClientConfigurator();
        configurator.handleGzipResponse(handleGzipResponse)
                .hostFromUrl(getUrl())
                .connectionTimeout(getSocketTimeoutMillis())
                .socketTimeout(getSocketTimeoutMillis())
                .retry(RETRY_COUNT, REQUEST_SENT_RETRY_ENABLED)
                .localAddress(getLocalAddress())
                .authentication(getUsername(), password, isAllowAnyHostAuth(), addAdditionalAuthScopes())
                .normalize(isNormalize)
                .redirectStrategy(getRedirectStrategy())
                .enableCookieManagement(isEnableCookieManagement());
        String certificateToUse = getRepoConfig().getRepoTypeConfig().getClientTlsCertificate();
        boolean usesCertificate = false;
        if (isNotBlank(certificateToUse)) {
            KeyStoreProvider keyStoreProvider = ClientCertificateUtils.getKeyStoreProvider();
            if (keyStoreProvider != null) {
                configurator.clientCertKeyStoreProvider(keyStoreProvider);
                configurator.clientCertAlias(ArtifactWebstartAddon.SSL_CERT_ALIAS_PREFIX + certificateToUse);
                usesCertificate = true;
            }
        }
        usesAuthentication = usesCertificate || StringUtils.isNotBlank(password);
        RemoteRepoRequestResponseInterceptor remoteRepoRequestResponseInterceptor =
                new RemoteRepoRequestResponseInterceptor(getKey(), getUsername(),
                        usesAuthentication, getRepoConfig().getPackageType().getType(), getUrl());
        HttpRepoResearchRequestResponseInterceptor requestResponseInterceptor =
                new HttpRepoResearchRequestResponseInterceptor(getKey());
        addSrcTenantIdInterceptorIfSmartRepo(configurator);
        return configurator
                .addRequestInterceptor(requestResponseInterceptor)
                .addResponseInterceptor(requestResponseInterceptor)
                .addRequestInterceptor(remoteRepoRequestResponseInterceptor)
                .addResponseInterceptor(remoteRepoRequestResponseInterceptor)
                .maxTotalConnections(maxTotalConnections)
                .enableTokenAuthentication(isEnableTokenAuthentication, getKey(), getHttpRequestInterceptor(), password,
                        addAdditionalAuthScopes())
                .handleGzipResponse(handleGzipResponse)
                .proxy(isDisableProxy() ? null : getProxy())
                .registerMBean(getMBeanName(mbeanSuffix))
                .build();
    }

    protected HttpRequestInterceptor getHttpRequestInterceptor() {
        return new PreemptiveBearerTokenAuthInterceptor(
                new ArtifactoryBearerScheme(this.getDescriptor().getKey(), this.getDescriptor().getType()));
    }

    /**
     * Attaches the {@link SrcTenantIdHeaderInterceptor} to the client only when this is a smart remote repo.
     * The smart-repo check runs once here (at client-build time) rather than on every outbound request.
     */
    @VisibleForTesting
    void addSrcTenantIdInterceptorIfSmartRepo(HttpClientConfigurator configurator) {
        if (getSmartRepoService().isSmartRepo(getKey())) {
            configurator.addRequestInterceptor(new SrcTenantIdHeaderInterceptor());
        }
    }


    protected RedirectStrategy getRedirectStrategy() {
        return wrapWithRedirectSsrfGuard(buildBaseRedirectStrategy());
    }

    private RedirectStrategy buildBaseRedirectStrategy() {
        boolean fallbackToDefault = !AolUtils.isAol() && ConstantValues.ignoreRedirectStrategy.getBoolean();
        if (fallbackToDefault) {
            return new DefaultRedirectStrategy();
        }
        SysConfig sysConfig = ArtifactoryHome.get().getSysConfig();
        DenylistValidator denylistValidator =
                new DenylistValidator(new DenylistLoader(sysConfig,
                        "artifactory.http.remote.client.redirect.denylist.addresses").getDenylist(),
                        ContextHelper.get().beanForType(DenyListCacheHolderService.class).getDenyListRedirectCache(),
                        sysConfig.getBoolean("artifactory.http.remote.client.redirect.shallow.block", true));
        // in some cases the customers are misconfiguring the proxies on their on-prem envs.
        // so to allow them to still be able to perform actions without failing them we will have a
        // feature flag that allows skipping the JFrogDefaultRedirectStrategy if they want to do it and they are not
        // on SaaS
        denylistValidator.setShadowMode(
                sysConfig.getBoolean("artifactory.http.remote.client.redirect.shadow.mode.block", true));
        denylistValidator.setNeedBlockPrivateRange(
                sysConfig.getBoolean("artifactory.http.remote.client.redirect.block.private.range", true));
        denylistValidator.setNeedBlockLoopback(
                sysConfig.getBoolean("artifactory.http.remote.client.redirect.block.loopback", true));
        return new JFrogDefaultRedirectStrategy(denylistValidator);
    }

    private RedirectStrategy wrapWithRedirectSsrfGuard(RedirectStrategy delegate) {
        if (!ConstantValues.httpRepoRedirectSsrfGuardEnabled.getBoolean()
                && !ConstantValues.httpRepoRedirectSsrfGuardShadowMode.getBoolean()) {
            return delegate;
        }
        UrlVerifier urlVerifier = ContextHelper.get().beanForType(UrlVerifier.class);
        return new UrlVerifierRedirectStrategy(delegate, urlVerifier, getKey());
    }

    private String getMBeanName(String suffix) {
        return suffix == null ? getKey() : String.format("%s-%s", getKey(), suffix);
    }

    protected List<String> addAdditionalAuthScopes() {
        String url = getUrl();
        if (isNotBlank(url)) {
            try {
                String host = new URL(url).getHost();
                switch (host) {
                    case GITHUB_HOST -> {
                        return List.of(GITHUB_API_HOST, GITHUB_RAW_HOST);
                    }
                    case BITBUCKET_HOST -> {
                        return List.of(BITBUCKET_API_HOST);
                    }
                    case GITLAB_HOST -> {
                        return List.of(GITLAB_API_HOST);
                    }
                    default -> {
                        // Intentional fall-through for unknown hosts
                    }
                }
                if (host.endsWith(GHE_HOST)) {
                    String GHE_API_HOST = "api." + host;
                    String GHE_RAW_HOST = "raw." + host;
                    return List.of(GHE_API_HOST, GHE_RAW_HOST);
                }
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Cannot parse the url " + url, e);
            }
        }
        // TODO: 02/02/2022 this should not be here, we should either put this as main config on the remote repo, or to
        //  provide the additional scopes from outside
        String cargoRegistryUrl =
                getRepoConfig().getPackageTypeConfig().get(GitPackageTypeConfigMapper.GIT_REGISTRY_URL);
        if (isNotBlank(cargoRegistryUrl)) {
            return List.of(cargoRegistryUrl);
        }
        return List.of();
    }

    protected RepoResource getCachedResource(String relPath) {
        LocalCacheRepo cache = getLocalCacheRepo();
        final NullRequestContext context = new NullRequestContext(getRepoPath(relPath));
        return requireNonNull(cache).getInfo(context);
    }

    /**
     * Adds default headers and extra headers to the HttpRequest method The extra headers are unique headers that should
     * be added to the remote server request according to special requirement example : user adds extra headers through
     * the User Plugin (BeforeRemoteDownloadAction)
     *
     * @param method       Method to execute
     * @param extraHeaders Extra headers to add to the remote server request
     */
    @SuppressWarnings({"deprecation"})
    private void addDefaultHeadersAndQueryParams(HttpRequestBase method, HeadersMultiMap extraHeaders) {
        //Explicitly force keep alive
        method.setHeader(HttpHeaders.CONNECTION, "Keep-Alive");

        //Add the current requester host id
        Set<String> originatedHeaders = RepoRequests.getOriginatedHeaders();
        for (String originatedHeader : originatedHeaders) {
            method.addHeader(ARTIFACTORY_ORIGINATED, originatedHeader);
        }
        String hostId = ContextHelper.get().beanForType(AddonsManager.class).addonByType(
                HaCommonAddon.class).getHostId();
        // Add the extra headers to the remote request
        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entries()) {
                boolean isReservedKey = ARTIFACTORY_ORIGINATED.equals(entry.getKey()) ||
                        ORIGIN_ARTIFACTORY.equals(entry.getKey()) ||
                        ACCEPT_ENCODING.equalsIgnoreCase(entry.getKey());
                if (!isReservedKey) {
                    method.addHeader(entry.getKey(), entry.getValue());
                }
            }
        }
        // Add the default artifactory headers, those headers will always override the existing headers if they
        //already exist
        method.addHeader(ARTIFACTORY_ORIGINATED, hostId);

        //For backwards compatibility
        method.setHeader(ORIGIN_ARTIFACTORY, hostId);

        //Set gzip encoding
        if (handleGzipResponse) {
            method.addHeader(ACCEPT_ENCODING, "gzip");
        }

        // Set custom query params
        String queryParams = getRepoConfig().getRepoTypeConfig().getQueryParams();
        if (isNotBlank(queryParams)) {
            String url = method.getURI().toString();
            if (url.contains("?")) {
                url += "&";
            } else {
                url += "?";
            }
            url += BasicHttpUtils.encodeQuery(queryParams);
            method.setURI(URI.create(url));
        }

        CustomHttpHeaderUtils.addCustomHttpHeadersToRequest(method, getKey(),
                getRepoConfig().getRepoTypeConfig().getCustomHttpHeaders());
    }

    private String getFilename(HttpRequestBase method, String originalPath) {
        // Skip filename parsing if we are not dealing with latest maven non-unique snapshot request
        if (!isRequestForLatestMavenSnapshot(originalPath)) {
            return null;
        }

        // Try our custom X-Artifactory-Filename header
        Header filenameHeader = method.getFirstHeader(ArtifactoryRequest.FILE_NAME);
        if (filenameHeader != null) {
            String filenameString = filenameHeader.getValue();
            try {
                return URLDecoder.decode(filenameString, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                log.warn("Unable to decode '{}' header '{}', returning un-encoded value.",
                        ArtifactoryRequest.FILE_NAME, filenameString);
                return filenameString;
            }
        }

        // Didn't find any filename, return null
        return null;
    }

    private boolean isRequestForLatestMavenSnapshot(String originalPath) {
        if (ConstantValues.requestDisableVersionTokens.getBoolean()) {
            return false;
        }

        String repoLayoutRef = getRepoConfig().getBaseConfig().getRepoLayoutRef();
        if (repoLayoutRef != null) {
            RepoLayout repoLayout = configService.getDescriptor().getRepoLayout(repoLayoutRef);
            if (!RepoLayoutUtils.isDefaultM2(repoLayout)) {
                return false;
            }
        }

        return MavenNaming.isNonUniqueSnapshot(originalPath);
    }

    @Override
    public List<RemoteItem> getChildUrls(String dirUrl) throws IOException {
        if (remoteBrowser == null) {
            initRemoteRepositoryBrowser();
        }
        return remoteBrowser.listContent(dirUrl);
    }

    /**
     * Converts the given path to the remote repo's layout if defined
     *
     * @param path Path to convert
     * @return Converted path if required and conversion was successful, given path if not
     */
    public String convertRequestPathIfNeeded(String path) {
        String repoLayoutRef = getRepoConfig().getBaseConfig().getRepoLayoutRef();
        // TODO: 08/03/2022 what is layout not found?
        var repoLayout = configService.getDescriptor().getRepoLayout(repoLayoutRef);
        String remoteRepoLayoutRef = getRepoConfig().getRepoTypeConfig().getRemoteRepoLayoutRef();
        var remoteRepoLayout = configService.getDescriptor().getRepoLayout(remoteRepoLayoutRef);
        return layoutsCoreAddon.translateArtifactPath(repoLayout, remoteRepoLayout, path);
    }

    @Override
    protected void putOffline() {
        long assumedOfflinePeriodSecs = getRepoConfig().getRepoTypeConfig().getAssumedOfflinePeriodSecs();
        if (assumedOfflinePeriodSecs <= 0) {
            return;
        }
        onlineMonitorService.createOfflineRepoMonitor(this);
    }

    @Override
    public void resetAssumedOffline() {
        log.debug("Resetting assumed offline status");
        assumedOffline.getAndSet(false);
    }

    @Override
    public void setAssumedOffline() {
        log.debug("Setting repo {} to assumed offline status", getKey());
        assumedOffline.getAndSet(true);
    }

    private void stopOfflineCheckThread() {
        onlineMonitorService.stopMonitorForRepo(this.getKey());
    }

    @Override
    public CloseableHttpClient getHttpClient() {
        if (isConfiguredOffline()) {
            throw new IllegalStateException("Repo " + this.getKey() + " is offline. Cannot use the HTTP client.");
        }
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    boolean enableTokenAuthentication = isEnableTokenAuthentication();
                    this.client = createHttpClient(ConstantValues.httpClientMaxTotalConnections.getInt(),
                            enableTokenAuthentication, isNeedToNormalize(getRepoConfig()), null);
                }
            }
        }
        return client;
    }

    private String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("Failed to url encode: " + s, e);
            return s;
        }
    }

    /**
     * If this repo has the 'block mismatching mimetypes' flag lit and the request passed along the expected mime type,
     * verify that the returned mime type matches the expected one.  If no header was passed an attempt is made
     * to match both types by the requested resource's extension instead.
     *
     * If there's a mismatch and the response's mime type is in the repo's block list (either default
     * {@link ConstantValues#blockedMismatchingMimeTypes} in the system properties or the repo's
     * {@link RemoteRepoDescriptor}.mismatchingMimeTypesOverrideList)
     * this method will fail the download with Exception.
     *
     * @param request     Request to fetch remote resource (whether it came internally or from a client)
     * @param response    Response from remote resource to match mime type against
     * @param downloadUrl For logging
     */
    private void blockMismatchingMimeTypes(@Nullable Request request, CloseableHttpResponse response,
            String downloadUrl)
            throws RemoteRequestException {
        //Found -> check if received mime type matches the requested one
        if (!shouldVerifyMimeType(response)) {
            return;
        }
        String responseContentType = getMimeTypeFromHeader(response.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue())
                .toLowerCase();
        if (responseContentType.equals("*/*") || responseContentType.equals("application/octet-stream")) {
            log.trace("Response content type is {}: skipping mime type checks", responseContentType);
            return;
        }
        Set<String> acceptableMimeTypes = getAcceptableMimeTypes(request, downloadUrl);
        if (acceptableMimeTypes.contains("*/*") || acceptableMimeTypes.contains("application/octet-stream")) {
            log.trace("Accepts header allows everything, skipping mime type checks");
            return;
        }
        if (shouldBlockMimeType(responseContentType, acceptableMimeTypes)) {
            try {
                response.close();
            } catch (Exception e) {
                log.debug("Failed to release response stream before blocking mime type mismatch: ", e);
            }
            String err = "Error fetching " + downloadUrl + ": Mismatching mime types found in request: "
                    + acceptableMimeTypes + " and response: " + responseContentType + ", and this repo blocks "
                    + responseContentType + " on mismatch";
            RepoRequests.logToContext(err);
            throw new RemoteRequestException(err, HttpStatus.SC_CONFLICT, null);
        }
    }

    /**
     * @param request     the request that goes to the resource
     * @param downloadUrl actual download url (if it was modified, / translated etc.), without query params
     * @return a unique list of acceptable mime types for this request either by the request's 'Accepts' header
     * or based on the requested file
     */
    private Set<String> getAcceptableMimeTypes(@Nullable Request request, String downloadUrl) {
        return getAcceptableHeaders(request, downloadUrl)
                .flatMap(acceptsHeader -> getUniqueMimeTypeList(acceptsHeader).stream())
                .collect(Collectors.toSet());
    }

    private Stream<String> getAcceptableHeaders(@Nullable Request request, String downloadUrl) {
        if (request != null) {
            Enumeration<String> acceptHeadersRaw = request.getHeaderValues(HttpHeaders.ACCEPT);
            if (acceptHeadersRaw.hasMoreElements()) {
                if (log.isTraceEnabled()) {
                    log.trace("Found Accepts header in request: {}", acceptHeadersRaw);
                }
                return StreamSupportUtils.enumerationToStream(acceptHeadersRaw);
            }
        }
        return Stream.of(NamingUtils.getMimeType(downloadUrl).getType());
    }

    /**
     * Mime type mismatch should be verified only if the repo requires it and the response's header is present.
     */
    private boolean shouldVerifyMimeType(CloseableHttpResponse response) {
        return getRepoConfig().getRepoTypeConfig().isBlockMismatchingMimeTypes() &&
                response.containsHeader(HttpHeaders.CONTENT_TYPE);
    }

    /**
     * Should block if there's a mismatch between the response's Content-Type header and the acceptable mime types list
     * (which was derived from the request's Accepts header or the requested file's extension) and the response's
     * returned type is in this repo's block list.
     */
    private boolean shouldBlockMimeType(String responseContentType, Set<String> acceptableMimeTypes) {
        return !acceptableMimeTypes.contains(responseContentType)
                && getRepoMismatchingMimeTypeBlockList().contains(responseContentType);
    }

    private Set<String> getRepoMismatchingMimeTypeBlockList() {
        String mimeTypeBlockList = getRepoConfig().getRepoTypeConfig().getMismatchingMimeTypesOverrideList();
        String repoKey = getRepoConfig().getKey();
        if (isNotBlank(mimeTypeBlockList)) {
            log.trace("Repo {} defines an overriding mismatching mime type block list: {}", repoKey, mimeTypeBlockList);
        } else {
            mimeTypeBlockList = ConstantValues.blockedMismatchingMimeTypes.getString();
            log.trace("Repo {} does not define an overriding mismatching mime type block list, using default: {}",
                    repoKey, mimeTypeBlockList);
        }
        return getUniqueMimeTypeList(mimeTypeBlockList);
    }

    /**
     * Accepts header can have multiple entries https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
     */
    private Set<String> getUniqueMimeTypeList(String acceptsHeader) {
        return Stream.of(acceptsHeader.split(","))                       //if multivalued - values are delimited by ','
                .map(mimeType -> mimeType.replaceFirst(";(?:.*)", ""))   //each value can have options with ';'
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private boolean isNeedToNormalize(RemoteRepoConfig config) {
        String url = config.getRepoTypeConfig().getUrl();
        if (StringUtils.isBlank(url)) {
            log.warn("No remote url found for: {}", config.getKey());
            return true;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            log.warn("Invalid url {} : {}", url, e.getMessage());
            return true;
        }
        if (uri.getHost() == null || config.getRepoTypeConfig().isDisableUrlNormalization()) {
            return false;
        }
        String hostsToFilter = ConstantValues.listOfReposHostsNeedToAvoidUrlNormalisation.getString();
        String[] hostsArray = hostsToFilter.split(",[ ]*");
        return !Set.of(hostsArray).contains(uri.getHost());
    }

    /**
     * Content-Type header can be 'X/Y; [some encoding info]'
     *
     * @param header Content-Type header value
     * @return Just the mime type
     */
    private String getMimeTypeFromHeader(String header) {
        if (header.contains(";")) {
            return header.split(";")[0];
        } else {
            return header;
        }
    }

    @Override
    public InputStream getFileContent(String path) {
        return getLocalCacheRepo().getFileContent(path);
    }

    @VisibleForTesting
    boolean shouldLogRangeNotSupported(CloseableHttpResponse response) {
        if (response == null || response.getStatusLine() == null) {
            return false;
        }
        int status = response.getStatusLine().getStatusCode();
        return status == SC_OK || status == HttpStatus.SC_NO_CONTENT;
    }

    /**
     * Sets the response repo path on a cached resource
     *
     * @param repoPath       Path item to resource
     * @param cachedResource Cached resource
     * @return Repo resource object
     */
    @Override
    protected RepoResource returnCachedResource(RepoPath repoPath, RepoResource cachedResource,
            RequestContext context) {
        cachedResource.setResponseRepoPath(InternalRepoPathFactory.create(localCacheRepo.getKey(), repoPath.getPath()));
        if (cachedResource instanceof FileResource fileResource) {
            log.debug("Checking {} by Curation", repoPath);
            return checkByCuration(fileResource, context);
        } else {
            log.debug("{} is not instanceof FileResource", repoPath);
        }
        return cachedResource;
    }

    @Override
    public RepoResource checkByCuration(RepoResource repoResource, RequestContext context) {
        if (isBlockDownloadEnabled() && !context.isSkipCurationOnResource()) {
            if (repoResource instanceof FileResource fileResource) {
                String originRemotePath = checkOriginRemotePath(fileResource, context);
                String mimeType = fileResource.getMimeType();
                log.debug("CURATION CALL in checkByCuration downloadUrl: {} contentType: {}",
                        originRemotePath, mimeType);
                CurationPackageStatus status = getCurationAddon().
                        getCachedPackageStatus(getRepoConfig(), fileResource.getRepoPath(), originRemotePath, mimeType);
                if (!status.permitted()) {
                    return new BlockedRepoResource(fileResource.getRepoPath(), status.message(),
                            BlockedRepoResource.BlockedType.CURATION);
                }
            } else {
                log.debug("{} is not file resource  - skip curation check", repoResource.getRepoPath());
            }
        } else {
            log.debug("Block download enabled {}, SkipCurationOnCachedResource {} - skip curation check",
                    isBlockDownloadEnabled(), context.isSkipCurationOnResource());
        }
        return repoResource;
    }

    @VisibleForTesting
    String checkOriginRemotePath(FileResource resource, RequestContext context) {
        String originRemotePath = resource.getOriginRemotePath();
        if (isBlank(originRemotePath)) {
            OriginRemotePath remotePath = createOriginRemotePath(resource.getRepoPath(), context);
            if (!remotePath.fallback()) {
                updateOriginRemotePath(resource, remotePath.path());
            }
            return remotePath.path();
        } else {
            log.debug("{} has {} property {}", resource.getRepoPath().toPath(),
                    ORIGIN_REMOTE_PATH_PROP_KEY, originRemotePath);
            return originRemotePath;
        }
    }

    private OriginRemotePath createOriginRemotePath(RepoPath repoPath, RequestContext context) {
        String path = repoPath.getPath();
        String originRemotePath = null;
        boolean fallback = false;

        if (getSmartRepoService().isSmartRepo(getKey())) {
            log.debug("{} retrieving origin remote path", repoPath.toPath());
            originRemotePath = retrieveOriginRemotePathHeader(path, context);

            if (isBlank(originRemotePath)) {
                log.warn("{} package type {} failed to retrieve origin remote path, falling back to assemble",
                        repoPath.toPath(), getRepoConfig().getPackageType().getType());
                fallback = true;
            } else if (LOCAL_REPO_RESPONSE_INDICATOR.equalsIgnoreCase(originRemotePath)) {
                log.debug("{} package type {} got response from local repo for origin remote path",
                        repoPath.toPath(), getRepoConfig().getPackageType().getType());
            } else {
                log.debug("{} retrieved origin remote path: {}", repoPath.toPath(), originRemotePath);
                return new OriginRemotePath(originRemotePath, false);
            }
        }

        log.debug("{} assembling origin remote path", repoPath.toPath());
        String assembledPath = assembleOriginRemotePath(path, context);
        if (!isBlank(assembledPath)) {
            log.debug("{} assembled origin remote path: {}", repoPath.toPath(), assembledPath);
            String normalizedPath = normalizeOriginRemotePath(originRemotePath, assembledPath);
            return new OriginRemotePath(normalizedPath, fallback);
        }

        String fallbackPath = addTrailingSlashIfNeeded(path, getUrl());
        log.warn("{} package type {} failed to assemble origin remote path, falling back to repo path {}",
                repoPath.toPath(), getRepoConfig().getPackageType().getType(), fallbackPath);
        String normalizedPath = normalizeOriginRemotePath(originRemotePath, fallbackPath);
        return new OriginRemotePath(normalizedPath, true);
    }

    private String assembleOriginRemotePath(String path, RequestContext context) {
        String originRemotePath = null;
        try {
            originRemotePath = assembleRetrieveInfoUrl(path, context);
        } catch (Exception e) {
            log.warn("Failed to assemble origin remote path: {}", path, e);
        }
        return originRemotePath == null ? "" : originRemotePath;
    }

    private void updateOriginRemotePath(FileResource resource, String originRemotePath) {
        if (isNotBlank(originRemotePath)) {
            RepoPath repoPath = resource.getRepoPath();
            resource.setOriginRemotePath(originRemotePath);
            PropertiesService propertiesService = getPropertiesService();
            propertiesService.addProperty(repoPath, null, new Property(ORIGIN_REMOTE_PATH_PROP_KEY),
                    originRemotePath);
            log.debug("Saved {} {} property value : {}", repoPath.toPath(),
                    ORIGIN_REMOTE_PATH_PROP_KEY, originRemotePath);
        }
    }

    private static SmartRepoService getSmartRepoService() {
        InternalArtifactoryContext internalArtifactoryContext = InternalContextHelper.get();
        return internalArtifactoryContext.beanForType(SmartRepoService.class);
    }

    @VisibleForTesting
    PropertiesService getPropertiesService() {
        return InternalContextHelper.get().beanForType(PropertiesService.class);
    }

    private record OriginRemotePath(String path, boolean fallback) {
    }

    /**
     * Normalizes the origin remote path by handling the "local" response indicator.
     * When the upstream response indicates the artifact came from a local repo (returns "local"),
     * this method prefixes the default path with "local:" to mark it as locally sourced.
     *
     * @param originPath  The origin path retrieved from upstream (may be null, blank, or "local")
     * @param defaultPath The default path to use if originPath is blank or needs to be prefixed
     * @return The normalized origin remote path
     */
    private String normalizeOriginRemotePath(String originPath, String defaultPath) {
        if (isBlank(originPath)) {
            return defaultPath;
        }
        if (LOCAL_REPO_RESPONSE_INDICATOR.equalsIgnoreCase(originPath)) {
            log.debug("Got response from local repo, prefixing path: {}", defaultPath);
            return LOCAL_PREFIX + defaultPath;
        }
        return originPath;
    }

    public class TrafficAwareRemoteResourceStreamHandle extends RemoteResourceStreamHandle
            implements RemoteResourceTrafficContextHolder {
        private final BandwidthMonitorInputStream bmis;
        private final String remoteIp;
        private CloseableHttpResponse response;
        private String fullUrl;
        private Request requestForPlugins;
        private PluginsAddon pluginAddon;
        private RepoPath repoPath;

        TrafficAwareRemoteResourceStreamHandle(CloseableHttpResponse response, String fullUrl,
                Request requestForPlugins, PluginsAddon pluginAddon, RepoPath repoPath, InputStream is) {
            this.response = response;
            this.fullUrl = fullUrl;
            this.requestForPlugins = requestForPlugins;
            this.pluginAddon = pluginAddon;
            this.repoPath = repoPath;
            this.bmis = new BandwidthMonitorInputStream(is);
            TrafficService trafficService = ContextHelper.get().beanForType(TrafficService.class);
            if (trafficService.isActive() && response != null) {
                this.remoteIp = HttpUtils.resolveResponseRemoteAddress(response);
            } else {
                this.remoteIp = StringUtils.EMPTY;
            }
        }

        TrafficAwareRemoteResourceStreamHandle(String fullUrl, Request requestForPlugins,
                PluginsAddon pluginAddon, RepoPath repoPath, InputStream is, String remoteIp) {
            this.response = null;
            this.fullUrl = fullUrl;
            this.requestForPlugins = requestForPlugins;
            this.pluginAddon = pluginAddon;
            this.repoPath = repoPath;
            this.bmis = new BandwidthMonitorInputStream(is);
            this.remoteIp = remoteIp;
        }

        public String getContentType() {
            if (response == null) {
                return "";
            }
            return Arrays.stream(response.getHeaders(HttpHeaders.CONTENT_TYPE)).map(Header::getValue).findFirst()
                    .orElse("");
        }

        @Override
        public long getTotalBytesRead() {
            return bmis.getTotalBytesRead();
        }

        public RepoPath getRepoPath() {
            return repoPath;
        }

        @Override
        public InputStream getInputStream() {
            return bmis;
        }

        @Override
        public long getSize() {
            return -1;
        }

        @Override
        public CloseableHttpResponse getResponse() {
            return response;
        }

        @Override
        public void close() {
            IOUtils.closeQuietly(bmis);
            IOUtils.closeQuietly(response);
            StatusLine statusLine = response != null ? response.getStatusLine() : null;

            Throwable throwable = getThrowable();
            if (throwable != null) {
                String exceptionMessage = HttpClientUtils.getErrorMessage(throwable);
                log.error("{}: Failed to download '{}'. Received status code {} and caught exception: {}",
                        HttpRepo.this, fullUrl, statusLine != null ? statusLine.getStatusCode() : "unknown",
                        exceptionMessage);
                String downLoadSummary =
                        StorageUnit.toReadableString(bmis.getTotalBytesRead()) + " at " +
                                StorageUnit.format(StorageUnit.KB.fromBytes(bmis.getBytesPerSec())) + " KB/sec";
                log.debug("Failed to download '{}'. Download summary: {}", fullUrl, downLoadSummary, throwable);
                RepoRequests.logToContext("Failed to download: %s", exceptionMessage);
            } else {
                if (response != null) {
                    int status = statusLine != null ? statusLine.getStatusCode() : 0;
                    logDownloaded(fullUrl, status, bmis);
                }
                RepoRequests.logToContext("Downloaded content");
            }
            RepoRequests.logToContext("Executing any AfterRemoteDownload user plugins that may exist");
            pluginAddon.execPluginActions(AfterRemoteDownloadAction.class, null, requestForPlugins, repoPath);
            RepoRequests.logToContext("Executed all AfterRemoteDownload user plugins");
        }

        @Override
        public String getRemoteIp() {
            return remoteIp;
        }
    }

    /**
     * ResourceStreamHandle for parallel downloads with pre-calculated checksums.
     * Uses composition with TrafficAwareRemoteResourceStreamHandle for plugin/event/traffic logic.
     */
}
