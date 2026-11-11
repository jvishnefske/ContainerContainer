package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageCancelException;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageExpiredException;
import com.jfrog.ph.acl.exception.PackageForbiddenException;
import com.jfrog.ph.acl.exception.PackageInternalServerError;
import com.jfrog.ph.acl.exception.PackageNotFoundException;
import com.jfrog.ph.acl.exception.PackageRepoNotFoundException;
import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.model.PackageDownloadContext;
import com.jfrog.ph.acl.service.PackageArtifactService;
import com.jfrog.ph.acl.virtual.resolution.PackageRequestContext;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ConnectTimeoutException;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.request.DownloadService;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.request.JerseyArtifactoryResponse;
import org.artifactory.common.repo.PackageType;
import org.artifactory.common.resource.BaseResource;
import org.artifactory.exception.CancelException;
import org.artifactory.exception.LoadHealerException;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.HttpRepo;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.request.DownloadRequestContext;
import org.artifactory.resource.BlockedRepoResource;
import org.artifactory.resource.ExpiredRepoResource;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.rest.exception.ExpiredRestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.rest.exception.RestException;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.traffic.context.TrafficContextThreadLocal;
import org.jfrog.common.ExceptionLogUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Optional;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_REQUEST_TIMEOUT;
import static org.apache.http.HttpStatus.SC_TOO_MANY_REQUESTS;
import static org.artifactory.ph.exception.PackageResourceExceptionUtil.assertValidResource;
import static org.artifactory.repo.db.DbLocalRepo.BYPASS_UNSCANNED_BLOCKING_HEADER;

@Slf4j
public class PackageArtifactServiceImpl implements PackageArtifactService {

    private static final String REMOTE_CACHE_SUFFIX = "-cache";
    private final DownloadService downloadService;
    private final InternalRepositoryService repositoryService;

    public PackageArtifactServiceImpl(DownloadService downloadService, InternalRepositoryService repositoryService) {
        this.downloadService = downloadService;
        this.repositoryService = repositoryService;
    }

    @Override
    public Response download(PackageDownloadContext downloadContext) throws PackageException {
        InternalArtifactoryRequest request = fromPackageDownloadContext(downloadContext);
        return download(request);
    }

    @Override
    public Response downloadFromRemoteCache(PackageDownloadContext downloadContext) throws PackageException {
        String remoteRepoKey = downloadContext.getRepoKey();
        if (remoteRepoKey.endsWith(REMOTE_CACHE_SUFFIX)) {
            throw new PackageException(SC_BAD_REQUEST, "Bad request",
                    "Download request from remote-cache repo should not include '-cache' in the repo key");
        }
        LocalRepo<?, ?> cachedRepo = repositoryService.localFederatedOrCachedRepositoryByKey(remoteRepoKey);
        if (!(cachedRepo instanceof LocalCacheRepo)) {
            String error = "Repo '" + remoteRepoKey + "' does not exist or storing artifacts locally is disabled";
            log.error(error);
            throw new PackageException(SC_BAD_REQUEST, error, error);
        }
        InternalArtifactoryRequest internalArtifactoryRequest =
                fromPackageDownloadContext(downloadContext, cachedRepo.getKey());
        return download(internalArtifactoryRequest);
    }

    private Response download(InternalArtifactoryRequest request) throws PackageException {
        if (CurrentActivity.GROUP.get() == null) {
            CurrentActivity.GROUP.put("download");
        }
        try {
            log.debug("Executing download on {}/{} with alternative URL to {}", request.getRepoKey(), request.getPath(),
                    request.getAlternativeRemoteDownloadUrl());
            JerseyArtifactoryResponse response = new JerseyArtifactoryResponse();
            downloadService.process(request, response);
            return response.build();
        } catch (AuthorizationRestException e) {
            log.debug("Failed to process download from {}/{} because of AuthorizationRestException: {}",
                      request.getRepoKey(), request.getPath(), e.getMessage(), e);
            throw new PackageForbiddenException(e.getMessage(), e.getMessage(), e);
        } catch (FileExpectedException e) {
            log.debug("Failed to process download from {}/{} because of FileExpectedException: {}",
                      request.getRepoKey(), request.getPath(), e.getMessage(), e);
            throw new PackageNotFoundException(e.getMessage(), e);
        } catch (ExpiredRestException e) {
            log.debug("Failed to process download from {}/{} because of ExpiredRestException: {}", request.getRepoKey(),
                      request.getPath(), e.getMessage(), e);
            throw new PackageExpiredException(e.getMessage(), e);
        } catch (RestException e) {
            log.debug("Failed to process download from {}/{} because of RestException: {}", request.getRepoKey(),
                      request.getPath(), e.getMessage(), e);
            throw new PackageException(e.getStatusCode(), e.getMessage(), e.getMessage(),
                                       Optional.ofNullable(e.getHeaders()).orElse(new HashMap<>()));
        } catch (CancelException e) {
            log.debug("Failed to process download from {}/{} because of CancelException: {}", request.getRepoKey(),
                      request.getPath(), e.getMessage(), e);
            throw new PackageCancelException(e.getErrorCode(), e.getMessage(), e.getMessage());
        } catch (LoadHealerException e) {
            String message =  String.format("Failed to process download for path:%s in repo:%s because of " +
                            "LoadHealerException", request.getPath(), request.getRepoKey());
            log.warn(message, e);
            throw new PackageException(SC_TOO_MANY_REQUESTS, e.getMessage(), message, e);
        } catch (Exception e) {
            String message =
                    String.format("Failed to process download for path:%s in repo:%s because of: %s", request.getPath(),
                                  request.getRepoKey(), e.getMessage());
            log.error(message, e);
            throw new PackageInternalServerError(e.getMessage());
        }
    }

    @Override
    public PackageArtifact getArtifact(String repoKey, String path) throws PackageException {
        return getArtifact(new PackageDownloadContext(repoKey, path));
    }

    @Override
    public PackageArtifact getArtifact(PackageDownloadContext downloadContext) throws PackageException {
        Repo<?, ?> repo = repositoryService.repositoryByKey(downloadContext.getRepoKey());
        if (repo == null) {
            throw repoNotFoundException(downloadContext.getRepoKey());
        }
        PackageType packageType = PackageType.valueOf(repo.getDescriptor().getType().name().toUpperCase());
        if (isStoreArtifactsLocally(downloadContext.getRepoKey())) {
            VfsFile<?> vfsFile = getVfsFile(downloadContext, true);
            return PackageArtifactMapper.artifact(vfsFile, packageType);
        }
        BaseResource resource = getResource(downloadContext);
        if (resource instanceof UnfoundRepoResource) {
            throw new PackageNotFoundException();
        }
        if (resource instanceof BlockedRepoResource) {
            throw new PackageForbiddenException();
        }
        return PackageArtifactMapper.artifact((RepoResource) resource, packageType);
    }

    @Override
    public InputStream getStream(String repoKey, String path) throws PackageException {
        return getStream(new PackageDownloadContext(repoKey, path));
    }

    /**
     * IMPORTANT NOTE:
     * Flows that go through this method will be later written to traffic log v3.
     * Please see {@link org.artifactory.webapp.servlet.TrafficFilter} for more details.
     */
    @Override
    public InputStream getStream(PackageDownloadContext downloadContext) throws PackageException {
        TrafficContextThreadLocal.setTrafficLogV3Context(downloadContext.getRepoKey(), downloadContext.getPath());
        if (isStoreArtifactsLocally(downloadContext.getRepoKey())) {
            boolean isRemoteRepo = repositoryService.isRemoteRepoExist(downloadContext.getRepoKey());
            // only in case of remote repository, we're allowing expired resource, as it will be handled by the remote
            VfsFile<?> vfsFile = getVfsFile(downloadContext, !isRemoteRepo);
            return vfsFile.getStream();
        }
        return getResourceStreamHandle(downloadContext).getInputStream();
    }

    @Override
    public BaseResource getResource(PackageDownloadContext packageDownloadContext) throws PackageException {
        Repo<?, ?> repo = getRepo(packageDownloadContext.getRepoKey());
        RepoResource resource = getRepoResource(repo, packageDownloadContext);
        translateErrorsToPackageException(resource);
        return resource;
    }

    private Repo<?, ?> getRepo(String repoKey) throws PackageException {
        Repo<?, ?> repo = repositoryService.repositoryByKey(repoKey);
        if (repo == null || !repo.isReal()) {
            throw repoNotFoundException(repoKey);
        }
        return repo;
    }

    private RepoResource getRepoResource(Repo<?, ?> repo, PackageDownloadContext packageDownloadContext) {
        InternalArtifactoryRequest request = fromPackageDownloadContext(packageDownloadContext);
        return repo.getInfo(new DownloadRequestContext(request));
    }

    private void translateErrorsToPackageException(RepoResource resource) throws PackageException {
        if (resource instanceof UnfoundRepoResource unfoundRepoResource) {
            switch (unfoundRepoResource.getStatusCode()) {
                case SC_NOT_FOUND:
                    throw new PackageNotFoundException(unfoundRepoResource.getDetail());
                case SC_FORBIDDEN:
                    throw new PackageForbiddenException(unfoundRepoResource.getDetail());
                default:
                    throw new PackageException(unfoundRepoResource.getStatusCode(), unfoundRepoResource.getDetail(),
                            unfoundRepoResource.getDetail());
            }
        } else if (resource instanceof BlockedRepoResource blockedRepoResource) {
            String internalMessage;
            if (blockedRepoResource.getMessage() == null) {
                if (StringUtils.isNotBlank(blockedRepoResource.getPath())) {
                    internalMessage = "Blocked repo resource for path: " + blockedRepoResource.getPath();
                } else {
                    internalMessage = "Blocked repo resource without path";
                }
            } else {
                internalMessage = blockedRepoResource.getMessage();
            }
            throw new PackageForbiddenException(internalMessage);
        }
    }

    @Override
    public BaseResource getResourceFromVirtualCache(String repoKey, String path) throws PackageException {
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        VirtualRepo virtualRepo = getVirtualRepo(repoKey);
        InternalArtifactoryRequest request = new InternalArtifactoryRequest(repoPath, false);
        RepoResource cachedResource = virtualRepo.getInfoFromVirtualCache(new DownloadRequestContext(request));
        if (cachedResource.isFound() && virtualRepo.isCachedResourceExpired(cachedResource)) {
            cachedResource = new ExpiredRepoResource(cachedResource);
        }
        return cachedResource;
    }

    @Override
    public CloseableHttpResponse executeRemoteHttpRequest(String remoteRepoKey, HttpRequestBase httpRequest)
            throws PackageException {
        RemoteRepo<?, ?> remoteRepo = repositoryService.remoteRepositoryByKey(remoteRepoKey);
        if (remoteRepo == null) {
            String message = "Remote repo " + remoteRepoKey + " does not exist";
            throw new PackageException(SC_NOT_FOUND, message, message);
        }
        if (remoteRepo.isOffline() || remoteRepo.isAssumedOffline() || remoteRepo.isBlackedOut()) {
            String message = "Remote repo " + remoteRepoKey + " is offline";
            throw new PackageException(SC_NOT_FOUND, message, message);
        }
        try {
            HttpRepo httpRepo = (HttpRepo) remoteRepo;
            return httpRepo.executeMethod(httpRequest);
        } catch (ForbiddenException e) {
            throw new PackageForbiddenException(e.getMessage(), e.getMessage(), e);
        } catch (SocketTimeoutException | ConnectTimeoutException e) {
            String errorMessage = "Timeout occurred while executing http request" +
                    "in "+ remoteRepoKey + ": " + e.getMessage();
            ExceptionLogUtils.debugOrWarn(log, errorMessage, e);
            String message = "Failed to execute http request with repo " + remoteRepoKey;
            int returnCode = getStatusCodeFromRequestWithoutResponse(e);
            throw new PackageException(returnCode, message, message, e);
        } catch (IOException e) {
            String errorMessage =
                    "An error occurred while executing http request in " + remoteRepoKey + ": " + e.getMessage();
            ExceptionLogUtils.debugOrError(log, errorMessage, e);
            String message = "Failed to execute http request with repo " + remoteRepoKey;
            int returnCode = getStatusCodeFromRequestWithoutResponse(e);
            throw new PackageException(returnCode, message, message, e);
        }
    }

    /**
     * Execute an HTTP request with the remote repo context and associate the response with a traffic-log path.
     * Sets the traffic-log V3 context with the provided {@code trafficPath} before executing the request,
     * ensuring that any downloaded bytes are logged under that path rather than the upstream request URI.
     *
     * @param remoteRepoKey the remote repository key used to execute the request
     * @param trafficPath   the repository path to associate with traffic logging
     * @param httpRequest   the HTTP request to execute
     * @return {@link CloseableHttpResponse} the HTTP response
     * @throws PackageException in case of missing repository, remote request failure or internal error
     */
    public CloseableHttpResponse executeRemoteHttpRequest(String remoteRepoKey, String trafficPath,
            HttpRequestBase httpRequest) throws PackageException {
        TrafficContextThreadLocal.setTrafficLogV3Context(remoteRepoKey, trafficPath);
        return executeRemoteHttpRequest(remoteRepoKey, httpRequest);
    }

    /**
     * IMPORTANT NOTE:
     * Flows that go through this method will be later written to traffic log v3.
     * Please see {@link org.artifactory.webapp.servlet.TrafficFilter} for more details.
     */
    @Override
    public InputStream getStreamFromVirtualCache(String repoKey, String path) throws PackageException {
        TrafficContextThreadLocal.setTrafficLogV3Context(repoKey, path);
        VirtualRepo virtualRepo = getVirtualRepo(repoKey);
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        InternalArtifactoryRequest request = new InternalArtifactoryRequest(repoPath, false);
        RepoResource cachedResource = virtualRepo.getInfoFromVirtualCache(new DownloadRequestContext(request));
        if (cachedResource.isFound() && !virtualRepo.isCachedResourceExpired(cachedResource)) {
            return ArtifactExtractor.getResourceStreamHandle(cachedResource, new DownloadRequestContext(request),
                    virtualRepo, repositoryService).getInputStream();
        }
        String message =
                String.format("Failed to find artifact in virtual repo:%s, cache path:%s, cause: not found", repoKey,
                        path);
        throw new PackageNotFoundException(message);
    }

    public PackageArtifact getArtifactFromVirtualCache(String repoKey, String path) throws PackageException {
        VirtualRepo virtualRepo = getVirtualRepo(repoKey);
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        InternalArtifactoryRequest request = new InternalArtifactoryRequest(repoPath, false);
        RepoResource cachedResource = virtualRepo.getInfoFromVirtualCache(new DownloadRequestContext(request));
        if (cachedResource.isFound() && !virtualRepo.isCachedResourceExpired(cachedResource)) {
            return PackageArtifactMapper.artifact(cachedResource, PackageType.valueOf(
                    virtualRepo.getRepoConfig().getPackageType().getType().toUpperCase()));
        }
        String message =
                String.format("Failed to find artifact in virtual repo:%s, cache path:%s, cause: not found", repoKey,
                              path);
        throw new PackageNotFoundException(message);
    }

    @NonNull
    private VirtualRepo getVirtualRepo(String repoKey) throws PackageRepoNotFoundException {
        VirtualRepo virtualRepo = repositoryService.virtualRepositoryByKey(repoKey);
        if (virtualRepo == null) {
            throw new PackageRepoNotFoundException("Virtual repo " + repoKey + " does not exist");
        }
        return virtualRepo;
    }

    /**
     * IMPORTANT NOTE:
     * Flows that go through this method will be later written to traffic log v3.
     * Please see {@link org.artifactory.webapp.servlet.TrafficFilter} for more details.
     */
    @Override
    public InputStream getStreamFromResource(BaseResource resource) throws PackageException {
        TrafficContextThreadLocal.setTrafficLogV3Context(resource.getRepoKey(), resource.getPath());
        if (resource instanceof RepoResource repoResource) {
            PackageDownloadContext packageDownloadContext =
                    new PackageDownloadContext(resource.getRepoKey(), resource.getPath());
            InternalArtifactoryRequest request = fromPackageDownloadContext(packageDownloadContext);
            if (isStoreArtifactsLocally(resource.getRepoKey())) {
                request.addHeader(BYPASS_UNSCANNED_BLOCKING_HEADER,
                        Boolean.TRUE.toString()); // need for remote repository
                return ArtifactExtractor.locateAndGetByInfo(repoResource, new DownloadRequestContext(request), false,
                                repositoryService)
                        .getStream();
            }
            Repo<?, ?> repo = repositoryService.repositoryByKey(repoResource.getRepoKey());
            if (repo == null) {
                throw repoNotFoundException(repoResource.getRepoKey());
            }
            return ArtifactExtractor.getResourceStreamHandle(repoResource, new DownloadRequestContext(request), repo,
                            repositoryService)
                    .getInputStream();
        }
        String message =
                String.format("Failed to get stream from repo:%s, path:%s, cause: not found", resource.getRepoKey(),
                        resource.getPath());
        throw new PackageNotFoundException(message);
    }

    private static PackageException repoNotFoundException(String repoKey) {
        String message = String.format("Failed to find repository %s", repoKey);
        return new PackageRepoNotFoundException(message);
    }

    private InternalArtifactoryRequest fromPackageDownloadContext(PackageDownloadContext downloadContext) {
        return fromPackageDownloadContext(downloadContext, null);
    }

    private InternalArtifactoryRequest fromPackageDownloadContext(PackageDownloadContext downloadContext,
            String overrideRepoKey) {
        final String repoKey = overrideRepoKey == null ? downloadContext.getRepoKey() : overrideRepoKey;
        RepoPath repoPath = RepoPathFactory.create(repoKey, downloadContext.getPath());
        InternalArtifactoryRequest request =
                new InternalArtifactoryRequest(repoPath, downloadContext.isDisableRedirect());
        downloadContext.getDelegationAllowedHeaders().forEach(request::setHeaderEligibleForDelegation);
        request.setZipResourcePath(downloadContext.getZipResourcePath());
        request.setReplaceHeadRequestWithGet(downloadContext.isReplaceHeadRequestWithGet());
        updateRequestFromRequestContext(request, downloadContext);
        request.setAlternateRemoteSiteUrl(downloadContext.getOverrideRemoteSiteUrl());
        request.setRequestMethod(downloadContext.getHttpMethod());
        request.setSkipEncoding(downloadContext.isSkipEncoding());
        request.setReplicationOriginatedDownloadRequest(downloadContext.isReplicationOriginatedDownloadRequest());
        request.setDisableFolderRedirectAssertion(downloadContext.isDisableFolderRedirectAssertion());
        request.setSkipPackageUsageTrack(downloadContext.isSkipPackageUsageTrack());

        if (downloadContext.getOriginalRepoKey() != null) {
            RepoPath originalRepoPath =
                    RepoPathFactory.create(downloadContext.getOriginalRepoKey(), downloadContext.getPath());
            request.setOriginalRepoPath(originalRepoPath);
        }
        return request;
    }

    private void updateRequestFromRequestContext(InternalArtifactoryRequest internalArtifactoryRequest,
            PackageRequestContext downloadContext) {
        if (StringUtils.isNotBlank(downloadContext.getAlternativeUrl())) {
            internalArtifactoryRequest.setAlternativeRemoteDownloadUrl(downloadContext.getAlternativeUrl());
        }
        internalArtifactoryRequest.addHeaders(downloadContext.getRequestHeaders());
        internalArtifactoryRequest.setServletContextUrl(downloadContext.getServletContextUrl());
    }

    @NonNull
    private VfsFile<?> getVfsFile(PackageDownloadContext downloadContext, boolean throwIfExpire)
            throws PackageException {
        Repo<?, ?> repo = repositoryService.repositoryByKey(downloadContext.getRepoKey());
        if (repo == null) {
            throw repoNotFoundException(downloadContext.getRepoKey());
        }
        InternalArtifactoryRequest request = fromPackageDownloadContext(downloadContext);
        request.addHeader(BYPASS_UNSCANNED_BLOCKING_HEADER, Boolean.TRUE.toString()); // need for remote repository
        return ArtifactExtractor.locateFile(new DownloadRequestContext(request), repositoryService, throwIfExpire);
    }

    @NonNull
    private ResourceStreamHandle getResourceStreamHandle(PackageDownloadContext downloadContext)
            throws PackageException {
        Repo<?, ?> repo = repositoryService.repositoryByKey(downloadContext.getRepoKey());
        if (repo == null) {
            throw repoNotFoundException(downloadContext.getRepoKey());
        }
        DownloadRequestContext context = new DownloadRequestContext(fromPackageDownloadContext(downloadContext));
        RepoResource resource = repo.getInfo(context);
        assertValidResource(resource);
        return ArtifactExtractor.getResourceStreamHandle(resource, context, repo, repositoryService);
    }


    /**
     * This function return if the given repo store artifact locally (local,virtual or remote with cache repo)
     *
     * @throws PackageException if given repo not exists
     */
    private boolean isStoreArtifactsLocally(String repoKey) throws PackageException {
        RepoConfig repoConfig = repositoryService.getRepositoryConfigByKey(repoKey);
        if (repoConfig == null) {
            throw repoNotFoundException(repoKey);
        }
        if (repoConfig.isReal() && repoConfig instanceof RemoteRepoConfig remoteRepo) {
            return remoteRepo.getRepoTypeConfig().isStoreArtifactsLocally();
        }
        return true;
    }

    /**
     * Get the status code from the exception, if the exception is a timeout exception return SC_REQUEST_TIMEOUT
     *
     * @param exception the exception
     * @return the status code
     */
    private int getStatusCodeFromRequestWithoutResponse(Exception exception) {
        return exception instanceof ConnectTimeoutException || exception instanceof SocketTimeoutException ?
                SC_REQUEST_TIMEOUT : SC_BAD_REQUEST;
    }
}
