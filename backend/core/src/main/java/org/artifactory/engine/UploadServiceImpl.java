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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.RestCoreAddon;
import org.artifactory.addon.analytics.AnalyticsAddon;
import org.artifactory.addon.leadartifactdetector.LeadArtifactDetectorAddon;
import org.artifactory.addon.onboarding.OnBoardingAddon;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.impl.PluginPackageTypeResolver;
import org.artifactory.addon.plugin.pkgtype.impl.PluginTypeHandlers;
import org.artifactory.addon.plugin.upload.BeforeUploadRequestAction;
import org.artifactory.addon.plugin.upload.UploadCtx;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.repo.exception.maven.BadPomException;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.request.GracefulShutdownAware;
import org.artifactory.api.request.TranslatedArtifactoryRequest;
import org.artifactory.api.request.UploadService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.application.service.CommitContextPropertiesProvider;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.MutableFileInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.fs.StatsInfo;
import org.artifactory.md.MutablePropertiesInfo;
import org.artifactory.md.Properties;
import org.artifactory.md.PropertiesXmlProvider;
import org.artifactory.md.StatsInfoXmlProvider;
import org.artifactory.mime.MavenNaming;
import org.artifactory.mime.NamingUtils;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoStatDto;
import org.artifactory.repo.SaveResourceContext;
import org.artifactory.repo.cache.expirable.InternalMetadataChecker;
import org.artifactory.repo.db.DbCacheRepo;
import org.artifactory.repo.local.ValidDeployPathContext;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.deploy.ArtifactoryDeployRequest;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.MutableRepoResourceInfo;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.storage.checksum.replication.StorageChecksumReplicationConfig;
import org.artifactory.storage.checksum.replication.StorageChecksumReplicationService;
import org.artifactory.storage.fs.service.RepoStatsService;
import org.artifactory.storage.fs.service.StatsService;
import org.artifactory.traffic.TrafficService;
import org.artifactory.traffic.context.TrafficContextThreadLocal;
import org.artifactory.unifiedevent.UnifiedEventService;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.util.HttpUtils;
import org.artifactory.webapp.servlet.DelayedHttpResponse;
import org.artifactory.webapp.servlet.HttpArtifactoryRequest;
import org.artifactory.webapp.servlet.HttpArtifactoryResponse;
import org.jfrog.access.common.ScopeContainer;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.storage.binstore.common.ChecksumInputStream;
import org.jfrog.storage.binstore.exceptions.BinaryNotFoundException;
import org.jfrog.storage.binstore.exceptions.BinaryRejectedException;
import org.jfrog.storage.binstore.exceptions.BinaryStorageException;
import org.jfrog.storage.binstore.ifc.ArtificialInputStream;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.jfrog.storage.binstore.utils.Checksum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.artifactory.api.request.GracefulShutdownAware.noActiveTracking;
import static org.artifactory.api.request.GracefulShutdownAware.track;
import static org.artifactory.util.HttpUtils.getMd5Checksum;
import static org.artifactory.util.HttpUtils.getSha1Checksum;
import static org.artifactory.util.HttpUtils.getSha256Checksum;
import static org.artifactory.util.UploadServiceUtils.adjustAndGetChecksumTargetRepoPath;
import static org.artifactory.util.UploadServiceUtils.adjustMavenSnapshotPath;
import static org.artifactory.util.UploadServiceUtils.buildArtifactUrl;
import static org.artifactory.util.UploadServiceUtils.commitResponseIfDelayed;
import static org.artifactory.util.UploadServiceUtils.consumeContentAndRespondAccepted;
import static org.artifactory.util.UploadServiceUtils.consumeContentAndRespondWithSuccess;
import static org.artifactory.util.UploadServiceUtils.consumeRequestBody;
import static org.artifactory.util.UploadServiceUtils.fireUploadTrafficEvent;
import static org.artifactory.util.UploadServiceUtils.fireUploadTrafficEventV2;
import static org.artifactory.util.UploadServiceUtils.getBinarySize;
import static org.artifactory.util.UploadServiceUtils.getChecksumContentAsString;
import static org.artifactory.util.UploadServiceUtils.getInputStream;
import static org.artifactory.util.UploadServiceUtils.getTargetRepository;
import static org.artifactory.util.UploadServiceUtils.isAbnormalChecksumContentLength;
import static org.artifactory.util.UploadServiceUtils.isAbnormalPropertiesContentLength;
import static org.artifactory.util.UploadServiceUtils.isAbnormalStatisticsContentLength;
import static org.artifactory.util.UploadServiceUtils.isCheckBinaryExistenceInFilestore;
import static org.artifactory.util.UploadServiceUtils.isChecksumDeploy;
import static org.artifactory.util.UploadServiceUtils.isChecksumValidAccordingToPolicy;
import static org.artifactory.util.UploadServiceUtils.isDeployArchiveBundle;
import static org.artifactory.util.UploadServiceUtils.isMavenRepo;
import static org.artifactory.util.UploadServiceUtils.isRepoSnapshotPolicyNotDeployer;
import static org.artifactory.util.UploadServiceUtils.isRequestedRepoKeyInvalid;
import static org.artifactory.util.UploadServiceUtils.isSuccess;
import static org.artifactory.util.UploadServiceUtils.isTargetRepositoryInvalid;
import static org.artifactory.util.UploadServiceUtils.isVirtualRepoKey;
import static org.artifactory.util.UploadServiceUtils.populateAndValidateItemPropertiesFromRequest;
import static org.artifactory.util.UploadServiceUtils.populateContentLength;
import static org.artifactory.util.UploadServiceUtils.populateItemInfoFromHeaders;
import static org.artifactory.util.UploadServiceUtils.processOriginatedExternally;
import static org.artifactory.util.UploadServiceUtils.rejectionSignifiesRequiredAuthorization;
import static org.artifactory.util.UploadServiceUtils.responseWasIntercepted;
import static org.artifactory.util.UploadServiceUtils.sendInvalidUploadedChecksumResponse;
import static org.artifactory.util.UploadServiceUtils.sendUploadedChecksumResponse;
import static org.artifactory.util.UploadServiceUtils.setFileInfoChecksums;
import static org.artifactory.util.UploadServiceUtils.validateCanAnnotate;
import static org.artifactory.util.UploadServiceUtils.validateProperties;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.REPOSITORY_KEY;

/**
 * Handles upload of a single item. The item can be file, directory, properties, checksum data etc.
 * This service validates the request and delegates the actual upload to the repo and repo service.
 * For a local repository of a registered plugin package type, the type's upload handler may first accept, reject
 * or rewrite the upload (see {@link #routeByPluginPackageType}).
 *
 * @author Yoav Landman
 */
@Slf4j
@Service
public class UploadServiceImpl implements UploadService, GracefulShutdownAware {

    private AuthorizationService authService;
    private SecurityService securityService;
    private InternalRepositoryService repoService;
    private PropertiesService propertiesService;
    private StatsService statsService;
    private StorageChecksumReplicationService checksumReplicationService;
    private ArtifactoryBinaryService binaryService;
    private BasicAuthenticationEntryPoint authenticationEntryPoint;
    private AddonsManager addonsManager;
    private TrafficService trafficService;
    private CommitContextPropertiesProvider commitContextPropertiesProvider;
    private ChecksumDeployPermissionValidator checksumDeployPermissionValidator;
    private final SuccessfulDeploymentResponseHelper successfulDeploymentResponseHelper;
    private Function<InputStream, CountingInputStream> streamFactory;
    private final DefaultArtifactVersion versionComparator;
    private final Cache<String, Object> activeUploadTracker;
    private final Set<String> deployFallbackRepos;
    private final boolean allowDeployFallback;
    private RepoStatsService repoStatsService;
    private UnifiedEventService unifiedEventService;
    private PluginPackageTypeResolver pluginPackageTypeResolver;

    public UploadServiceImpl() {
        successfulDeploymentResponseHelper = new SuccessfulDeploymentResponseHelper();
        streamFactory = CountingInputStream::new;
        versionComparator = new DefaultArtifactVersion("2.27.0");
        activeUploadTracker = GracefulShutdownAware.createTrackingCache();
        deployFallbackRepos = Set.copyOf(ConstantValues.deployFallbackOnUpload.getList());
        allowDeployFallback = !deployFallbackRepos.isEmpty();
    }

    @Override
    public void upload(ArtifactoryRequest request, ArtifactoryResponse response) throws IOException {
        try {
            TrafficContextThreadLocal.bind(request.getRepoKey());
            track(this::processUpload, request, response, activeUploadTracker, log);
        } finally {
            TrafficContextThreadLocal.unbind();
        }
    }

    @Override
    public boolean canBeShutdown() {
        return noActiveTracking(activeUploadTracker);
    }

    @VisibleForTesting
    void processUpload(ArtifactoryRequest request, ArtifactoryResponse response) throws IOException {
        log.debug("Request: {}", request);
        addonsManager.interceptResponse(response, request, true);
        if (responseWasIntercepted(response)) {
            return;
        }
        validateRequestAndUpload(request, response);
    }

    private void validateRequestAndUpload(ArtifactoryRequest request, ArtifactoryResponse response)
            throws IOException {
        if (isRequestedRepoKeyInvalid(request)) {
            response.sendError(SC_NOT_FOUND, "No target local repository specified in deploy request.", log);
            return;
        }
        if (addonsManager.isUploadRequestBlocked(request, response)) {
            return;
        }
        if (isInternalMetadataDeploy(request)) {
            response.sendError(SC_NOT_FOUND, "Internal metadata request blocked", log);
            return;
        }

        PluginsAddon pluginAddon = addonsManager.addonByType(PluginsAddon.class);
        UploadCtx uploadCtx = new UploadCtx();
        log.debug("Executing any BeforeUploadRequest user plugins that may exist");
        sendUnifiedEvent(request, uploadCtx);
        appendHeaders(uploadCtx, response);
        pluginAddon.execPluginActions(BeforeUploadRequestAction.class, uploadCtx, request, request.getRepoPath());

        if (uploadCtx.getModifiedRepoPath() != null) {
            log.debug("BeforeUploadRequest user plugins provided a modified repo path: {}",
                    uploadCtx.getModifiedRepoPath().getId());
            request = new TranslatedArtifactoryRequest(uploadCtx.getModifiedRepoPath(), request);
        }

        LocalRepo<?, ?> targetRepository = getTargetRepository(request, repoService);
        if (isTargetRepositoryInvalid(targetRepository)) {
            sendInvalidTargetRepositoryError(request, response);
            return;
        }

        if (NamingUtils.isProperties(request.getPath())) {
            validateAndUploadProperties(request, response, targetRepository);
            return;
        }

        if (NamingUtils.isStatistics(request.getPath())) {
            validateAndUploadStatistics(request, response, targetRepository);
            return;
        }

        if (checksumDeployPermissionValidator.isChecksumDeployRequest(request) &&
                !checksumDeployPermissionValidator.hasReadPermission(request, response)) {
            return;
        }

        Optional<ArtifactoryRequest> routed = routeByPluginPackageType(request, response, targetRepository);
        if (routed.isEmpty()) {
            return;
        }
        request = routed.get();

        RepoPath repoPath = InternalRepoPathFactory.create(targetRepository.getKey(), request.getPath(),
                request.getRepoPath().isFolder());
        try {
            long contentLength = request.getContentLength();
            repoService
                    .assertValidDeployPathAndPermissions(new ValidDeployPathContext.Builder(targetRepository, repoPath)
                            .contentLength(contentLength).requestSha1(getSha1Checksum(request))
                            .requestSha2(getSha256Checksum(request)).build());
        } catch (RepoRejectException e) {
            handleInvalidDeployPathError(request, response, repoPath, e);
            return;
        }

        adjustResponseAndUpload(request, response, targetRepository);
    }

    /**
     * Applies the upload handler of the target repository's plugin package type (requirement R4). Folder creation
     * and repositories without a registered plugin type pass through unchanged. {@code Accept} keeps the request,
     * {@code Rewrite} to a path in the same repository returns a request translated to that path, and
     * {@code Reject} (or a rewrite into another repository, refused with 400) sends the error and returns empty so
     * nothing is stored. A handler that throws is logged with type and plugin name and the upload proceeds.
     *
     * @return the request to continue with, or empty when the response has already been sent
     */
    @VisibleForTesting
    Optional<ArtifactoryRequest> routeByPluginPackageType(ArtifactoryRequest request, ArtifactoryResponse response,
            LocalRepo<?, ?> targetRepository) throws IOException {
        if (pluginPackageTypeResolver == null || request.getRepoPath().isFolder()) {
            return Optional.of(request);
        }
        Optional<PluginPackageType> type = pluginPackageTypeResolver.routableTypeOf(targetRepository);
        if (type.isEmpty() || type.get().upload().isEmpty()) {
            return Optional.of(request);
        }
        RepoPath requested = InternalRepoPathFactory.create(targetRepository.getKey(), request.getPath());
        PluginTypeHandlers.UploadOutcome outcome = PluginTypeHandlers.decideUpload(type.get(), request, requested);
        if (outcome instanceof PluginTypeHandlers.UploadOutcome.Failed failed) {
            log.error("Upload handler of plugin package type '{}' (plugin '{}') failed for {}; " +
                            "storing the upload unchanged: {}", type.get().name(), type.get().pluginName(),
                    requested, failed.error().getMessage(), failed.error());
            return Optional.of(request);
        }
        PluginTypeHandlers.UploadRoute route = PluginTypeHandlers.route(
                ((PluginTypeHandlers.UploadOutcome.Decided) outcome).decision(), requested);
        if (route instanceof PluginTypeHandlers.UploadRoute.Refuse refuse) {
            response.sendError(refuse.status(), refuse.message(), log);
            return Optional.empty();
        }
        RepoPath target = ((PluginTypeHandlers.UploadRoute.Store) route).path();
        if (target.equals(requested)) {
            return Optional.of(request);
        }
        log.debug("Plugin package type '{}' rewrote upload {} to {}", type.get().name(), requested, target);
        return Optional.of(new TranslatedArtifactoryRequest(target, request));
    }

    private static boolean isInternalMetadataDeploy(ArtifactoryRequest request) {
        return (request instanceof HttpArtifactoryRequest || request instanceof ArtifactoryDeployRequest) &&
                InternalMetadataChecker.isInternalMetadata(request.getPath());
    }

    @VisibleForTesting
    void sendUnifiedEvent(ArtifactoryRequest request, UploadCtx uploadCtx) {

        log.debug("Sending BeforeUpload event via unifiedEventService for {}", request);
        DispatcherResponse response = unifiedEventService
                .sendArtifactEvent(SupportedUnifiedEvents.BEFORE_UPLOAD, request, null);
        log.debug("Received BeforeUpload response from unifiedEventService: {}", response);
        if (response.data() != null) {
            uploadCtx.setHeaders(response.data().headers());
            if (response.data().modifiedRepoPath() != null) {
                uploadCtx.setModifiedRepoPath(response.data().modifiedRepoPath());
            }
        }

    }

    @VisibleForTesting
    void appendHeaders(UploadCtx uploadCtx, ArtifactoryResponse response) {
        Map<String, String> headers = uploadCtx.getHeaders();
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                response.setHeader(entry.getKey(), entry.getValue());
            }
        }
    }

    private void sendInvalidTargetRepositoryError(ArtifactoryRequest request, ArtifactoryResponse response)
            throws IOException {
        int responseStatus;
        String responseMessage;
        String repoKey = request.getRepoKey();
        if (isVirtualRepoKey(repoKey, repoService)) {
            response.setHeader("Allow", "GET");
            responseStatus = HttpStatus.SC_METHOD_NOT_ALLOWED;
            responseMessage = "No local repository was configured as local deployment " +
                    "repository for the (" + repoKey + ") virtual repository.";
        } else {
            responseStatus = SC_NOT_FOUND;
            responseMessage = "Could not find a local repository named " + repoKey + " to deploy to.";
        }
        response.sendError(responseStatus, responseMessage, log);
    }

    private void adjustResponseAndUpload(ArtifactoryRequest request, ArtifactoryResponse response,
            LocalRepo<?, ?> targetRepository) throws IOException {
        if (processOriginatedExternally(response)) {
            response = new DelayedHttpResponse((HttpArtifactoryResponse) response);
        }
        try {
            if (request.getRepoPath().isFolder()) {
                createDirectory(request, response);
            } else if (request.isChecksum()) {
                validateAndUploadChecksum(request, response, targetRepository);
            } else if (NamingUtils.isMetadata(request.getPath())) {
                response.sendError(SC_CONFLICT, "Old metadata notation is not supported anymore: " +
                        request.getRepoPath(), log);
            } else {
                uploadArtifact(request, response, targetRepository);
            }
        } catch (RepoRejectException | BinaryRejectedException | BinaryStorageException e) {
            log.debug("", e);
            //Catch rejections on save
            response.sendError(e.getErrorCode(), e.getMessage(), log);
            return;
        }
        commitResponseIfDelayed(response);
    }

    private void handleInvalidDeployPathError(ArtifactoryRequest request, ArtifactoryResponse response,
            RepoPath repoPath, RepoRejectException rejectionException) throws IOException {
        if (rejectionSignifiesRequiredAuthorization(rejectionException, securityService)) {
            consumeRequestBody(request);
            String realmName = authenticationEntryPoint.getRealmName();
            response.sendAuthorizationRequired(rejectionException.getMessage(), realmName);
        } else {
            if (handleDeployFallbackIfEnabled(request, response, repoPath, rejectionException.getErrorCode())) {
                return;
            }
            response.sendError(rejectionException.getErrorCode(), rejectionException.getMessage(), log);
        }
    }

    /**
     * In extreme rare case, where network issues that are not caught by the application,
     * completed deployments that already returned 201 by Rt failed afterward, making the clients retry.
     * If the user does not have a delete permissions, he will get 403 on the same item he just uploaded successfully
     * (without knowing). @see RTDEV-36252
     * <p>
     * The new fallback will return 201 if:
     * <ul>
     *  <li> Response status was FORBIDDEN (403) </li>
     *  <li> Request was uploaded to a supported repo </li>
     *  <li> Original file was uploaded by the same user that is trying to upload </li>
     *  <li> Original file was uploaded within the threshold time </li>
     *  <li> The new request checksum was matches the original file, either by header or by consuming the stream</li>
     * </ul>
     */
    @VisibleForTesting
    boolean handleDeployFallbackIfEnabled(ArtifactoryRequest request, ArtifactoryResponse response, RepoPath repoPath,
            int statusCode) {
        if (shouldEvaluateFallback(statusCode, repoPath.getRepoKey())) {
            try {
                FileInfo fileInfo = repoService.getFileInfo(repoPath);
                if (securityService.currentUsername().equals(fileInfo.getCreatedBy()) &&
                        deployedWithinThreshold(fileInfo.getCreated())) {
                    log.debug("Verifying checksums before skipping deploy for {}", repoPath);
                    if (verifyChecksumMatchForDeploy(request, fileInfo)) {
                        markSkipped(response, repoPath);
                        return true;
                    }
                }
            } catch (Exception e) {
                log.debug("Unable to verify file info for {}", repoPath, e);
            }
        }
        return false;
    }

    private static boolean deployedWithinThreshold(long artifactCreated) {
        long thresholdMs = ConstantValues.deployFallbackOnUploadThresholdMs.getLong();
        return (System.currentTimeMillis() - artifactCreated) < thresholdMs;
    }


    private static void markSkipped(ArtifactoryResponse response, RepoPath repoPath) throws IOException {
        log.info("Skipped due to fallback flag for {}", repoPath);
        response.setStatus(SC_CREATED);
        response.sendStream(IOUtils.toInputStream("SKIPPED DUE TO FALLBACK FLAG", Charset.defaultCharset()));
    }

    /**
     * Check that the fallback flow is enabled globally, the response was 403,
     * and that the fallback is supported for the given repo
     */
    private boolean shouldEvaluateFallback(int statusCode, String repoKey) {
        return (allowDeployFallback && statusCode == HttpStatus.SC_FORBIDDEN && deployFallbackRepos.contains(repoKey));
    }

    private static boolean verifyChecksumMatchForDeploy(ArtifactoryRequest request, FileInfo fileInfo)
            throws IOException {
        String sha1FromHeader = request.getHeader(ArtifactoryHeaders.CHECKSUM_SHA1);
        if (StringUtils.isNotBlank(sha1FromHeader)) {
            return fileInfo.getSha1().equals(sha1FromHeader);
        }
        ChecksumInputStream input = new ChecksumInputStream(request.getInputStream(), Checksum.sha1());
        try (input) {
            IOUtils.consume(input);
        }
        Checksum checksums = input.getChecksums()[0];
        return checksums.getChecksum().equals(fileInfo.getSha1());
    }

    void createDirectory(ArtifactoryRequest request, ArtifactoryResponse response)
            throws IOException, RepoRejectException {
        RepoPath repoPath = request.getRepoPath();
        log.info("MKDir request to '{}'", request.getRepoPath());

        boolean canAnnotate = validateCanAnnotate(repoPath, authService, securityService);
        if (canAnnotate) {
            validateRequestProperties(request);
        }
        repoService.mkdirs(repoPath);

        Properties properties = request.getProperties();
        if (properties != null && !properties.isEmpty() && canAnnotate) {
            propertiesService.setProperties(repoPath, properties, false);
        }
        sendSuccessfulResponse(request, response, repoPath, true);
        log.info("Successfully created directory '{}'", request.getRepoPath());
    }

    void validateRequestProperties(ArtifactoryRequest request) throws RepoRejectException {
        Properties properties = request.getProperties();
        if (properties != null && !properties.isEmpty()) {
            validateProperties(properties);
        }
    }

    private void validateAndUploadChecksum(ArtifactoryRequest request, ArtifactoryResponse response,
            LocalRepo<?, ?> repo) throws IOException {
        long length = request.getContentLength();
        if (isAbnormalChecksumContentLength(length)) {
            // something is fishy, checksum file should not be so big...
            response.sendError(SC_CONFLICT, "Suspicious checksum file, content length of " + length +
                    " bytes is bigger than allowed.", log);
            return;
        }
        boolean artificial = request.getInputStream() instanceof ArtificialInputStream;
        log.debug("Deploy to '{}' Content-Length: {} artificial: {}",
                request.getRepoPath(), length < 0 ? "unspecified" : length, artificial);
        String checksumPath = request.getPath();
        if (NamingUtils.isMetadataChecksum(checksumPath) || MavenNaming.isMavenMetadataChecksum(checksumPath)) {
            //Ignore request - we maintain our self-calculated checksums for metadata
            consumeContentAndRespondWithSuccess(request, response);
            return;
        }
        validatePathAndUploadChecksum(request, response, repo);
    }

    /**
     * @see
     * <a href="http://wiki.jfrog.org/confluence/display/RTF30/Artifactory%27s+REST+API#Artifactory'sRESTAPI-SetItemProperties">Set Item Properties REST API</a>
     * @deprecated can use the set item properties REST API instead, see
     */
    @Deprecated(forRemoval = false)
    private void validateAndUploadProperties(ArtifactoryRequest request, ArtifactoryResponse response,
            LocalRepo<?, ?> repo) throws IOException {
        //TORE: [by YS] this doesn't belong here
        long length = request.getContentLength();
        if (isAbnormalPropertiesContentLength(length)) {
            // something is fishy, properties file should not be so big...
            response.sendError(SC_CONFLICT, "Properties content length of " + length +
                    " bytes is bigger than allowed.", log);
            return;
        }
        log.debug("Deploy properties to '{}' Content-Length: {}", request.getRepoPath(),
                length < 0 ? "unspecified" : length);
        String path = request.getPath();
        if (isMavenRepo(repo)) {
            path = adjustMavenSnapshotPath(repo, request);
        }
        RepoPathImpl itemRepoPath = new RepoPathImpl(request.getRepoKey(), NamingUtils.stripMetadataFromPath(path));
        try {
            String propertiesStr = IOUtils.toString(request.getInputStream());
            PropertiesXmlProvider propertiesXmlProvider = new PropertiesXmlProvider();
            MutablePropertiesInfo propertiesInfo = propertiesXmlProvider.fromXml(propertiesStr);
            Properties properties = new PropertiesImpl(propertiesInfo);
            boolean success = propertiesService.setProperties(itemRepoPath, properties, false);
            if (success) {
                response.setStatus(SC_CREATED);
                // in case of empty response body we cant get target IP for the traffic logging
                response.sendStream(IOUtils.toInputStream("OK"));
            } else {
                response.sendError(SC_NOT_FOUND, "Failed to set properties on " + itemRepoPath, log);
            }
        } catch (Exception e) {
            log.debug("Failed to deploy properties to '" + itemRepoPath + "'", e);
            response.sendError(SC_CONFLICT, "Failed to deploy properties : " + e.getMessage() +
                    " on path " + request.getRepoPath(), log);
        }
    }

    private void validateAndUploadStatistics(ArtifactoryRequest request, ArtifactoryResponse response,
            LocalRepo<?, ?> repo) throws IOException {
        long length = request.getContentLength();
        if (isAbnormalStatisticsContentLength(length)) {
            // something is fishy, statistics file should not be so big...
            response.sendError(SC_CONFLICT, "Statistics content length of " + length +
                    " bytes is bigger than allowed.", log);
            return;
        }
        log.info("Deploy statistics to '{}' Content-Length: {}", request.getRepoPath(),
                length < 0 ? "unspecified" : length);
        String path = request.getPath();
        if (isMavenRepo(repo)) {
            path = adjustMavenSnapshotPath(repo, request);
        }
        RepoPathImpl itemRepoPath = new RepoPathImpl(request.getRepoKey(), NamingUtils.stripMetadataFromPath(path));
        try {
            String statisticsStr = IOUtils.toString(request.getInputStream(), Charset.defaultCharset());
            StatsInfoXmlProvider statsInfoXmlProvider = new StatsInfoXmlProvider();
            StatsInfo statsInfo = statsInfoXmlProvider.fromXml(statisticsStr);
            boolean success = statsService.setStats(itemRepoPath, statsInfo);
            if (success) {
                response.setStatus(SC_CREATED);
            } else {
                response.sendError(SC_NOT_FOUND, "Failed to set statistics on " + itemRepoPath, log);
            }
        } catch (Exception e) {
            log.debug("Failed to deploy statistics to '" + itemRepoPath + "'", e);
            response.sendError(SC_CONFLICT, "Failed to deploy statistics : " + e.getMessage() +
                    " on path " + request.getRepoPath(), log);
        }
    }

    private void validatePathAndUploadChecksum(ArtifactoryRequest request, ArtifactoryResponse response,
            LocalRepo<?, ?> repo) throws IOException {
        String uploadedChecksum;
        try {
            uploadedChecksum = getChecksumContentAsString(request);
        } catch (IOException e) {
            response.sendError(SC_CONFLICT, "Failed to read checksum from file: " + e.getMessage() +
                    " for path " + request.getRepoPath(), log);
            return;
        }
        ChecksumType checksumType = ChecksumType.forFilePath(request.getPath());
        RepoPath targetFileRepoPath = adjustAndGetChecksumTargetRepoPath(request, repo);
        try {
            ChecksumInfo checksumInfo = repoService
                    .setClientChecksum(repo, checksumType, targetFileRepoPath, uploadedChecksum);
            if (isChecksumValidAccordingToPolicy(uploadedChecksum, checksumInfo)) {
                sendUploadedChecksumResponse(request, response, targetFileRepoPath);
            } else {
                String message = String.format("Checksum error for '%s': received '%s' but actual is '%s'",
                        request.getPath(), uploadedChecksum, checksumInfo.getActual());
                sendInvalidUploadedChecksumResponse(request, response, repo, targetFileRepoPath, message);
            }
        } catch (ItemNotFoundRuntimeException e) {
            response.sendError(SC_NOT_FOUND, "Target file to set checksum on doesn't exist: " + targetFileRepoPath,
                    log);
        } catch (FileExpectedException e) {
            response.sendError(SC_CONFLICT,
                    "Checksum only supported for files (but found folder): " + targetFileRepoPath, log);
        }
    }

    private void uploadArtifact(ArtifactoryRequest request, ArtifactoryResponse response, LocalRepo<?, ?> repo)
            throws IOException, RepoRejectException {
        if (isDeployArchiveBundle(request)) {
            RestCoreAddon restCoreAddon = addonsManager.addonByType(RestCoreAddon.class);
            restCoreAddon.deployArchiveBundle(request, response, repo);
            return;
        }
        boolean artificial = request.getInputStream() instanceof ArtificialInputStream;
        long length =
                request.getContentLength() == -1 ? request.getInputStream().available() : request.getContentLength();
        log.debug("Deploy to '{}' Content-Length: {} (estimation) artificial: {}", request.getRepoPath(),
                length < 0 ? "unspecified" : length, artificial);
        uploadFile(request, response, repo);
    }

    private void uploadFile(ArtifactoryRequest request, ArtifactoryResponse response, LocalRepo<?, ?> repo)
            throws RepoRejectException, IOException {
        String path = request.getPath();
        if (isMavenRepo(repo)) {
            if (isRepoSnapshotPolicyNotDeployer(repo) && MavenNaming.isSnapshotMavenMetadata(path)) {
                // Skip the maven metadata deployment - use the metadata calculated after the pom is deployed
                consumeContentAndRespondAccepted(request, response);
                return;
            }
            if (!(repo instanceof DbCacheRepo)) {
                path = adjustMavenSnapshotPath(repo, request);
            }
        }
        RepoPath fileRepoPath = repo.getRepoPath(path);
        MutableFileInfo fileInfo = InfoFactoryHolder.get().createFileInfo(fileRepoPath);
        boolean isChecksumDeploy = isChecksumDeploy(request);
        setFileInfoChecksums(request, fileInfo, isChecksumDeploy);
        FileResource fileResource = new FileResource(fileInfo);
        uploadItem(request, response, repo, fileResource);
        if (ConstantValues.repoStatsEnabled.getBoolean()) {
            repoStatsService.update(repo.getKey(),
                    RepoStatDto.RepoStatUpdateType.UPLOAD, request.getRepoKey());
        }
    }

    private void uploadItem(ArtifactoryRequest request, ArtifactoryResponse response, LocalRepo<?, ?> repo,
            RepoResource res)
            throws IOException, RepoRejectException {
        if (isChecksumDeploy(request)) {
            uploadItemWithReusedContent(request, response, repo, res);
        } else if (ConstantValues.httpUseExpectContinue.getBoolean() && HttpUtils.isExpectedContinue(request)) {
            uploadItemWithReusedOrProvidedContent(request, response, repo, res);
        } else {
            uploadItemWithProvidedContent(request, response, repo, res);
        }
    }

    private void uploadItemWithReusedContent(ArtifactoryRequest request, ArtifactoryResponse response,
            LocalRepo<?, ?> repo, RepoResource res) throws IOException, RepoRejectException {
        String sha1 = getSha1Checksum(request);
        String sha2 = getSha256Checksum(request);
        if (isBlank(sha1) && isBlank(sha2)) {
            response.sendError(SC_BAD_REQUEST, "Checksum deploy failed. no checksum header '" +
                    ArtifactoryHeaders.CHECKSUM_SHA1 + "/" + ArtifactoryHeaders.CHECKSUM_SHA256 + "' was found.", log);
            return;
        }
        // At this point we verified we have one of them. Prefer sha2 deployment.
        // real usage of which sha to use moved to {BinaryServiceImpl#getBinaryForChecksumDeploy}
        if (StringUtils.isNotBlank(sha2)) {
            doChecksumDeploy(request, response, repo, res, ChecksumType.sha256, sha2);
        } else {
            doChecksumDeploy(request, response, repo, res, ChecksumType.sha1, sha1);
        }
    }

    void doChecksumDeploy(ArtifactoryRequest request, ArtifactoryResponse response, LocalRepo<?, ?> repo,
            RepoResource res, ChecksumType checksumType, String checksum) throws IOException, RepoRejectException {
        if (log.isDebugEnabled()) {
            log.debug("Checksum deploy to '{}' with {}: {}", res.getRepoPath(), checksumType.alg(), checksum);
        }
        if (!checksumType.isValid(checksum)) {
            response.sendError(SC_NOT_FOUND, "Checksum deploy failed. Invalid " + checksumType.alg() + ": " + checksum,
                    log);
            return;
        }
        InputStream inputStream = null;
        try {
            inputStream = getStreamForChecksumDeploy(request, repo);
            log.debug("Found file with checksum: {}, executing checksum deploy", getSha1Checksum(request));
            uploadItemWithContent(request, response, repo, res, inputStream);
            response.checksumDeployed();
        } catch (BinaryNotFoundException e) {
            response.sendError(SC_NOT_FOUND, "Checksum deploy failed. " + e.getMessage(), log);
        } finally {
            IOUtils.closeQuietly(inputStream, null);
        }
    }

    private InputStream getStreamForChecksumDeploy(ArtifactoryRequest request, LocalRepo repo) {
        Map<String, String> headers = Maps.newHashMap();
        headers.put(REPOSITORY_KEY.getHeaderName(), repo.getKey());
        //check if client requested using CHECK_BINARY_EXISTENCE_IN_FILESTORE flag
        boolean checkInFilestoreRequested = isCheckBinaryExistenceInFilestore(request);
        boolean checkInFilestoreEntitled = addonsManager.isEntitled(EntitlementType.CHECKSUM_BASED_STORAGE_REPLICATION);
        if (checkInFilestoreRequested && !checkInFilestoreEntitled) {
            log.warn("Attempt to retrieve binary from binary store failed. This operation require enterprise plus " +
                    "license.");
        }
        //check if allow to trust client: both flags from configService should be true && replicationConfig should allow
        boolean checkInFilestore = false;
        boolean shouldTrustClientChecksums = false;
        if (checkInFilestoreRequested && checkInFilestoreEntitled) {
            //check in DB if requested and entitled
            StorageChecksumReplicationConfig replicationConfig = checksumReplicationService.getConfig();
            if (replicationConfig != null) {
                checkInFilestore = replicationConfig.isAllowed();
                shouldTrustClientChecksums = replicationConfig.canTrustClientChecksums();
            }
        }

        log.debug("checkInFilestoreRequested={} checkInFilestoreEntitled={} checkInFilestore={} " +
                        "shouldTrustClientChecksums={}", checkInFilestoreRequested, checkInFilestoreEntitled,
                checkInFilestore, shouldTrustClientChecksums);

        BinaryElement element = binaryService.createBinaryElement(
                getSha1Checksum(request),
                getSha256Checksum(request),
                //passing md5 and contentLength only if checkInFilestore is true meaning we will trust
                //the client information completely without trying to read the binary from the store
                //(performance reasons during migration)
                shouldTrustClientChecksums ? getMd5Checksum(request) : null,
                shouldTrustClientChecksums ? getBinarySize(request) : -1L,
                headers);
        return binaryService.getBinaryForChecksumDeploy(element, checkInFilestore);
    }

    private void uploadItemWithReusedOrProvidedContent(ArtifactoryRequest request, ArtifactoryResponse response,
            LocalRepo<?, ?> repo, RepoResource res) throws IOException, RepoRejectException {
        log.debug("Client '{}' supports Expect 100/continue", request.getHeader(HttpHeaders.USER_AGENT));
        String found = getValidChecksumFromRequest(request, res);
        if (found != null) {
            Map<String, String> headers = Maps.newHashMap();
            headers.put(REPOSITORY_KEY.getHeaderName(), res.getRepoPath().getRepoKey());
            try (InputStream inputStream = binaryService.getBinary(found, headers)) {
                uploadItemWithContent(request, response, repo, res, inputStream);
                return;
            } catch (BinaryNotFoundException e) {
                log.warn("Could not get original stream with {}: {}. Using content provided by request", found,
                        e.getMessage());
            }
        }
        //no such binary as specified by header, continue normal deployment with incoming stream
        uploadItemWithProvidedContent(request, response, repo, res);
    }

    /**
     * Validates the checksum passed is valid and transforms sha1 to sha2 in case X-SHA256 header was passed.
     */
    private String getValidChecksumFromRequest(ArtifactoryRequest request, RepoResource res) {
        String found = null;
        String sha1 = getSha1Checksum(request);
        String sha2 = getSha256Checksum(request);
        // Allow uploads with either X-SHA1 or X-SHA256 headers
        if (StringUtils.isNotBlank(sha1) && ChecksumType.sha1.isValid(sha1)) {
            log.debug("Expect continue deploy to '{}' with SHA1: {}", res.getRepoPath(), sha1);
            found = sha1;
        } else if (StringUtils.isNotBlank(sha2) && ChecksumType.sha256.isValid(sha2)) {
            //[sha2]: change back to sha2-compatible method when binarystore supports retrieve by sha2
            log.debug("Expect continue deploy to '{}' with SHA256: {}", res.getRepoPath(), sha2);
            try {
                BinaryElement binary = binaryService.findBinary(ChecksumType.sha256, sha2);
                if (binary != null) {
                    found = binary.getSha1();
                } else {
                    log.warn("Cannot find binary with SHA256 '{}'", sha2);
                }
            } catch (Exception e) {
                log.warn("Cannot find binary with SHA256 '{}' : {}", sha2, e.getMessage());
                log.debug("", e);
            }
        }
        return found;
    }

    void uploadItemWithProvidedContent(ArtifactoryRequest request, ArtifactoryResponse response, LocalRepo<?, ?> repo,
            RepoResource res) throws IOException, RepoRejectException {
        InputStream inputStream = getInputStream(request, streamFactory);
        long remoteUploadStartTime = System.currentTimeMillis();
        boolean shouldLogTrafficUponFailure = !(inputStream instanceof ArtificialInputStream);
        try {
            uploadItemWithContent(request, response, repo, res, inputStream);
            if (!shouldLogTrafficUponFailure && isSuccess(response)) {
                shouldLogTrafficUponFailure = true;
            }
            if (shouldLogTraffic(request) && shouldLogTrafficUponFailure) {
                fireUploadTrafficEvent(res, remoteUploadStartTime, trafficService);
            }
        } finally {
            if (shouldLogTrafficUponFailure) {
                fireUploadTrafficEventV2(request, res, inputStream, trafficService);
            }
        }
    }

    void uploadItemWithContent(ArtifactoryRequest request, ArtifactoryResponse response, LocalRepo<?, ?> repo,
            RepoResource res, InputStream inputStream)
            throws RepoRejectException, BinaryRejectedException, IOException {
        // Update the last modified
        long lastModified = request.getLastModified() > 0 ? request.getLastModified() : System.currentTimeMillis();
        ((MutableRepoResourceInfo) res.getInfo()).setLastModified(lastModified);
        RepoPath repoPath = res.getRepoPath();
        Properties properties =
                populateAndValidateItemPropertiesFromRequest(request, inputStream, repoPath, authService,
                        securityService);
        if (ConstantValues.applicationServiceFeatureEnabled.getBoolean()) {
            properties = addAppTrustDataToProperties(repo, repoPath, properties);
        }
        SaveResourceContext.Builder contextBuilder = new SaveResourceContext.Builder(res, inputStream);
        if (properties != null && !properties.isEmpty()) {
            contextBuilder.properties(properties);
        }
        populateItemInfoFromHeaders(request, res, contextBuilder, securityService);
        populateContentLength(request, contextBuilder);
        try {
            RepoResource resource = repoService.saveResource(repo, contextBuilder.build());
            if (!resource.isFound()) {
                response.sendError(SC_NOT_FOUND, ((UnfoundRepoResource) resource).getDetail(), log);
                return;
            }
            repoPath = alterPathIfNeeded(repoPath, resource);
            sendSuccessfulResponse(request, response, repoPath, false);
            notifyOnSuccessfulPackageUpload(repo, request, repoPath);
        } catch (BadPomException bpe) {
            response.sendError(SC_CONFLICT, bpe.getMessage(), log);
        }
    }

    private Properties addAppTrustDataToProperties(LocalRepo<?, ?> repo, RepoPath repoPath, Properties properties) {
        if (isLeadArtifact(repo, repoPath)) {
            Authentication authentication = AuthenticationHelper.getAuthentication();
            if (authentication instanceof AccessTokenAuthentication accessTokenAuthentication) {
                if (properties == null) {
                    properties = (Properties) InfoFactoryHolder.get().createProperties();
                }
                addApplicationIdProperty(repoPath, properties, accessTokenAuthentication);
                addCommitContextProperties(properties, accessTokenAuthentication);
            } else {
                log.debug("Authentication type is '{}'. No AppTrust properties will be added.",
                        authentication == null ? "null" : authentication.getClass().getName());
            }
        } else {
            log.debug("Artifact {} is not Lead. No AppTrust properties will be added", repoPath.toPath());
        }
        return properties;
    }

    private boolean isLeadArtifact(LocalRepo<?, ?> repo, RepoPath repoPath) {
        RepoConfig repoConfig = repo.getRepoConfig();
        if (repoConfig == null) {
            log.trace("Repo config is null for artifact {}", repoPath.toPath());
            return false;
        } else {
            RepoType repoType = repoConfig.getPackageType();
            log.trace("Repo type is {} for artifact {}", repoType, repoPath.toPath());
            LeadArtifactDetectorAddon leadArtifactDetectorAddon =
                    addonsManager.addonByType(LeadArtifactDetectorAddon.class);
            return leadArtifactDetectorAddon.isLeadArtifact(repoType, repoPath);
        }
    }

    private void addApplicationIdProperty(RepoPath repoPath, Properties properties,
            AccessTokenAuthentication authentication) {
        List<String> scopesFromToken = authentication.getAccessToken().getScope();
        Optional<String> applicationId = ScopeContainer.extractApplicationKeyFromScope(scopesFromToken);
        if (applicationId.isPresent()) {
            properties.put("application.id", applicationId.get());
            log.info("Added application id '{}' to the lead artifact '{}' properties", applicationId,
                    repoPath.toPath());
        } else {
            log.debug("No application id found for lead artifact '{}'", repoPath.toPath());
        }
    }

    private void addCommitContextProperties(Properties properties, AccessTokenAuthentication authentication) {
        if (authentication.getAccessToken() == null) {
            log.debug("Commit context props will not be added. Auth token is not JwtAccessToken.");
            return;
        }
        var commitContextProps = commitContextPropertiesProvider.retrieveCommitContextProperties(
                authentication.getAccessToken());
        properties.putAll(commitContextProps);
        log.debug("Added following commit context properties to the lead artifact: {}", commitContextProps);
    }

    @VisibleForTesting
    void notifyOnSuccessfulPackageUpload(LocalRepo<?, ?> repo, ArtifactoryRequest request, RepoPath repoPath) {
        RepoConfig repoConfig = repo.getRepoConfig();
        if (repoConfig == null) {
            return;
        }
        RepoType repoType = repoConfig.getPackageType();
        if (repoType.isMavenGroup()) {
            addonsManager.addonByType(OnBoardingAddon.class)
                    .userUploadedPackage(repoType, repo.getKey(), repoPath.getName(), "");
        }
        if (!request.shouldSkipPackageUsageTrack() &&
                (addonsManager.addonByType(LeadArtifactDetectorAddon.class)
                        .isPackageConsumptionLead(repoType, repoPath))) {
            addonsManager.addonByType(AnalyticsAddon.class).trackSuccessfulPackageUpload(repoType);
        }
    }

    /**
     * Returns true if the artifact was not uploaded to this Artifactory by its own replicator
     */
    private boolean shouldLogTraffic(ArtifactoryRequest request) {
        String userAgent = HttpUtils.getRequestUserAgent(request);
        // In case the userAgent is 'jfrog-replicator' then the upload is internal from a replicator to its Artifactory
        return isBlank(userAgent) || !startsWith(userAgent, "jfrog-replicator/");
    }

    /**
     * Setting alternate response repoPath in case artifact was moved during the upload process.
     * For example due to storage interceptors moving the artifact after deploy.
     * <p>
     * {@link RepoResource#getResponseRepoPath()} holds a different path in case file was moved.
     */
    private RepoPath alterPathIfNeeded(RepoPath repoPath, RepoResource resource) {
        if (resource.getResponseRepoPath() != null) {
            repoPath = resource.getResponseRepoPath();
        }
        return repoPath;
    }

    void sendSuccessfulResponse(ArtifactoryRequest request, ArtifactoryResponse response, RepoPath repoPath,
            boolean isDirectory) throws IOException {
        String url = buildArtifactUrl(request, repoPath);
        successfulDeploymentResponseHelper.writeSuccessfulDeploymentResponse(repoService, response, repoPath, url,
                isDirectory, includeSha256Value(request));
    }

    @VisibleForTesting
    boolean includeSha256Value(ArtifactoryRequest request) {
        String userAgent = StreamSupportUtils.enumerationToStream(request.getHeaderValues(HttpHeaders.USER_AGENT))
                .findFirst()
                .orElse(null);
        String buildInfoAgent = "ArtifactoryBuildClient/";
        return isBlank(userAgent) || !userAgent.contains(buildInfoAgent) ||
                new DefaultArtifactVersion(StringUtils.substringAfter(userAgent, buildInfoAgent))
                        .compareTo(versionComparator) >= 0;
    }

    @Autowired
    public void setPluginPackageTypeResolver(PluginPackageTypeResolver pluginPackageTypeResolver) {
        this.pluginPackageTypeResolver = pluginPackageTypeResolver;
    }

    @Autowired
    public void setAuthService(AuthorizationService authService) {
        this.authService = authService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setRepoService(InternalRepositoryService repoService) {
        this.repoService = repoService;
    }

    @Autowired
    public void setPropertiesService(PropertiesService propertiesService) {
        this.propertiesService = propertiesService;
    }

    @Autowired
    public void setStatsService(@Qualifier("statsServiceImpl") StatsService statsService) {
        this.statsService = statsService;
    }

    @Autowired
    public void setStorageChecksumReplicationService(StorageChecksumReplicationService checksumReplicationService) {
        this.checksumReplicationService = checksumReplicationService;
    }

    @Autowired
    public void setBinaryService(ArtifactoryBinaryService binaryService) {
        this.binaryService = binaryService;
    }

    @Autowired
    public void setAuthenticationEntryPoint(BasicAuthenticationEntryPoint authenticationEntryPoint) {
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Autowired
    public void setTrafficService(TrafficService trafficService) {
        this.trafficService = trafficService;
    }

    @Autowired
    public void setChecksumDeployPermissionValidator(
            ChecksumDeployPermissionValidator checksumDeployPermissionValidator) {
        this.checksumDeployPermissionValidator = checksumDeployPermissionValidator;
    }

    @Autowired
    public void setCommitContextPropertiesProvider(CommitContextPropertiesProvider commitContextPropertiesProvider) {
        this.commitContextPropertiesProvider = commitContextPropertiesProvider;
    }

    void setStreamFactory(Function<InputStream, CountingInputStream> streamFactory) {
        this.streamFactory = streamFactory;
    }

    @Autowired
    public void setRepoStatsService(RepoStatsService repoStatsService) {
        this.repoStatsService = repoStatsService;
    }

    @Autowired
    public void setUnifiedEventService(UnifiedEventService unifiedEventService) {
        this.unifiedEventService = unifiedEventService;
    }
}