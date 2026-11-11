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

package org.artifactory.engine;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.collect.Iterables;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.analytics.AnalyticsAddon;
import org.artifactory.addon.leadartifactdetector.LeadArtifactDetectorAddon;
import org.artifactory.addon.onboarding.OnBoardingAddon;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.addon.plugin.download.AfterDownloadAction;
import org.artifactory.addon.plugin.download.AfterDownloadErrorAction;
import org.artifactory.addon.plugin.download.BeforeDownloadAction;
import org.artifactory.addon.plugin.download.BeforeDownloadRequestAction;
import org.artifactory.addon.plugin.download.DownloadCtx;
import org.artifactory.addon.worker.WorkersAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.jackson.JacksonFactory;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.repo.exception.XrayRepoRejectException;
import org.artifactory.api.repo.exception.maven.BadPomException;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.request.DownloadErrorNotificationResult;
import org.artifactory.api.request.ConditionalInternalArtifactoryRequest;
import org.artifactory.api.request.ControlledCacheRedirectService;
import org.artifactory.api.request.GracefulShutdownAware;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.request.LifecycleIndependentArtifactoryRequest;
import org.artifactory.api.request.TranslatedArtifactoryRequest;
import org.artifactory.api.rest.artifact.ItemProperties;
import org.artifactory.api.security.SecurityService;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.exception.WorkerStopException;
import org.artifactory.fs.RepoResource;
import org.artifactory.md.MetadataDefinitionService;
import org.artifactory.md.Properties;
import org.artifactory.mime.MavenNaming;
import org.artifactory.mime.NamingUtils;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.plugin.AltResponseHelper;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RemoteRepoBase;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.cache.expirable.InternalMetadataChecker;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.system.SystemFolders;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.request.AlteredDownloadRequestContext;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.DownloadPerfCounters;
import org.artifactory.request.DownloadRequestContext;
import org.artifactory.request.InternalArtifactoryResponse;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.RedirectInfo;
import org.artifactory.request.RemoteRequestException;
import org.artifactory.request.RepoRequests;
import org.artifactory.request.Request;
import org.artifactory.request.RequestResponseHelper;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.resource.BlockedRepoResource;
import org.artifactory.resource.ChecksumResource;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.RemoteRepoResource;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.resource.UnfoundRepoResourceReason;
import org.artifactory.rest.resource.ArtifactoryResourceInfo;
import org.artifactory.security.signature.SignedUrlAuthenticationToken;
import org.artifactory.service.SmartRepoService;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.spring.EntitlementsEventListener;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.fs.service.StatsService;
import org.artifactory.traffic.TrafficEntry;
import org.artifactory.traffic.TrafficService;
import org.artifactory.traffic.TrafficUtils;
import org.artifactory.traffic.context.TrafficContextThreadLocal;
import org.artifactory.traffic.entry.RedirectEntry;
import org.artifactory.unifiedevent.UnifiedEventServiceImpl;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.ResponseContextData;
import org.artifactory.util.AolUtils;
import org.artifactory.util.BasicHttpUtils;
import org.artifactory.util.HttpUtils;
import org.artifactory.util.PathValidator;
import org.artifactory.util.RepoPathUtils;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.config.diff.DataDiff;
import org.jfrog.storage.binstore.exceptions.BinaryRejectedException;
import org.jfrog.storage.binstore.exceptions.BinaryStorageException;
import org.jfrog.storage.binstore.exceptions.SignedUrlException;
import org.jfrog.storage.binstore.ifc.ManualRedirectType;
import org.jfrog.storage.binstore.ifc.RedirectType;
import org.jfrog.storage.binstore.ifc.SignedUrl;
import org.jfrog.storage.binstore.ifc.UnsupportedRedirectTypeException;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.InvalidPathException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Objects.nonNull;
import static org.artifactory.addon.release.bundle.ReleaseBundleAddon.ENTERPRISE_PLUS_MSG;
import static org.artifactory.api.request.ArtifactoryHeaders.IF_NONE_MATCH_MD5;
import static org.artifactory.api.request.ArtifactoryHeaders.X_JFROG_CONTROLLED_CACHE_RESPONSE;
import static org.artifactory.api.request.GracefulShutdownAware.noActiveTracking;
import static org.artifactory.api.request.GracefulShutdownAware.track;
import static org.artifactory.common.ConstantValues.includeMd5IntoETagCheck;
import static org.artifactory.engine.ControlledCacheConstants.JFROG_CONTROLLED_CACHE_RESPONSE_REDIRECT_VALUE;
import static org.artifactory.engine.ControlledCacheConstants.JFROG_CONTROLLED_CACHE_RESPONSE_STREAM_VALUE;
import static org.artifactory.engine.ControlledCacheRedirectUtil.shouldTriggerDownloadInterceptors;
import static org.artifactory.repo.RepoDetailsType.LOCAL_REPO;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.REMOTE_REPO;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL_REPO;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.getSystemRepo;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.isSystemRepo;
import static org.artifactory.request.ArtifactoryRequest.PARAM_FORCE_GET_STREAM;
import static org.artifactory.request.RequestResponseHelper.tryToGetRangeHeaderInsensitive;
import static org.artifactory.request.range.ResponseWithRangeSupportHelper.calculateContentLength;
import static org.artifactory.security.AuthenticationHelper.getAuthentication;
import static org.artifactory.security.AuthenticationHelper.getRemoteAddress;
import static org.artifactory.util.BasicHttpUtils.isSuccessfulResponseCode;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.MANUAL_REDIRECT_TYPE;

/**
 * @author Yoav Landman
 */
@Slf4j
@Service
@Reloadable(beanClass = InternalDownloadService.class,
        initAfter = {InternalRepositoryService.class},
        listenOn = CentralConfigKey.none)
public class DownloadServiceImpl implements InternalDownloadService, EntitlementsEventListener, GracefulShutdownAware {

    private static final String CACHE_REPO = "cache";

    @Autowired
    SecurityService securityService;
    @Autowired
    @VisibleForTesting
    CentralConfigService centralConfig;
    @Autowired
    TrafficService trafficService;
    @Autowired
    AddonsManager addonsManager;
    @Autowired
    ArtifactoryBinaryService binaryService;
    @Autowired
    DownloadRedirectHelper downloadRedirectHelper;
    @Autowired
    @VisibleForTesting
    InternalRepositoryService repositoryService;
    @Autowired
    @VisibleForTesting
    BinaryElementFactory binaryElementFactory;
    @Autowired
    private BasicAuthenticationEntryPoint authenticationEntryPoint;
    @Autowired
    private PropertiesService propertiesService;
    @Autowired
    @VisibleForTesting
    SmartRepoService smartRepoService;
    @Autowired
    @VisibleForTesting
    @Qualifier("statsServiceImpl")
    StatsService statsService;
    @Autowired
    @VisibleForTesting
    ProjectsService projectsService;
    @VisibleForTesting
    @Autowired
    ControlledCacheRedirectService controlledCacheRedirectService;
    RequestResponseHelper requestResponseHelper;
    @VisibleForTesting
    AltResponseHelper altResponseHelper;
    @Autowired
    UnifiedEventServiceImpl unifiedEventService;

    @Override
    public void onLicenseLoaded() {
        warnIfRepoHasDownloadRedirectEnabledAndLicenseIsNotValid();
    }

    private final Cache<String, Object> activeDownloadTraces = GracefulShutdownAware.createTrackingCache();

    @Override
    public void init() {
        requestResponseHelper = new RequestResponseHelper(trafficService, securityService);
        altResponseHelper = new AltResponseHelper(addonsManager);
    }

    /**
     * Print an error in case license is NOT Enterprise+ or Edge and one of the repos is configured for download
     * redirect.
     * It is allowed only on E+ and Edge (and will fail upon download request)
     */
    private void warnIfRepoHasDownloadRedirectEnabledAndLicenseIsNotValid() {
        if (addonsManager.isEntitled(EntitlementType.BINARY_PROVIDER_DOWNLOAD_REDIRECT)) {
            return;
        }
        // License is not Enterprise+, check if one of the repos is configured for redirect
        if (hasDownloadRedirectEnabled(repositoryService.getLocalRepoConfigsIncludingInfoRepos())
                || hasDownloadRedirectEnabled(repositoryService.getFederatedRepoConfigs())
                || hasDownloadRedirectEnabled(repositoryService.getRemoteRepoConfigs())) {
            logWarnRedirectConfigured();
        }
    }

    private boolean hasDownloadRedirectEnabled(List<? extends RealRepoConfig> realRepos) {
        return realRepos.stream()
                .anyMatch(realRepoConfig -> realRepoConfig.getRepoTypeConfig().isDownloadRedirect());
    }

    @VisibleForTesting
    void logWarnRedirectConfigured() {
        log.warn("Download redirect is configured for one of the repositories but {}", ENTERPRISE_PLUS_MSG);
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
    }

    @Override
    public void destroy() {
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
    }

    /**
     * Expects requests that starts with the repo url prefix and contains an optional target local repository. E.g.:
     * <pre>http://localhost:8080/artifactory/repo/ant/ant-antlr/1.6.5/ant-antlr-1.6.5.jar</pre>
     * <pre>http://localhost:8080/artifactory/repo/org/codehaus/xdoclet/xdoclet/2.0.5-SNAPSHOT/xdoclet-2.0.5-SNAPSHOT
     * .jar</pre>
     * or with a target local repo:
     * <pre>http://localhost:8080/artifactory/local-repo/ant/ant-antlr/1.6.5/ant-antlr-1.6.5.jar</pre>
     */
    @Override
    public void process(ArtifactoryRequest request, ArtifactoryResponse response) throws IOException {
        try {
            TrafficContextThreadLocal.bind(request.getRepoKey());
            track(this::processDownload, request, response, activeDownloadTraces, log);
        } finally {
            TrafficContextThreadLocal.unbind();
        }
    }

    @Override
    public DownloadErrorNotificationResult notifyDownloadError(ArtifactoryRequest request, int status, String reason) {
        RepoRequests.logToContext("Notifying download error with status %s and message '%s'", status, reason);
        ResponseCtx responseCtx = new ResponseCtx();
        responseCtx.setMessage(reason);
        responseCtx.setStatus(status);
        try {
            applyAfterDownloadErrorActions(request, responseCtx);
        } catch (WorkerStopException e) {
            log.debug("After Download Error worker stopped for {}: {}", request.getRepoPath(), e.getMessage());
            return new DownloadErrorNotificationResult(HttpStatus.SC_CONFLICT, e.getMessage());
        }
        return toDownloadErrorNotificationResult(responseCtx, status, reason);
    }

    private static DownloadErrorNotificationResult toDownloadErrorNotificationResult(ResponseCtx responseCtx,
            int defaultStatus, String defaultReason) {
        int status = responseCtx.getStatus() != ResponseCtx.UNSET_STATUS ? responseCtx.getStatus() : defaultStatus;
        String message = responseCtx.getMessage() != null ? responseCtx.getMessage() : defaultReason;
        return new DownloadErrorNotificationResult(status, message);
    }

    private void applyAfterDownloadErrorActions(ArtifactoryRequest request, ResponseCtx responseCtx) {
        RepoRequests.logToContext("Executing any After Download Error user plugins and workers that may exist");
        PluginsAddon pluginAddon = addonsManager.addonByType(PluginsAddon.class);
        pluginAddon.execPluginActions(AfterDownloadErrorAction.class, responseCtx, request);
        sendAfterDownloadErrorUnifiedEvent(request, responseCtx);
    }

    @Override
    public boolean canBeShutdown() {
        return noActiveTracking(activeDownloadTraces);
    }

    @VisibleForTesting
    void processDownload(ArtifactoryRequest request, ArtifactoryResponse response) throws IOException {
        CurrentActivity.GROUP.putIfEmpty("download");
        if (shouldBlockDownloadForRepo(request.getRepoKey())) {
            RepoRequests.logToContext("Exiting download process - repository has a specific api for" +
                    " downloading '%s'", request.getRepoKey());
            response.sendError(HttpStatus.SC_NOT_FOUND, "Failed to find the repository '" + request.getRepoKey() +
                    "' specified in the request.", log);
            return;
        }
        if (shouldBlockSystemFolderDownload(request)) {
            RepoRequests.logToContext("Exiting download process - download not allowed for system folder path '%s'",
                    request.getRepoPath());
            response.sendError(HttpStatus.SC_NOT_FOUND, "Failed to find the requested resource '" +
                    request.getRepoPath() + "'.", log);
            return;
        }

        String lastModified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(request.getLastModified()));
        RepoRequests.logToContext("Request source = %s, Last modified = %s, If modified since = %s, Thread name = %s",
                request.getClientAddress(), lastModified,
                request.getIfModifiedSince(), Thread.currentThread().getName());
        if (response.isPropertiesQuery()) {
            RepoRequests.logToContext("Requesting properties only with format " + response.getPropertiesMediaType());
        }

        //Check that this is not a recursive call
        if (request.isRecursive()) {
            RepoRequests.logToContext("Exiting download process - recursive call detected");
            String msg = "Recursive call detected for '" + request + "'. Returning nothing.";
            response.sendError(HttpStatus.SC_NOT_FOUND, msg, log);
            return;
        }

        setRemoteAddress(request);
        DownloadCtx downloadCtx = new DownloadCtx();

        if (shouldTriggerDownloadInterceptors(request)) {
            RepoRequests.logToContext("Executing any BeforeDownloadRequest user plugins that may exist");
            PluginsAddon pluginAddon = addonsManager.addonByType(PluginsAddon.class);
            pluginAddon.execPluginActions(BeforeDownloadRequestAction.class, downloadCtx, request,
                    request.getOriginalRepoPath());
            sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD_REQUEST, request, null,
                    downloadCtx);
            if (downloadCtx.getModifiedRepoPath() != null) {
                RepoRequests.logToContext("BeforeDownloadRequest user plugins provided a modified repo path: " +
                        downloadCtx.getModifiedRepoPath().getId());
                request = new TranslatedArtifactoryRequest(downloadCtx.getModifiedRepoPath(), request);
            }
        }

        if (NamingUtils.isMetadata(request.getPath())) {
            // the repo filter redirects for the ":properties" paths. This is here to protect direct calls to this
            // method
            response.sendError(HttpStatus.SC_CONFLICT, "Old metadata notation is not supported anymore: " +
                    request.getRepoPath(), log);
        }

        // This intercept is triggered anyway even if we should not trigger the download interceptors since it
        // validates the license
        addonsManager.interceptResponse(response, request, false);
        if (responseWasIntercepted(response)) {
            RepoRequests.logToContext("Exiting download process - intercepted by addon manager");
            return;
        }
        processDownloadInternal(request, response, downloadCtx);
    }

    private void processDownloadInternal(ArtifactoryRequest request, ArtifactoryResponse response,
            DownloadCtx downloadCtx) throws IOException {
        try {
            Repo repository = repositoryService.repositoryByKey(request.getRepoKey());

            // Should prevent direct download of the internal metadata
            if (repository != null && InternalMetadataChecker.isInternalMetadata(request.getPath())) {
                RepoRequests.logToContext("Exiting download process - internal metadata detected");
                String msg = "Internal metadata request detected for '" + request + "'. Returning nothing.";
                response.sendError(HttpStatus.SC_NOT_FOUND, msg, log);
                return;
            }
            RequestThreadLocal.getDownloadPerfCounters(true);
            DownloadRequestContext requestContext = getDownloadRequestContext(request, downloadCtx);
            RepoResource resource =
                    getRepoResource(request, repository, requestContext);
            setXrayOriginIfNeeded(response, resource);
            setCurationOriginIfNeeded(response, resource);

            boolean triggerDownloadInterceptors = shouldTriggerDownloadInterceptors(request);
            if (!request.isHeadOnly() && resource.isFound() && triggerDownloadInterceptors) {
                smartRepoService.onFileDownload(resource);
            }
            if (response.isSuccessful() && resource.isFound() && repository.getRepoConfig() != null &&
                    triggerDownloadInterceptors) {
                notifyOnSuccessfulPackageDownloadIfNeeded(resource, repository.getRepoConfig().getPackageType());
            }

            respond(requestContext, response, resource,
                    triggerDownloadInterceptors);
            if (resource.isFound() && repository.getRepoConfig() != null && triggerDownloadInterceptors) {
                trackSuccessfulPackageConsumption(request, response, repository.getRepoConfig().getPackageType());
            }
        } catch (IOException e) {
            RepoRequests.logToContext("Request failed: %s", e.getMessage());
            //We can get here when sending a response while the client hangs up the connection.
            //In this case the response will be committed so there is no point in sending an error.
            if (!response.isCommitted()) {
                response.sendInternalError(e, log);
            }
            throw e;
        } finally {
            if (response.isSuccessful()) {
                RepoRequests.logToContext("Request succeeded");
            }
            try {
                if (shouldTriggerDownloadInterceptors(request)) {
                    if (shouldTriggerAfterDownloadWorker(request, response.isSuccessfulOrRedirect())) {
                        log.debug("Triggering AFTER_DOWNLOAD workers");
                        sendUnifiedAfterEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD,
                                new LifecycleIndependentArtifactoryRequest(request));
                    }
                    log.debug("Triggering AFTER_DOWNLOAD plugins");
                    PluginsAddon pluginAddon = addonsManager.addonByType(PluginsAddon.class);
                    pluginAddon.execPluginActions(AfterDownloadAction.class, new Object(), request, response);
                }
            } catch (WorkerStopException e) {
                RepoRequests.logToContext("Failed to execute After Download worker: %s", e.getMessage());
                throw e;
            } catch (Exception e) {
                RepoRequests.logToContext("Failed to execute After Download plugin: %s", e.getMessage());
                throw e;
            }
        }
    }

    private void setCurationOriginIfNeeded(ArtifactoryResponse response, RepoResource resource) {
        if (resource instanceof BlockedRepoResource blockedResource &&
                BlockedRepoResource.BlockedType.CURATION == blockedResource.getBlockedType()) {
            response.setCurationOriginHeader("true");
        }
    }

    private void setXrayOriginIfNeeded(ArtifactoryResponse response, RepoResource resource) {
        if (resource instanceof BlockedRepoResource blockedResource &&
                BlockedRepoResource.BlockedType.XRAY == blockedResource.getBlockedType()) {
            response.setXrayOriginHeader("true");
        }
    }

    /**
     * Tracking successful package consumption request types - Head, Get and Redirect.
     * This consumption tracker method calls when we have a resource found and a nonnull repository config
     * during download.
     * We want to distinguish between the Head request executed by the user and turns to Get request by Artifactory,
     * to some other Get request.
     * Therefore, Heads first (Successful codes - 200 to 299 and Continue Code - 100 ) and only then Get, and redirect.
     */
    private void trackSuccessfulPackageConsumption(ArtifactoryRequest request, ArtifactoryResponse response,
            RepoType repoType) {
        RepoPath repoPath = RepoPathFactory.create(request.getRepoKey(), request.getPath());

        if (!request.shouldSkipPackageUsageTrack() &&
                addonsManager.addonByType(LeadArtifactDetectorAddon.class)
                        .isPackageConsumptionLead(repoType, repoPath)) {
            AnalyticsAddon analyticsAddon = addonsManager.addonByType(AnalyticsAddon.class);
            if (isSuccessfulHeadRequest(request, response)) {
                analyticsAddon.trackSuccessfulPackageHeadRequest(repoType);
            } else if (response.isSuccessful()) {
                analyticsAddon.trackSuccessfulPackageDownload(repoType);
            } else if (BasicHttpUtils.isRedirectionResponseCode(response.getStatus())) {
                analyticsAddon.trackPackageDownloadRedirect(repoType);
            }
        }
    }

    private boolean isSuccessfulHeadRequest(ArtifactoryRequest request, ArtifactoryResponse response) {
        return request.isHeadOnly() && response.isSuccessful() || HttpStatus.SC_CONTINUE == response.getStatus();
    }

    private boolean shouldTriggerAfterDownloadWorker(ArtifactoryRequest request,
            boolean isSuccessfulResponseOrRedirect) {
        return isSuccessfulResponseOrRedirect && !request.isHeadOnly();
    }

    @VisibleForTesting
    RepoResource getRepoResource(ArtifactoryRequest request, Repo<?, ?> repository,
            DownloadRequestContext requestContext) {
        RepoResource resource;
        if (repository == null) {
            RepoRequests.logToContext("Exiting download process - failed to find the repository '%s'",
                    request.getRepoKey());
            resource = new UnfoundRepoResource(request.getRepoPath(), "Failed to find the repository '" +
                    request.getRepoKey() + "' specified in the request.");
        } else {
            RepoRequests.logToContext("Retrieving info from %s repository '%s' type %s",
                    getRepoType(repository),
                    repository.getKey(), repository.getDescriptor().getType().name());
            resource = repository.getInfo(requestContext);
            if (resource instanceof FileResource fileResource) {
                String repoKey = fileResource.getRepoKey();
                boolean isCache = RepoPathUtils.isCache(repoKey);
                if (isCache) {
                    log.debug("Set skip curation on cached resource for {} to false", request.getRepoPath());
                    requestContext.setSkipCurationOnResource(false);
                    String remoteRepoKey = RepoPathUtils.removeDashCache(repoKey);
                    log.debug("Curation check will be on {} repo", remoteRepoKey);
                    RemoteRepo remoteRepo = repositoryService.remoteRepositoryByKey(remoteRepoKey);
                    resource = remoteRepo.checkByCuration(fileResource, requestContext);
                    log.debug("Set skip curation on cached resource for {} to true", request.getRepoPath());
                    requestContext.setSkipCurationOnResource(true);
                }
            }
        }
        return resource;
    }

    @NonNull
    private String getRepoType(Repo<?, ?> repository) {
        if (repository.isReal()) {
            if (repository.isCache()) {
                return CACHE_REPO;
            } else if (repository.isLocal()) {
                return LOCAL_REPO;
            } else {
                return REMOTE_REPO;
            }
        } else {
            return VIRTUAL_REPO;
        }
    }

    @NonNull
    @VisibleForTesting
    DownloadRequestContext getDownloadRequestContext(ArtifactoryRequest request,
            DownloadCtx downloadCtx) {
        DownloadRequestContext requestContext;
        if (request instanceof ConditionalInternalArtifactoryRequest conditionalInternalArtifactoryRequest) {
            requestContext = new AlteredDownloadRequestContext(request, downloadCtx.isExpired(),
                    conditionalInternalArtifactoryRequest.getAlteredFunction(),
                    conditionalInternalArtifactoryRequest.getShouldUnExpireFunc());
        } else if (isRelativeRoot(request)) {
            requestContext = new AlteredDownloadRequestContext(request, downloadCtx.isExpired(),
                    r -> request.getRepoPath().getPath().substring(2), res -> false);
        } else {
            requestContext = new DownloadRequestContext(request, downloadCtx.isExpired());
        }
        return requestContext;
    }

    private boolean isRelativeRoot(Request request) {
        return request.getRepoPath().getPath().startsWith("./");
    }

    @VisibleForTesting
    void sendUnifiedEvent(SupportedUnifiedEvents supportedUnifiedEvents, ArtifactoryRequest request,
            RepoPath responseRepoPath, DownloadCtx downloadCtx) {
        try {
            log.debug("Sending {} event via unifiedEventService for {}", supportedUnifiedEvents, request);
            DispatcherResponse response =
                    unifiedEventService.sendArtifactEvent(supportedUnifiedEvents, request, responseRepoPath);
            log.debug("Received {} response from unifiedEventService: {}", supportedUnifiedEvents, response);
            if (response != null && response.data() != null) {
                updateContext(response.data(), downloadCtx);
            }
        } catch (UnifiedEventException e) {
            log.debug("{} event via unifiedEventService for {} was rejected: {}", supportedUnifiedEvents, request,
                    e.getMessage());
            if (e.statusCode == HttpStatus.SC_CONFLICT) {
                throw new WorkerStopException(e.getMessage());
            }
            throw e;
        }
    }

    private void updateContext(ResponseContextData data, DownloadCtx downloadCtx) {
        // fill this method
        if (data.isExpired() != null) {
            downloadCtx.setExpired(data.isExpired());
        }
        if (data.modifiedRepoPath() != null) {
            downloadCtx.setModifiedRepoPath(data.modifiedRepoPath());
        }
        downloadCtx.setHeaders(data.headers());
    }

    @VisibleForTesting
    void sendUnifiedAfterEvent(SupportedUnifiedEvents supportedUnifiedEvents, ArtifactoryRequest request) {
        try {
            log.debug("Sending AfterDownload event via unifiedEventService for {}", request);
            DispatcherResponse response =
                    unifiedEventService.sendArtifactEvent(supportedUnifiedEvents, request, null);
            log.debug("Received AfterDownload response from unifiedEventService: {}", response);
        } catch (UnifiedEventException e) {
            log.debug("AfterDownload event via unifiedEventService for {} was rejected: {}", request, e.getMessage());
            if (e.statusCode == HttpStatus.SC_CONFLICT) {
                throw new WorkerStopException(e.getMessage());
            }
            throw e;
        }
    }

    @VisibleForTesting
    void runWorker(SupportedUnifiedEvents supportedUnifiedEvents, Object... args) {
        addonsManager.addonByType(WorkersAddon.class).run(supportedUnifiedEvents, args);
    }

    private boolean shouldBlockDownloadForRepo(String repoKey) {
        return isSystemRepo(repoKey) &&
                getSystemRepo(repoKey).isDownloadThroughAPI() &&
                !SecurityService.USER_SYSTEM.equals(securityService.currentUsername());
    }

    @VisibleForTesting
    boolean shouldBlockSystemFolderDownload(ArtifactoryRequest request) {
        if (!ConstantValues.systemFolderContractEnabled.getBoolean()) {
            return false;
        }
        if (request instanceof InternalArtifactoryRequest) {
            return false;
        }
        if (!repositoryService.isRemoteOrCacheFlow(request.getRepoKey())) {
            return false;
        }
        return SystemFolders.forPath(request.getRepoPath())
                .filter(folder -> folder == SystemFolders.SystemFolder.EVIDENCE)
                .map(folder -> !SystemFolders.isDownloadAllowed(folder))
                .orElse(false);
    }

    private void setRemoteAddress(ArtifactoryRequest request) {
        if (request instanceof InternalArtifactoryRequest) {
            Authentication authentication = getAuthentication();
            String remoteAddress = getRemoteAddress(authentication);
            if (remoteAddress != null) {
                ((InternalArtifactoryRequest) request).setClientAddress(remoteAddress);
            }
        }
    }

    private boolean responseWasIntercepted(ArtifactoryResponse response) {
        return response.isError();
    }

    @VisibleForTesting
    void sendAfterDownloadErrorUnifiedEvent(ArtifactoryRequest request, ResponseCtx responseCtx) {
        try {
            log.debug("Sending AfterDownloadError event via unifiedEventService for {}", request);
            DispatcherResponse response =
                    unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null);
            log.debug("Received AfterDownloadError response from unifiedEventService: {}", response);
            if (response != null && response.data() != null) {
                if (response.data().statusCode() != null) {
                    responseCtx.setStatus(response.data().statusCode());
                }
                if (response.data().message() != null) {
                    responseCtx.setMessage(response.data().message());
                }
                if (response.data().size() != null) {
                    responseCtx.setSize(response.data().size());
                }
            }
        } catch (UnifiedEventException e) {
            log.debug("AfterDownloadError event via unifiedEventService for {} was rejected: {}", request,
                    e.getMessage());
            if (e.statusCode == HttpStatus.SC_CONFLICT) {
                throw new WorkerStopException(e.getMessage());
            }
            throw e;
        }
    }

    @VisibleForTesting
    void respond(InternalRequestContext requestContext, ArtifactoryResponse response,
            RepoResource resource, boolean triggerDownloadInterceptors)
            throws IOException {
        try {
            Request request = requestContext.getRequest();

            boolean resourceFound = resource.isFound();
            boolean resourceBlocked = resource.isBlocked();
            boolean headRequest = request.isHeadOnly();
            boolean checksumRequest = request.isChecksum();
            boolean targetRepoIsNotRemoteOrDoesntStore = isRepoNotRemoteOrDoesntStoreLocally(resource);

            RepoRequests.logToContext("Requested resource is found = %s", resourceFound);
            RepoRequests.logToContext("Requested resource is blocked = %s", resourceBlocked);
            RepoRequests.logToContext("Request is HEAD = %s", headRequest);
            RepoRequests.logToContext("Request is for a checksum = %s", checksumRequest);
            RepoRequests.logToContext("Target repository is not remote or doesn't store locally = %s",
                    targetRepoIsNotRemoteOrDoesntStore);
            boolean notModified = isNotModified(request, resource);
            RepoRequests.logToContext("Requested resource was not modified = %s", notModified);

            if (isRemoteRepo(resource) && !isRelativeRoot(request)) {
                PathValidator.validate(request.getRepoPath().getPath());
            }
            if (!resourceFound) {
                RepoRequests.logToContext("Responding with unfound resource");
                respondResourceNotFound(requestContext, response, resource);
            } else if (resourceBlocked) {
                RepoRequests.logToContext("Responding with blocked resource");
                respondBlocked(requestContext, response, resource);
            } else if (headRequest && !checksumRequest && targetRepoIsNotRemoteOrDoesntStore) {
                /*
                 * Send head response only if the file isn't a checksum. Also, if the repo is a remote, only respond
                 * like this if we don't store artifacts locally (so that the whole artifact won't be requested twice),
                 * otherwise download the artifact normally and return the full info for the head request
                 */
                RepoRequests.logToContext("Responding to HEAD request with status %s", response.getStatus());
                if (notModified) {
                    respondNotModifiedResource(requestContext, response, resource);
                } else {
                    respondHeadResource(requestContext, response, resource);
                }
            } else if (notModified) {
                respondNotModifiedResource(requestContext, response, resource);
            } else if (checksumRequest) {
                RepoRequests.logToContext("Responding to checksum request");
                respondForChecksumRequest(request, response, resource);
            } else {
                RepoRequests.logToContext("Responding with found resource");
                respondFoundResource(requestContext, response, resource, triggerDownloadInterceptors);
            }
        } catch (FileNotFoundException e) {
            RepoRequests.logToContext("File deleted while sending request response: %s", e.getMessage());
            respondResourceNotFound(requestContext, response, resource);
        } catch (IOException e) {
            RepoRequests.logToContext("Error occurred while sending request response: %s", e.getMessage());
            handleGenericIoException(response, resource, e);
        } catch (InvalidPathException e) {
            String message = "Invalid path exception occurred while sending request response: " + e.getMessage();
            RepoRequests.logToContext(message);
            response.sendError(HttpStatus.SC_BAD_REQUEST, message);
        }
    }

    private boolean isRemoteRepo(RepoResource resource) {
        return repositoryService.existsByType(REMOTE,
                RepoPathUtils.removeDashCache(resource.getRepoPath().getRepoKey()));
    }

    private void respondBlocked(InternalRequestContext requestContext, ArtifactoryResponse response,
            RepoResource resource) throws IOException {
        if (resource instanceof BlockedRepoResource) {
            sendError(requestContext, response, resource, HttpStatus.SC_FORBIDDEN,
                    ((BlockedRepoResource) resource).getMessage());
        } else {
            sendError(requestContext, response, resource, HttpStatus.SC_FORBIDDEN, "Resource is blocked");
        }
    }

    @VisibleForTesting
    boolean isNotModified(Request request, RepoResource resource) {
        boolean hasIfModifiedSince = request.hasIfModifiedSince();
        boolean notModified = request.isNewerThan(resource.getLastModified());
        boolean hasIfNoneMatch = request.hasIfNoneMatch();
        boolean nonMatch = isNonMatch(request, resource);

        // Only If-Modified-Since header present
        if (hasIfModifiedSince && !hasIfNoneMatch && notModified) {
            RepoRequests.logToContext("Local resource isn't newer - sending a not modified response");
            return true;
        }
        // If-None-Match header present
        if (hasIfNoneMatch && !nonMatch) {
            RepoRequests.logToContext("Local resource's entity exists - sending a not modified response");
            return true;
        }
        return false;
    }

    boolean isNonMatch(Request request, RepoResource resource) {
        RepoResourceInfo resourceInfo = resource.getInfo();
        logTraceIsNoneMatch(resource, resourceInfo);
        boolean alsoCheckMD5 = includeMd5IntoETagCheck.getBoolean();
        Enumeration<String> headerValues = request.getHeaderValues(IF_NONE_MATCH_MD5);
        if (nonNull(headerValues) && headerValues.hasMoreElements() &&
                BooleanUtils.toBoolean(headerValues.nextElement())) {
            alsoCheckMD5 = true;
        }
        if (alsoCheckMD5) {
            return isSha1AndMd5NotMatch(request, resourceInfo);
        } else {
            return isSha1NotMatch(request, resourceInfo);
        }
    }

    private boolean isSha1AndMd5NotMatch(Request request, RepoResourceInfo resourceInfo) {
        return resourceInfo == null || (request.isNoneMatch(resourceInfo.getSha1())
                && request.isNoneMatch(resourceInfo.getMd5()));
    }

    private boolean isSha1NotMatch(Request request, RepoResourceInfo resourceInfo) {
        return resourceInfo == null || request.isNoneMatch(resourceInfo.getSha1());
    }

    private void logTraceIsNoneMatch(RepoResource resource, RepoResourceInfo resourceInfo) {
        if (!log.isTraceEnabled()) {
            return;
        }
        if (resourceInfo == null) {
            log.trace("Resource {} info is null", resource.getRepoPath());
        } else {
            log.trace("Resource {} sha1 {} md5 {} includeMd5IntoETagCheck {}",
                    resource.getRepoPath(), resourceInfo.getSha1(),
                    resourceInfo.getMd5(), includeMd5IntoETagCheck.getBoolean());
        }
    }

    private boolean isRepoNotRemoteOrDoesntStoreLocally(RepoResource resource) {
        RemoteRepoDescriptor remoteRepoDescriptor = repositoryService.remoteRepoDescriptorByKey(
                resource.getRepoPath().getRepoKey());
        return (remoteRepoDescriptor == null) || !remoteRepoDescriptor.isStoreArtifactsLocally();
    }

    private void respondNotModifiedResource(InternalRequestContext requestContext, ArtifactoryResponse response,
            RepoResource resource)
            throws IOException {
        response.setStatus(HttpStatus.SC_NOT_MODIFIED);
        ResourceStreamHandle handle = altResponseHelper
                .alternateResponse(requestContext, response, resource.getResponseRepoPath(), response.getStatus());

        if (response.getStatus() == HttpStatus.SC_NOT_MODIFIED) {
            requestResponseHelper.sendNotModifiedResponse(response, resource);
            return;
        }
        if (isError(response, handle)) {
            return;
        }
        if (requestContext.getRequest().isHeadOnly()) {
            requestResponseHelper.sendHeadResponse(response, resource, handle);
            return;
        }
        if (handle == null) {
            requestResponseHelper.sendEmptyResponse(response, resource);
            return;
        }
        RepoRequests.logToContext("Responding with selected content handle");
        requestResponseHelper.sendBodyResponse(response, resource, handle, requestContext, true);
    }

    private void respondHeadResource(InternalRequestContext requestContext, ArtifactoryResponse response,
            RepoResource resource)
            throws IOException {
        ResourceStreamHandle handle = altResponseHelper
                .alternateResponse(requestContext, response, resource.getResponseRepoPath(), response.getStatus());
        if (response.getStatus() == HttpStatus.SC_NOT_MODIFIED) {
            requestResponseHelper.sendNotModifiedResponse(response, resource);
            return;
        }
        if (isError(response, handle)) {
            return;
        }
        requestResponseHelper.sendHeadResponse(response, resource, handle);
    }

    private void respondFoundResource(InternalRequestContext requestContext, ArtifactoryResponse response,
            RepoResource resource, boolean triggerDownloadInterceptors) throws IOException {
        DownloadPerfCounters metrics = RequestThreadLocal.getDownloadPerfCounters(true);
        if (metrics != null) {
            metrics.setArtifactSizeBytes(resource.getSize());
        }

        //Get the actual repository the resource is in
        RepoPath responseRepoPath = resource.getResponseRepoPath();
        //Send the resource file back (will update the cache for remote repositories)
        ResourceStreamHandle handle =
                altResponseHelper.alternateResponse(requestContext, response, responseRepoPath, response.getStatus());
        if (handle == null) {
            handle = altResponseHelper.getAlternateHandle(requestContext, response, responseRepoPath);
        }
        if (isError(response, handle)) {
            return;
        }
        Repo responseRepo = repositoryService.repositoryByKey(responseRepoPath.getRepoKey());
        String signedUrl = null;
        RedirectType redirectTypeForRepo = null;

        try {
            if (handle == null) {
                RepoRequests.logToContext("Retrieving a content handle from target repo");
                handle = performDownloadIfResourceNotPresent(resource, requestContext, responseRepo);
                // ORDER IS IMPORTANT! check for redirect must be made after performDownloadIfResourceNotPresent
                ManualRedirectType manualRedirectType = getManualRedirectType(requestContext, responseRepo);
                redirectTypeForRepo =
                        getRedirectTypeForRepo(requestContext, responseRepo, resource, manualRedirectType);
                log.debug("Redirect type for repo {} is {}", responseRepoPath.getRepoKey(), redirectTypeForRepo);
                if (redirectTypeForRepo != RedirectType.DIRECT) {
                    BinaryElement element =
                            binaryElementFactory.getBinaryElement(requestContext, responseRepo, resource,
                                    manualRedirectType, redirectTypeForRepo);
                    SignedUrl url = getSignedUrl(element);
                    if (url == null) {
                        redirectTypeForRepo = RedirectType.DIRECT;
                    } else {
                        signedUrl = url.value();
                        redirectTypeForRepo = url.redirectType();
                    }
                }
                if (signedUrl == null) {
                    // Only if we didn't already read the artifact or set an alternate response
                    log.trace("Direct download, fetching resource:{}", handle == null);
                    if (handle == null) {
                        handle = repositoryService.getResourceStreamHandle(requestContext, responseRepo, resource);
                    }
                }
            }
            if (triggerDownloadInterceptors) {
                PluginsAddon pluginAddon = addonsManager.addonByType(PluginsAddon.class);
                RepoRequests.logToContext("Executing any BeforeDownload user plugins that may exist");
                DownloadCtx downloadCtx = new DownloadCtx();
                sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD,
                        (ArtifactoryRequest) requestContext.getRequest(), responseRepoPath, downloadCtx);
                appendHeaders(downloadCtx, response);
                pluginAddon.execPluginActions(BeforeDownloadAction.class, null, requestContext.getRequest(),
                        responseRepoPath);
            }

            if (requestContext.getRequest().isHeadOnly()) {
                /*
                 * If we should respond to a head, make sure repo is a remote and that stores locally (to save the
                 * double artifact downloads)
                 */
                RepoRequests.logToContext("Request was of type HEAD - responding with no content");
                requestResponseHelper.sendHeadResponse(response, resource);
            } else if (response.isPropertiesQuery()) {
                RepoRequests.logToContext("Request was of type Properties - responding with properties format "
                        + response.getPropertiesMediaType());
                Properties properties = propertiesService.getProperties(resource.getRepoPath());
                if (!properties.isEmpty()) {
                    MediaType mediaType = MediaType.valueOf(response.getPropertiesMediaType());
                    String content;
                    if (mediaType.equals(MediaType.APPLICATION_XML)) {
                        content = InternalContextHelper.get().beanForType(
                                MetadataDefinitionService.class).getMetadataDefinition(
                                Properties.class).getXmlProvider().toXml(properties);
                    } else if (mediaType.equals(MediaType.APPLICATION_JSON)) {
                        content = jsonProperties(responseRepoPath, properties);
                    } else {
                        response.sendError(HttpStatus.SC_BAD_REQUEST,
                                "Media Type " + mediaType + " not supported!", log);
                        return;
                    }
                    requestResponseHelper.updateResponseForProperties(response, resource, content, mediaType,
                            requestContext);
                } else {
                    RepoRequests.logToContext("No properties found. Responding with 404");
                    response.sendError(HttpStatus.SC_NOT_FOUND, "No properties could be found.", log);
                }
            } else {
                RepoRequests.logToContext("Responding with selected content handle");
                sendStreamOrRedirect(requestContext, response, resource, responseRepoPath, handle, signedUrl,
                        requestContext.getRequest(), responseRepo, redirectTypeForRepo, triggerDownloadInterceptors);
            }
        } catch (RepoRejectException | BinaryRejectedException rre) {
            int status = rre.getErrorCode();
            log.debug("Repo rejection while downloading: " + rre.getMessage(), rre);
            if (shouldSendAuthRequired(status, resource)) {
                RepoRequests.logToContext("Response status is '%s' and authenticated as anonymous - sending challenge",
                        status);

                // Transform a forbidden to unauthorized if received for an anonymous user
                response.sendAuthorizationRequired(rre.getMessage(), authenticationEntryPoint.getRealmName());
            } else {
                RepoRequests.logToContext("Error occurred while sending response - sending error instead: %s",
                        rre.getMessage());
                String msg = "Rejected artifact download request: " + rre.getMessage();
                if (rre instanceof XrayRepoRejectException && ConstantValues.xrayRemoteValidate.getBoolean()) {
                    response.setXrayOriginHeader("true");
                }
                sendError(requestContext, response, resource, status, msg);
            }
        } catch (RemoteRequestException rre) {
            log.info("Failed to complete remote request during download. Error: {}. Remote server returned status " +
                    "code: {}", rre.getMessage(), rre.getRemoteReturnCode());
            RepoRequests.logToContext("Error occurred while sending response - sending error instead: %s",
                    rre.getMessage());
            response.setThirdPartyOriginHeader(Boolean.TRUE.toString());
            int statusCode = getStatusCode(rre);
            sendError(requestContext, response, resource, statusCode, rre.getMessage());
        } catch (BadPomException bpe) {
            log.debug("Bad pom while downloading: " + bpe.getMessage(), bpe);
            RepoRequests.logToContext("Error occurred while sending response - sending error instead: %s",
                    bpe.getMessage());
            sendError(requestContext, response, resource, HttpStatus.SC_CONFLICT, bpe.getMessage());
        } catch (BinaryStorageException bse) {
            log.debug("Exception while downloading: " + bse.getMessage(), bse);
            RepoRequests.logToContext("Error occurred while sending response - sending error instead: %s",
                    bse.getMessage());
            sendError(requestContext, response, resource, HttpStatus.SC_BAD_REQUEST, bse.getMessage());
        } catch (StorageException se) {
            log.debug("Exception while downloading: " + se.getMessage(), se);
            RepoRequests.logToContext("Error occurred while sending response - sending error instead: %s",
                    se.getMessage());
            sendError(requestContext, response, resource, HttpStatus.SC_INTERNAL_SERVER_ERROR, se.getMessage());
        } catch (WorkerStopException e) {
            log.debug("Exception while running the Before Download worker: " + e.getMessage(), e);
            RepoRequests.logToContext("Before Download worker have thrown an exception with message: %s",
                    e.getMessage());
            sendError(requestContext, response, resource, HttpStatus.SC_CONFLICT, e.getMessage());
        } catch (UnsupportedRedirectTypeException re) {
            log.debug("Exception while reading redirect type: " + re.getMessage(), re);
            RepoRequests.logToContext("Error occurred while sending response - sending error instead: %s",
                    re.getMessage());
            sendError(requestContext, response, resource, HttpStatus.SC_INTERNAL_SERVER_ERROR, re.getMessage());
        } finally {
            if (handle != null) {
                response.close(handle);
            }
        }
    }

    @VisibleForTesting
    void reportRedirectInfo(RedirectType redirectType) {
        if (redirectType == null) {
            log.trace("Redirect type is missing");
            return;
        }
        if (redirectType != RedirectType.DIRECT) {
            RedirectInfo redirectInfo = RequestThreadLocal.getRedirectInfo();
            if (redirectInfo != null) {
                redirectInfo.setRedirectTarget(TrafficEntry.TrafficTarget.valueOf(redirectType).displayName());
            }
        }
    }

    @VisibleForTesting
    void reportControlledCacheRedirect() {
        RedirectInfo redirectInfo = RequestThreadLocal.getRedirectInfo();
        if (redirectInfo != null) {
            redirectInfo.setRedirectTarget(TrafficEntry.TrafficTarget.CONTROLLED_CACHE.displayName());
        }
    }

    private boolean isError(ArtifactoryResponse response, ResourceStreamHandle handle) {
        boolean alternateRedirect = BasicHttpUtils.isRedirectionResponseCode(response.getStatus()) && handle != null;
        // Error is every value that is not between 200-207, in case of alternateRedirect the status is 30x therefore
        //we have to exclude this case
        if (response.isError() && !alternateRedirect) {
            RepoRequests.logToContext("Alternative response reset status as error - returning");
            return true;
        }
        return false;
    }

    /**
     * Determines the appropriate status code to return based on the remote request exception.
     * If the remote return code is 5xx, it returns 502 bad gateway.
     * Otherwise, it returns the original remote return code.
     *
     * @param rre the RemoteRequestException containing the remote return code
     * @return the appropriate status code to return
     */
    private int getStatusCode(RemoteRequestException rre) {
        int statusCode = rre.getRemoteReturnCode();
        if (statusCode >= 500) {
            statusCode = HttpStatus.SC_BAD_GATEWAY;
        }
        return statusCode;
    }

    /**
     * In case the desired artifact is of a remote repository - the artifact is not cached:
     * perform download and cache it, no matter if we are going to redirect the response or not.
     *
     * In case we will redirect - stream will be closed later, and the actual stream won't be read.
     * In case we won't redirect - stream will be served to the client, then closed.
     *
     * If its not of a remote type, artifact is present and stream will be read later or redirected.
     */
    private ResourceStreamHandle performDownloadIfResourceNotPresent(RepoResource resource,
            InternalRequestContext requestContext, Repo responseRepo) throws IOException, RepoRejectException {
        if (resource instanceof RemoteRepoResource) {
            RepoRequests.logToContext("Resource is not yet in cache, performing download first");
            return repositoryService.getResourceStreamHandle(requestContext, responseRepo, resource);
        }
        return null;
    }

    /**
     * We validate that the repo is not configured to serve download redirect while store locally is disabled
     * (can't give a url if the file is not stored in our binary provider).
     *
     * We validate the two settings in the repo creation/update, but currently there is no validation for that
     * through repo YAML update (ConfigResource class), hence we are checking that on runtime as well.
     *
     * Can be deleted once we add validation for ALL repo update methods.
     */
    private boolean isRepoAllowsStoringLocally(Repo responseRepo) {
        if (responseRepo instanceof RemoteRepoBase) {
            if (!((RemoteRepoBase) responseRepo).isStoreArtifactsLocally()) {
                log.warn("Can't perform download redirect from a repository that " +
                        "does not allow storing artifacts locally. Requested artifact is not stored locally.");
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the signed url from cloud provider.
     *
     * @return Signed url, or null in case couldn't generate it
     */
    @VisibleForTesting
    @Nullable
    SignedUrl getSignedUrl(BinaryElement element) {
        try {
            return binaryService.getSignedUrl(element);
        } catch (SignedUrlException e) {
            log.warn("Signed url couldn't be generated, falling back to normal download: {}", e.getMessage());
            log.debug("Signed url couldn't be generated, falling back to normal download", e);
        }
        return null;
    }

    void sendStreamOrRedirect(InternalRequestContext requestContext, ArtifactoryResponse response,
            RepoResource resource, RepoPath responseRepoPath, ResourceStreamHandle handle,
            String signedUrl, Request request, Repo<?, ?> responseRepo,
            RedirectType redirectType, boolean triggerDownloadInterceptors)
            throws IOException {
        log.debug("Downloading repo_path={} redirect_type={}", responseRepoPath, redirectType);
        if (controlledCacheRedirectService.shouldRedirect(requestContext.getRequest(), resource, redirectType)) {
            // Validate requests for cacheable repos (DIRECT or cloud-backed) take priority over the
            // signed URL path so the controlled cache protocol can serve the artifact from the edge.
            log.debug("Serving download with reverse proxy cache");
            reportControlledCacheRedirect();
            handleControlledCacheDownload(requestContext, response, resource, responseRepoPath, redirectType);
        } else if (signedUrl != null) {
            log.debug("Serving download with redirect to signed url");
            response.setRedirect(signedUrl);
            reportRedirectInfo(redirectType);
            TrafficEntry.TrafficTarget trafficTarget = TrafficEntry.TrafficTarget.valueOf(redirectType);
            if (ConstantValues.trafficIgnoreRangeHeaderWhenCalcBinarySizeForRedirect.getBoolean()) {
                fireDownloadRedirectEvent(response, responseRepoPath, resource.getSize());
                fireDownloadRedirectEventV2(requestContext, response, trafficTarget, responseRepoPath,
                        resource.getSize(), resource.getSize());
            } else {
                boolean multiRangeSupports = RedirectType.S3 != redirectType;
                long responseLength = getResponseLength(requestContext.getRequest(), resource, multiRangeSupports);
                fireDownloadRedirectEvent(response, responseRepoPath, responseLength);
                fireDownloadRedirectEventV2(requestContext, response, trafficTarget, responseRepoPath,
                        resource.getSize(), responseLength);
            }
            // Flushing the response here to make sure other components won't change the response status
            response.flush();
            if (handle == null) {
                // This means the artifact requested is in a local repository, and we need to update stats
                // (in case of remote we update the stats in getResourceStreamHandle method previously called)
                statsService
                        .updateDownloadStatsIfNeeded(responseRepoPath, request, responseRepo.getDescriptor().isReal());
            }
        } else {
            log.debug("Serving download with stream in response");
            //Streaming the file is done outside a tx, so there is a chance that the content will change!
            requestResponseHelper.sendBodyResponse(response, resource, handle, requestContext,
                    triggerDownloadInterceptors);
        }
    }

    private void notifyOnSuccessfulPackageDownloadIfNeeded(RepoResource resource, RepoType repoType) {
        if (resource == null || resource.getRepoPath() == null) {
            return;
        }
        if (repoType.isMavenGroup() && MavenNaming.isPom(resource.getPath())) {
            addonsManager.addonByType(OnBoardingAddon.class).userDownloadedPackage(
                    repoType, resource.getRepoKey(), resource.getRepoPath().getName(), "");
        }
    }

    long getResponseLength(Request request, RepoResource resource, boolean multiRangeSupports) {
        long lastModified = resource.getLastModified();
        String sha1 = resource.getInfo().getSha1();
        long size = resource.getSize();
        String ifRangesString = tryToGetRangeHeaderInsensitive(HttpHeaders.IF_RANGE, request);
        String rangesString = tryToGetRangeHeaderInsensitive(HttpHeaders.RANGE, request);

        return calculateContentLength(size, rangesString, ifRangesString, lastModified, sha1, multiRangeSupports);
    }

    /**
     * Type of redirect depends on whether to redirect according to the conditions below and the configuration on the
     * repo.
     * Will send HTTP 302 redirect in case:
     * 1. Repository type is configured to allow redirect
     * 2. No override redirect header is present on request
     * 3. Repository can store artifact locally
     * 4. Requested artifact size is greater-equals to the redirect threshold
     * 5. Artifact is not in eventual
     * 6. License is E+ or Edge. if AOL we ignore license check
     */
    RedirectType getRedirectTypeForRepo(InternalRequestContext requestContext, Repo responseRepo,
            RepoResource resource, ManualRedirectType manualRedirectType) {

        RepoDescriptor descriptor = responseRepo.getDescriptor();
        boolean isAol = AolUtils.isAol();

        if (!isAol && manualRedirectType.equals(ManualRedirectType.NONE)) {
            return RedirectType.DIRECT;
        }

        boolean shouldRedirect =
                shouldRedirect(requestContext, responseRepo, resource, descriptor, isAol);
        if (shouldRedirect) {
            if (canUseCdn(responseRepo)) {
                if (manualRedirectType.equals(ManualRedirectType.S3)) {
                    log.trace("Redirecting to S3 instead of CF because of manual header");
                    return RedirectType.S3;
                }
                return RedirectType.CLOUD_FRONT;
            }
            return RedirectType.S3;
        } else {
            return RedirectType.DIRECT;
        }
    }

    protected ManualRedirectType getManualRedirectType(InternalRequestContext requestContext, Repo responseRepo) {
        ManualRedirectType header = getHeaderByPriority(requestContext, MANUAL_REDIRECT_TYPE)
                .map(ManualRedirectType::fromType)
                .orElse(ManualRedirectType.DEFAULT);
        if (header.equals(ManualRedirectType.CF) && !canUseCdn(responseRepo)) {
            header = ManualRedirectType.DEFAULT;
        }

        return header;
    }

    // First try to get the header from the thread local request and if not exist try from the context request.
    // In each, first try the header by its normal name and then in the Docker client form (prefixed by X-Meta...).
    @NonNull
    static Optional<String> getHeaderByPriority(InternalRequestContext requestContext,
            BinaryElementHeaders elementHeader) {
        HttpServletRequest request = RequestThreadLocal.getRequest();
        String normalName = elementHeader.getHeaderName();
        String dockerName = elementHeader.getDockerHeaderName();
        return Stream.of(
                        getHeader(request, normalName),
                        getHeader(request, dockerName),
                        getHeader(requestContext, normalName),
                        getHeader(requestContext, dockerName))
                .filter(StringUtils::isNotBlank)
                .findFirst();
    }

    private static String getHeader(HttpServletRequest request, String headerName) {
        return request == null ? null : request.getHeader(headerName);
    }

    private static String getHeader(InternalRequestContext requestContext, String headerName) {
        var headerValues = requestContext.getRequest().getHeaderValues(headerName);
        return headerValues == null || !headerValues.hasMoreElements() ? null : headerValues.nextElement();
    }

    protected boolean canUseCdn(Repo responseRepo) {
        return (AolUtils.isAol() && downloadRedirectHelper.isCdnForcedForThisRepoType(responseRepo))
                || canUseCdnWithLicense(responseRepo);
    }

    protected boolean canUseCdnWithLicense(@NonNull Repo responseRepo) {
        if (AolUtils.isAol()) {
            return isCdnRedirectEnabled(responseRepo) &&
                    (addonsManager.isEntitled(EntitlementType.CDN) ||
                            (addonsManager.isEntitled(EntitlementType.ANONYMOUS_CDN) && isCdnAllowedByAuth()));
        } else {
            // For On-Prem, redirect (both S3 and CDN) is enabled only for E+ and Edge licenses
            // That is checked as part of shouldRedirect method with the entitlement:
            // addonsManager.isEntitled(EntitlementType.BINARY_PROVIDER_DOWNLOAD_REDIRECT)
            // If allowed, we default to CDN redirect. Then it's up to the manual header or the redirect service itself
            // to override it to S3.
            return true;
        }
    }

    /**
     * CDN redirect on Enterprise is enabled only for anonymous user or signed url created by ANY user
     */
    private boolean isCdnAllowedByAuth() {
        Authentication authentication = getAuthentication();
        boolean isGrantedBySignedUrl = authentication instanceof SignedUrlAuthenticationToken;
        return isGrantedBySignedUrl || securityService.isAnonymous();
    }

    private boolean isCdnRedirectEnabled(@NonNull Repo responseRepo) {
        return responseRepo.getDescriptor().isCdnRedirect();
    }

    boolean shouldRedirect(InternalRequestContext requestContext, Repo responseRepo, RepoResource resource,
            RepoDescriptor descriptor, boolean isAol) {
        return !Boolean.parseBoolean(requestContext.getRequest().getParameter(PARAM_FORCE_GET_STREAM))
                && !requestContext.getRequest().isHeadOnly()
                && (descriptor.isReal() || ConstantValues.virtualDownloadRedirectEnabled.getBoolean())
                && downloadRedirectHelper.isRedirectEnable(responseRepo, descriptor)
                && (addonsManager.isEntitled(EntitlementType.BINARY_PROVIDER_DOWNLOAD_REDIRECT) || isAol)
                && isRepoAllowsStoringLocally(responseRepo) // order is important, log message inside it
                && downloadRedirectHelper.isArtifactGreaterEqualsRedirectThreshold(resource)
                && resource.getInfo().getSha1() != null
                && binaryService.canCloudProviderGenerateRedirection();
    }

    /**
     * @param size sent in this event is the size according to nodes table (while on real download we send the
     *             actual size according to resource input stream)
     */
    private void fireDownloadRedirectEvent(ArtifactoryResponse response, RepoPath repoPath, long size) {
        if (!(response instanceof InternalArtifactoryResponse)) {
            String remoteAddress = HttpUtils.getRemoteClientAddress();
            RedirectEntry redirectEntry = new RedirectEntry(
                    repoPath.getId(), size, System.currentTimeMillis(), remoteAddress);
            trafficService.handleTrafficEntry(redirectEntry);
        }
    }

    /**
     * @param fullSize sent in this event is the size according to nodes table (while on real download we send the
     *                 actual size according to resource input stream)
     */
    private void fireDownloadRedirectEventV2(InternalRequestContext requestContext, ArtifactoryResponse response,
            TrafficEntry.TrafficTarget trafficTarget, RepoPath repoPath,
            long fullSize, long requestedSize) {
        if (!(response instanceof InternalArtifactoryResponse)) {
            String remoteAddress = HttpUtils.getRemoteClientAddress();
            String username = securityService.currentUsername();
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String userAgent = HttpUtils.getRequestUserAgent(requestContext.getRequest());
            TrafficEntry.TrafficActor actor = TrafficUtils.getTrafficActor(userAgent, username, authentication);
            TrafficEntry entry = TrafficEntry.builder()
                    .action(TrafficEntry.TrafficAction.REDIRECT)
                    .actor(actor)
                    .redirectTarget(trafficTarget)
                    .userAddress(remoteAddress)
                    .repoPath(repoPath.getId())
                    .traceId(MDC.get("uber-trace-id"))
                    .size(fullSize)
                    .requestedSize(requestedSize)
                    .transferredSize(requestedSize)
                    .build();
            trafficService.handleTrafficEntry(entry);
        }
    }

    private String jsonProperties(RepoPath repoPath, Properties properties) throws IOException {
        ItemProperties itemProperties = new ItemProperties();
        for (String propertyName : properties.keySet()) {
            Set<String> propertySet = properties.get(propertyName);
            if ((propertySet != null) && !propertySet.isEmpty()) {
                itemProperties.properties.put(propertyName, Iterables.toArray(propertySet, String.class));
            }
        }
        itemProperties.slf = repoPath.getRepoKey() + "/" + repoPath.getPath();
        StringWriter out = new StringWriter();
        try (JsonGenerator generator = JacksonFactory.createGenerator(out)) {
            generator.writeObject(itemProperties);
            return out.getBuffer().toString();
        }
    }

    private void sendError(InternalRequestContext requestContext, ArtifactoryResponse response, RepoResource resource,
            int status, String reason) throws IOException {
        RepoRequests.logToContext("Sending error with status %s and message '%s'", status, reason);
        ResponseCtx responseCtx = new ResponseCtx();
        responseCtx.setMessage(reason);
        responseCtx.setStatus(status);
        applyAfterDownloadErrorActions((ArtifactoryRequest) requestContext.getRequest(), responseCtx);

        status = responseCtx.getStatus();
        String message = responseCtx.getMessage();

        if (isSuccessfulResponseCode(status)) {//plugin changed the status, it's not error anymore
            RepoRequests.logToContext("Response code was modified to %s by the user plugins", status);
            response.setStatus(status);
            if (responseCtx.getInputStream() == null) {// no content, so only message (if set) and status
                RepoRequests.logToContext("Received no response content from the user plugins");
                //message changed in the plugin, need to write it as response
                if (reason != null && !reason.equals(message)) {
                    RepoRequests.logToContext("Response message was modified to '%s' by the user plugins", message);
                    response.getWriter().write(message);
                }
                RepoRequests.logToContext("Sending successful response");
                response.sendSuccess();
            } else {//yay, content from plugin!
                RepoRequests.logToContext("Received a response content stream from the user plugins - sending");
                if (responseCtx.hasSize()) {
                    response.setContentLength(responseCtx.getSize());
                }
                response.sendStream(responseCtx.getInputStream());
            }
        } else { //still error, proceed as usual
            RepoRequests.logToContext("Response code wasn't modified by the user plugins");
            if (!Objects.equals(message, reason)) {
                RepoRequests.logToContext("Response message was modified to '%s' by the user plugins", message);
            }
            reason = message; // in case user changed the reason in the plugin
            if (shouldSendAuthRequired(status, resource)) {
                RepoRequests.logToContext("Response status is '%s' and authenticated as anonymous - sending challenge",
                        status);
                // Transform a forbidden to unauthorized if received for an anonymous user
                String realmName = authenticationEntryPoint.getRealmName();
                response.sendAuthorizationRequired(reason, realmName);
            } else {
                RepoRequests.logToContext("Sending response with the status '%s' and the message '%s'", status,
                        message);
                response.sendError(status, reason, log,
                        new ArtifactoryResourceInfo(resource.isFound(), resource.isExpired(), resource.isBlocked()));
            }
        }
    }

    private boolean shouldSendAuthRequired(int status, RepoResource resource) {
        return status == HttpStatus.SC_FORBIDDEN &&
                securityService.isAnonymous() && !resource.isBlocked();
    }

    private void respondResourceNotFound(InternalRequestContext requestContext, ArtifactoryResponse response,
            RepoResource resource) throws IOException {
        String detail = "Resource not found";
        int status = HttpStatus.SC_NOT_FOUND;
        RepoRequests.logToContext("Setting default response status to '%s' reason to '%s'", status, detail);
        if (resource instanceof UnfoundRepoResourceReason) {
            RepoRequests.logToContext("Response is an instance of UnfoundRepoResourceReason");
            UnfoundRepoResourceReason unfound = (UnfoundRepoResourceReason) resource;
            // use the reason and status from the resource unless it's authorization response and the
            // settings prohibit revealing this information
            boolean hideUnauthorizedResources = shouldHideUnauthorisedResponse(requestContext);
            boolean originalStatusNotAuthorization = notAuthorizationStatus(unfound.getStatusCode());

            RepoRequests.logToContext("Original response status is auth related = %s",
                    Boolean.toString(!originalStatusNotAuthorization));
            if (!hideUnauthorizedResources || originalStatusNotAuthorization) {
                detail = unfound.getDetail();
                status = unfound.getStatusCode();
                RepoRequests.logToContext("Using original response status of '%s' and message '%s'", status, detail);
            }
        }
        detail += "; Path: '" + resource.getRepoPath().toString() + "'";
        if (shouldSendAuthRequired(status, resource)) {
            RepoRequests.logToContext("Response status is '%s' and authenticated as anonymous - sending challenge",
                    status);
            // Transform a forbidden to unauthorized if received for an anonymous user
            String realmName = authenticationEntryPoint.getRealmName();
            response.sendAuthorizationRequired(detail, realmName);
        } else {
            sendError(requestContext, response, resource, status, detail);
        }
    }

    @VisibleForTesting
    boolean shouldHideUnauthorisedResponse(InternalRequestContext requestContext) {
        boolean hideUnauthorizedResources = centralConfig.getDescriptor().getSecurity().isHideUnauthorizedResources();
        RepoRequests.logToContext("Configured to hide un-authorized resources = %s",
                Boolean.toString(hideUnauthorizedResources));
        if (hideUnauthorizedResources) {
            return true;
        }

        // Check if current repo is set to hide unauthorized resource response (401/403)
        // (only for virtual repos)
        String repoKey = requestContext.getRequest().getRepoPath().getRepoKey();
        if (repositoryService.isVirtualRepoExist(repoKey)) {
            VirtualRepo repo = repositoryService.virtualRepositoryByKey(repoKey);
            if (repo != null) {
                hideUnauthorizedResources = repo.getDescriptor().isHideUnauthorizedResources();
            }
        }
        RepoRequests.logToContext("Configured to hide real status of un-authorized resources = %s for repo %s",
                Boolean.toString(hideUnauthorizedResources), repoKey);
        return hideUnauthorizedResources;
    }

    private boolean notAuthorizationStatus(int status) {
        return status != HttpStatus.SC_UNAUTHORIZED && status != HttpStatus.SC_FORBIDDEN;
    }

    /**
     * This method handles the response to checksum requests (HEAD and GET). Even though this method is called only for
     * files that exist, some checksum policies might return null values, or even fail if the checksum algorithm is not
     * found. If for any reason we don't have the checksum we return http 404 to the client and let the client decide
     * how to proceed.
     */
    private void respondForChecksumRequest(Request request, ArtifactoryResponse response,
            RepoResource resource) throws IOException {

        RepoPath requestRepoPath = request.getRepoPath();
        if (request.isZipResourceRequest()) {
            RepoRequests.logToContext("Requested resource is located within an archive");
            requestRepoPath = InternalRepoPathFactory
                    .archiveResourceRepoPath(requestRepoPath, request.getZipResourcePath());
        }
        String requestChecksumFilePath = requestRepoPath.getPath();
        ChecksumType checksumType = ChecksumType.forFilePath(requestChecksumFilePath);
        if (checksumType == null) {
            RepoRequests.logToContext("Unable to detect the type of the requested checksum - responding with status %s",
                    HttpStatus.SC_NOT_FOUND);
            response.sendError(HttpStatus.SC_NOT_FOUND, "Checksum not found: " + requestChecksumFilePath, log);
            return;
        }

        RepoPath responseRepoPath = resource.getResponseRepoPath();
        String repoKey = responseRepoPath.getRepoKey();
        String responsePath = responseRepoPath.getPath();
        Repo repository = repositoryService.repositoryByKey(repoKey);
        String checksum = repository.getChecksum(responsePath + checksumType.ext(), resource);
        if (checksum == null) {
            RepoRequests.logToContext("Unable to find the requested checksum - responding with status %s",
                    HttpStatus.SC_NOT_FOUND);
            response.sendError(HttpStatus.SC_NOT_FOUND, "Checksum not found for " + responsePath, log);
            return;
        }

        if (request.isHeadOnly()) {
            RepoRequests.logToContext("Sending checksum HEAD response");
            // send head response using the checksum data
            ChecksumResource checksumResource = new ChecksumResource(resource, checksumType, checksum);
            requestResponseHelper.sendHeadResponse(response, checksumResource);
        } else {
            PluginsAddon pluginAddon = addonsManager.addonByType(PluginsAddon.class);
            RepoRequests.logToContext("Executing any BeforeDownloadAction user plugins that may exist");
            DownloadCtx downloadCtx = new DownloadCtx();
            try {
                sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, (ArtifactoryRequest) request, responseRepoPath,
                        downloadCtx);
                appendHeaders(downloadCtx, response);
            } catch (WorkerStopException e) {
                RepoRequests.logToContext("Before Download worker have thrown an exception with message: %s",
                        e.getMessage());
                response.sendError(HttpStatus.SC_CONFLICT, e.getMessage(), log);
                appendHeaders(downloadCtx, response);
                return;
            }

            pluginAddon.execPluginActions(BeforeDownloadAction.class, null, request, responseRepoPath);
            // send the checksum as the response body, use the original repo path (the checksum path,
            // not the file) from the request
            RepoRequests.logToContext("Sending checksum response with status %s", response.getStatus());

            requestResponseHelper
                    .sendBodyResponseForChecksum(response, requestRepoPath, checksum, request, resource.isExpirable());
        }
    }

    @VisibleForTesting
    void appendHeaders(DownloadCtx downloadCtx, ArtifactoryResponse response) {
        Map<String, String> headers = downloadCtx.getHeaders();
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                response.setHeader(entry.getKey(), entry.getValue());
            }
        }
    }

    private void handleGenericIoException(ArtifactoryResponse response, RepoResource res, IOException e)
            throws IOException {
        if (e instanceof InterruptedIOException) {
            String msg = this + ": Timed out when retrieving data for " + res.getRepoPath()
                    + " (" + e.getMessage() + ").";
            RepoRequests.logToContext("Setting response status to %s - %s", HttpStatus.SC_NOT_FOUND, msg);
            response.sendError(HttpStatus.SC_NOT_FOUND, msg, log);
        } else {
            throw e;
        }
    }

    /**
     * Redirect with generated redirect url contains local repo path and sha1
     * Add 'X-JFrog-Cache-Response' to response.
     * Consider redirect as a real download, fire traffic log events.
     */
    private void handleControlledCacheDownload(InternalRequestContext requestContext, ArtifactoryResponse response,
            RepoResource resource, RepoPath responseRepoPath, RedirectType redirectType) {
        URI redirect = controlledCacheRedirectService.createRedirectUri(resource.getInfo());
        log.debug("Redirecting to {}", redirect.toString());
        // Add header to be used in caching proxy.
        String responseStrategy = (RedirectType.DIRECT == redirectType)
                ? JFROG_CONTROLLED_CACHE_RESPONSE_STREAM_VALUE
                : JFROG_CONTROLLED_CACHE_RESPONSE_REDIRECT_VALUE;
        response.setHeader(X_JFROG_CONTROLLED_CACHE_RESPONSE, responseStrategy);
        response.setRedirect(redirect.toString());
        response.flush();

        // Fire traffic log events, redirect will be considered as real download.
        long responseLength = getResponseLength(requestContext.getRequest(), resource, true);
        fireDownloadRedirectEvent(response, responseRepoPath, responseLength);
        fireDownloadRedirectEventV2(requestContext, response, TrafficEntry.TrafficTarget.CONTROLLED_CACHE,
                responseRepoPath, resource.getSize(), responseLength);
    }
}
