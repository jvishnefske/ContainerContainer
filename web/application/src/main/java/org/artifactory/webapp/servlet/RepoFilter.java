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

package org.artifactory.webapp.servlet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.jfrog.build.acl.bridge.BuildFacade;
import jakarta.annotation.Nullable;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.addon.npm.NpmAuditRequestWrapper;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.FolderExpectedException;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.request.DownloadService;
import org.artifactory.api.request.UploadService;
import org.artifactory.api.rest.constant.ArtifactRestConstants;
import org.artifactory.api.rest.constant.GitLfsConstants;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.webdav.WebdavService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.exception.CancelException;
import org.artifactory.exception.LoadHealerException;
import org.artifactory.exception.SQLIntegrityException;
import org.artifactory.download.RateLimiterException;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.exception.WorkerStopException;
import org.artifactory.mime.NamingUtils;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.cache.expirable.CacheExpiry;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.RepoRequests;
import org.artifactory.security.HttpAuthenticationDetails;
import org.artifactory.throttling.lh.model.RunningActivityHandler;
import org.artifactory.throttling.lh.service.LoadHealerService;
import org.artifactory.util.BasicHttpUtils;
import org.artifactory.util.HttpUtils;
import org.artifactory.util.URIUtil;
import org.artifactory.util.UiRequestUtils;
import org.jfrog.client.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS;
import static org.artifactory.common.ConstantValues.allowMatrixParamsForDownloadsRequests;
import static org.artifactory.throttling.lh.config.LoadHealerActivity.LIMIT_ACTIVITY;
import static org.artifactory.throttling.lh.config.LoadHealerActivity.PARALLEL_DOWNLOAD_UPLOAD_PER_PACKAGE_TYPE;
import static org.artifactory.throttling.lh.config.LoadHealerActivity.PARALLEL_UPLOAD_PRE_REPO_KEY;
import static org.artifactory.util.evidence.EvidenceConfigurationUtil.EVIDENCE_DIR;
import static org.artifactory.webapp.servlet.RequestUtils.ATTR_ARTIFACTORY_REPOSITORY_PATH;
import static org.artifactory.webapp.servlet.RequestUtils.ATTR_ARTIFACTORY_REQUEST_PROPERTIES;
import static org.artifactory.webapp.servlet.RequestUtils.RepoRequestAnalysis.NOT_A_REPO_REQUEST;
import static org.artifactory.webapp.servlet.RequestUtils.RepoRequestAnalysis.REPO_REQUEST_REPO_EXISTS;
import static org.artifactory.webapp.servlet.RequestUtils.RepoRequestAnalysis.REPO_REQUEST_REPO_NOT_EXISTS;
import static org.jfrog.client.util.PathUtils.trimLeadingSlashes;

/**
 * @author Yoav Landman
 */
public class RepoFilter extends DelayedFilterBase {
    public static final String ACTION_DOWNLOAD = "download";
    public static final String ACTION_UPLOAD = "upload";
    public static final String ACTION_GET_META = "meta";
    public static final String ACTION_GET_PROPS = "props";
    public static final String ACTION_LIST = "list";
    public static final String ACTION_WEBDAV = "webdav";
    public static final String ACTION_AUDIT = "audit";

    private static final String SEMICOLON = ";";
    private static final Logger log = LoggerFactory.getLogger(RepoFilter.class);

    // Inbound headers exposed in the ?trace output (whitelist; may hold additional headers in the future)
    private static final List<String> TRACE_WHITELISTED_HEADERS = List.of(
            ArtifactoryHeaders.ORIGINAL_CLIENT_URI,
            ArtifactoryHeaders.JFROG_OVERRIDE_BASE_URL,
            ArtifactoryHeaders.X_FORWARDED_HOST,
            ArtifactoryHeaders.X_FORWARDED_PROTO);

    @Override
    public void initLater(FilterConfig filterConfig) {
        String nonUiPathPrefixes = filterConfig.getInitParameter("nonUiPathPrefixes");
        String uiPathPrefixes = filterConfig.getInitParameter("UiPathPrefixes");
        List<String> nonUiPrefixes = PathUtils.delimitedListToStringList(nonUiPathPrefixes, ",");
        UiRequestUtils.setNonUiPathPrefixes(nonUiPrefixes);
        RequestUtils.setNonUiPathPrefixes(nonUiPrefixes);
        List<String> uiPrefixes = PathUtils.delimitedListToStringList(uiPathPrefixes, ",");
        uiPrefixes.add(HttpUtils.WEBAPP_URL_PATH_PREFIX);
        RequestUtils.setUiPathPrefixes(uiPrefixes);
        UiRequestUtils.setUiPathPrefixes(uiPrefixes);
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        if (shouldSkipFilter(req)) {
            chain.doFilter(req, resp);
            return;
        }
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;
        final String servletPath = RequestUtils.getServletPathFromRequest(request);
        execute(chain, request, response, servletPath);
    }

    private LoadHealerService getLoadHealerService() {
        return getContext().beanForType(LoadHealerService.class);
    }

    private void execute(FilterChain chain, final HttpServletRequest request, HttpServletResponse response,
            String servletPath) throws IOException, ServletException {
        if (log.isDebugEnabled()) {
            log.debug("Entering request {}.", requestDebugString(request));
        }

        RequestUtils.RepoRequestAnalysis repoRequestAnalysis = analyzeRepoRequest(request, servletPath);

        String method = request.getMethod().toLowerCase().intern();
        if (isRepoListing(servletPath, repoRequestAnalysis)) {
            ArtifactoryRequest artifactoryRequest = createArtifactoryRequest(request);
            doRepoListing(request, response, servletPath, artifactoryRequest);
        } else if (isBuildInfoDeploymentRequest(repoRequestAnalysis, servletPath, method)) {
            doBuildUploadRedirect(request, response);
        } else if (isNpmAuditRequest(repoRequestAnalysis, servletPath, method)) {
            doNpmAuditRequest(chain, request, response);
        } else if (repoRequestAnalysis == REPO_REQUEST_REPO_NOT_EXISTS &&
                ConstantValues.respondWith404ForNonExistentRepo.getBoolean()) {
            response.sendError(HttpStatus.SC_NOT_FOUND);
        } else if (repoRequestAnalysis == REPO_REQUEST_REPO_EXISTS) {
            doRepoRequest(request, response, servletPath, method);
        } else if (!response.isCommitted()) {
            // Webdav request not on repository, return 403
            if (RequestUtils.isWebdavRequest(request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                if (log.isDebugEnabled()) {
                    log.debug("Received webdav request on {} which is not a repository!\n Returning: {}", servletPath,
                            HttpServletResponse.SC_FORBIDDEN);
                }
            } else {
                // This is a workaround for the Git LFS bug in 0.5.1 - see RTFACT-7587.
                chain.doFilter(wrapRequestIfNeeded(request), response);
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Exiting request: {}", requestDebugString(request));
        }
    }

    private void doRepoRequest(HttpServletRequest request, HttpServletResponse response, String servletPath,
            String method) throws IOException, ServletException {
        ArtifactoryRequest artifactoryRequest = createArtifactoryRequest(request);
        ArtifactoryResponse artifactoryResponse = createArtifactoryResponse(response);

        if (checkNoUrlInPath(artifactoryRequest, artifactoryResponse)) {
            return;
        }

        if (artifactoryRequest.isDirectoryRequest() && isGetOrHeadRequest(method)
                && !shouldDirectoryRequestTreatAsDownload(request, artifactoryRequest)) {
            doDirectoryRequest(request, response, servletPath, artifactoryRequest, artifactoryResponse);
        } else {

            try {
                initRequestContext(method, artifactoryRequest, artifactoryResponse);
                if (isGetOrHeadRequest(method)) {
                    doGetOrHeadRequest(request, response, method, artifactoryRequest, artifactoryResponse);
                } else if ("put".equals(method)) {
                    doUpload(artifactoryRequest, artifactoryResponse);
                } else {
                    doWebDavMethod(request, response, method, artifactoryRequest, artifactoryResponse);
                }
            } finally {
                RepoRequests.destroy();
            }
        }
    }

    private void doDirectoryRequest(HttpServletRequest request, HttpServletResponse response, String servletPath,
            ArtifactoryRequest artifactoryRequest, ArtifactoryResponse artifactoryResponse)
            throws IOException, ServletException {
        //Check that this is not a recursive call
        if (artifactoryRequest.isRecursive()) {
            String msg = "Recursive call detected for '" + request + "'. Returning nothing.";
            artifactoryResponse.sendError(HttpStatus.SC_NOT_FOUND, msg, log);
            return;
        }
        log.debug("Serving a directory get request.");
        doRepoListing(request, response, servletPath, artifactoryRequest);
    }

    private void doGetOrHeadRequest(HttpServletRequest request, HttpServletResponse response, String method,
            ArtifactoryRequest artifactoryRequest, ArtifactoryResponse artifactoryResponse) throws IOException {
        if (!allowMatrixParamsForDownloadsRequests.getBoolean() && isInvalidUrl(artifactoryRequest)) {
            artifactoryResponse.sendError(HttpStatus.SC_BAD_REQUEST, "Invalid matrix parameters.", log);
            return;
        }
        /*
          Do not check for this parameter when not performing a get/head request so that the container
          doesn't try to read the parameters and verify the size of the form in case of an upload
         */
        if (artifactoryRequest.getParameter(ArtifactRestConstants.TRACE_PARAM) != null) {
            //Re-init the context with the trace logging response
            artifactoryResponse = newTraceLoggingResponse(request, artifactoryResponse);
            initRequestContext(method, artifactoryRequest, artifactoryResponse);
        }

        // TODO: Should we return 405 Method not allowed for head request on properties:
        // TODO: For now the HEAD request will ignore this properties query param
        if (artifactoryRequest.getParameter(ArtifactRestConstants.PROPERTIES_PARAM) != null) {
            //Set the response to return only the properties of the item in json format
            artifactoryResponse.setPropertiesMediaType(MediaType.APPLICATION_JSON.toString());
        }
        if (artifactoryRequest.getParameter(ArtifactRestConstants.PROPERTIES_XML_PARAM) != null) {
            //Set the response to return only the properties of the item in json format
            artifactoryResponse.setPropertiesMediaType(MediaType.APPLICATION_XML.toString());
        }

        doDownload(request, response, method, artifactoryRequest, artifactoryResponse);
    }

    /**
     * Wraps the response in a {@link TraceLoggingResponse} for the {@code ?trace} flow, attaching the whitelisted
     * inbound headers so they are rendered in the trace metadata block.
     */
    TraceLoggingResponse newTraceLoggingResponse(HttpServletRequest request, ArtifactoryResponse artifactoryResponse) {
        TraceLoggingResponse traceLoggingResponse = new TraceLoggingResponse(artifactoryResponse);
        traceLoggingResponse.setRequestHeaders(collectWhitelistedHeaders(request));
        return traceLoggingResponse;
    }

    Map<String, String> collectWhitelistedHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        TRACE_WHITELISTED_HEADERS.forEach(header -> {
            // Intentionally using getHeader (first value only): for these proxy-identity headers the first
            // value is the relevant one for trace debugging; joining multi-hop values would only add noise.
            String value = request.getHeader(header);
            if (StringUtils.isNotBlank(value)) {
                headers.put(header, value);
            }
        });
        return headers;
    }

    private void doNpmAuditRequest(FilterChain chain, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        updateAction(ACTION_AUDIT);
        CurrentActivity.PACKAGE.put(PackageType.NPM);
        chain.doFilter(new NpmAuditRequestWrapper(request), response);
    }

    private static boolean isRepoListing(String servletPath, RequestUtils.RepoRequestAnalysis repoRequestAnalysis) {
        return repoRequestAnalysis == REPO_REQUEST_REPO_EXISTS && isListBrowsingPath(servletPath);
    }

    @VisibleForTesting
    static RequestUtils.RepoRequestAnalysis analyzeRepoRequest(HttpServletRequest request,
            @Nullable String servletPath) {
        return servletPath == null ? NOT_A_REPO_REQUEST : RequestUtils.analyzeRepoRequest(request);
    }

    @VisibleForTesting
    static boolean isListBrowsingPath(String servletPath) {
        return servletPath.startsWith("/" + ArtifactoryHeaders.LIST_BROWSING_PATH)
                && servletPath.endsWith("/");
    }

    private boolean isInvalidUrl(ArtifactoryRequest artifactoryRequest) {
        try {
            String uri = URIUtil.decode(artifactoryRequest.getUri().toLowerCase());
            int index = uri.indexOf(SEMICOLON);
            if (index >= 0 && uri.indexOf("..", index) >= 0) {
                return true;
            } else {
                uri = URIUtil.decode(uri);
                int index2 = uri.indexOf(SEMICOLON);
                return index2 >= 0 && uri.indexOf("..", index2) >= 0;
            }
        } catch (HttpException e) {
            return false;
        }
    }

    protected ArtifactoryRequest createArtifactoryRequest(HttpServletRequest request)
            throws UnsupportedEncodingException {
        ArtifactoryRequest artifactoryRequest = new HttpArtifactoryRequest(request);
        updatePackageTypeFromRepository(artifactoryRequest);
        return artifactoryRequest;
    }

    protected void updatePackageTypeFromRepository(ArtifactoryRequest artifactoryRequest) {
        if (getContext() == null || getContext().getRepositoryService() == null) {
            return; // Should never happen in production
        }
        RepoConfig config = getContext().getRepositoryService().repoConfigByKey(artifactoryRequest.getRepoKey());
        if (config != null
                // The following line is just for mocked data in tests, should never happen in production
                // unless configuration is corrupt
                && config.getPackageType() != null && config.getPackageType().getType() != null) {
            CurrentActivity.PACKAGE.put(config.getPackageType().getType().toLowerCase());
        }
    }

    protected void updateAction(String actionType) {
        CurrentActivity.GROUP.put(actionType);
    }

    protected String resolveDownloadAction(ArtifactoryRequest request, ArtifactoryResponse response) {
        if (request.isMetadata()) {
            return ACTION_GET_META;
        }
        if (response.isPropertiesQuery()) {
            return ACTION_GET_PROPS;
        }

        // Let package-specific code to check if this is a metadata request
        String action = resolveDownloadActionFromPackageType(request);
        if (action != null) {
            return action;
        }
        return ACTION_DOWNLOAD;
    }

    protected String resolveDownloadActionFromPackageType(ArtifactoryRequest request) {
        ArtifactoryContext context = getContext();
        if (context == null) {
            return null; // Should never happen in production
        }
        if (SystemRepos.SystemRepoConfig.isSystemRepo(request.getRepoKey())) {
            return null; // System repos do not have proper package type handling
        }
        RepoType type = context.getRepositoryService().getRepoType(request.getRepoKey());
        if (type != null) {
            CacheExpiry metadataChecker = context.beanForType(CacheExpiry.class);
            return metadataChecker.getMetadataArtifactType(type, request.getRepoKey(), request.getPath());
        }
        return null;
    }

    protected ArtifactoryResponse createArtifactoryResponse(HttpServletResponse response) {
        return new HttpArtifactoryResponse(response);
    }

    @VisibleForTesting
    boolean checkNoUrlInPath(ArtifactoryRequest artifactoryRequest, ArtifactoryResponse artifactoryResponse)
            throws IOException {

        String msgPrefix = "Forbidden Url in path: ";
        if (HttpUtils.isValidUrl(artifactoryRequest.getPath())) {
            artifactoryResponse.sendError(HttpStatus.SC_BAD_REQUEST, msgPrefix + artifactoryRequest.getPath(), log);
            return true;
        }

        if (StringUtils
                .isNotBlank(artifactoryRequest.getParameter(ArtifactoryRequest.PARAM_ALTERNATIVE_REMOTE_SITE_URL))) {
            artifactoryResponse.sendError(HttpStatus.SC_BAD_REQUEST,
                    msgPrefix + artifactoryRequest.getParameter(ArtifactoryRequest.PARAM_ALTERNATIVE_REMOTE_SITE_URL),
                    log);
            return true;
        }

        if (StringUtils.isNotBlank(
                artifactoryRequest.getParameter(ArtifactoryRequest.PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL))) {
            artifactoryResponse.sendError(HttpStatus.SC_BAD_REQUEST, msgPrefix +
                    artifactoryRequest.getParameter(ArtifactoryRequest.PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL), log);
            return true;
        }
        return false;
    }

    private boolean isNpmAuditRequest(RequestUtils.RepoRequestAnalysis repoRequestAnalysis, String servletPath,
            String method) {
        boolean repoRequest = (repoRequestAnalysis == REPO_REQUEST_REPO_EXISTS);
        return !repoRequest &&
                method.equals("post") &&
                ((servletPath.endsWith("/npm/v1/security/audits")) ||
                        (servletPath.endsWith("/npm/v1/security/audits/quick")) ||
                        (servletPath.endsWith("/npm/v1/security/advisories/bulk")));
    }

    /**
     * @return true if it is a {@param repoRequest} (meaning it went straight to /artifactory/*repo-name* (so not api
     * or ui)
     * And {@param servletPath} starts with artifactory-build-info repo key
     * And {@param servletPath} doesn't end with '/' (so not a folder)
     * And {@param method} is PUT (so just deploy request is redirected)
     */
    private boolean isBuildInfoDeploymentRequest(RequestUtils.RepoRequestAnalysis repoRequestAnalysis,
            String servletPath, String method) {
        boolean repoRequest = repoRequestAnalysis == REPO_REQUEST_REPO_EXISTS;
        return repoRequest
                && getBuildFacade().getAllBuildInfoRepos().stream().anyMatch(r -> servletPath.startsWith("/" + r))
                && !servletPath.endsWith("/")
                && HttpMethod.PUT.name().equalsIgnoreCase(method)
                && !isEvidenceServletPath(servletPath);
    }

    @VisibleForTesting
    boolean isEvidenceServletPath(String servletPath) {
        String path = trimLeadingSlashes(servletPath);
        String[] arr = path.split("/", 3);
        return arr.length > 1 && arr[1].equals(EVIDENCE_DIR);
    }

    /**
     * Forwards build-info related requests to the /api/build/buildUploadRedirect endpoint
     */
    private void doBuildUploadRedirect(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        log.debug("Forwarding internally to an build resource upload redirect endpoint");
        //We're redirecting so the original servlet path goes out the window, but the request might contain matrix
        //params
        //that we need to propagate back to the upload service when the build service re-does the upload.
        String originalServletPath = RequestUtils.getServletPathFromRequest(request);
        log.debug("Original servlet path inferred as '{}'", originalServletPath);
        RequestDispatcher dispatcher =
                request.getRequestDispatcher("/api/build/buildUploadRedirect" + originalServletPath);
        dispatcher.forward(request, response);
    }

    private boolean isGetOrHeadRequest(String method) {
        return "get".equals(method) || "head".equals(method);
    }

    private void initRequestContext(String method, ArtifactoryRequest artifactoryRequest,
            ArtifactoryResponse artifactoryResponse) {
        RepoRequests.set(method, getContext().beanForType(SecurityService.class).currentUsername(),
                artifactoryRequest, artifactoryResponse);
    }

    @VisibleForTesting
    void doWebDavMethod(HttpServletRequest request, HttpServletResponse response, String method,
            ArtifactoryRequest artifactoryRequest, ArtifactoryResponse artifactoryResponse) throws IOException {
        updateAction(ACTION_WEBDAV);
        if (!getWebdavService().handleRequest(method, artifactoryRequest, artifactoryResponse)) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setHeader("Allow", PathUtils.collectionToDelimitedString(getWebdavService().supportedMethods()));
            log.info("Received unsupported request method: {} from: {}", method, request.getRemoteAddr());
        }
    }

    private void doUpload(ArtifactoryRequest artifactoryRequest, ArtifactoryResponse artifactoryResponse)
            throws IOException {
        LoadHealerService loadHealerService = getLoadHealerService();
        try (
                RunningActivityHandler parallelUploadPerRepoKeyActivity = loadHealerService.startActivity(
                        PARALLEL_UPLOAD_PRE_REPO_KEY, artifactoryRequest.getRepoKey());
                RunningActivityHandler parallelUploadPerPackageTypeActivity = loadHealerService.startActivity(
                        PARALLEL_DOWNLOAD_UPLOAD_PER_PACKAGE_TYPE, CurrentActivity.PACKAGE.get());
                RunningActivityHandler uploadActivity = loadHealerService.startActivity(LIMIT_ACTIVITY, ACTION_UPLOAD)
        ) {
            loadHealerService.assertAllows(uploadActivity);
            loadHealerService.assertAllows(parallelUploadPerRepoKeyActivity);
            loadHealerService.assertAllows(parallelUploadPerPackageTypeActivity);
            log.debug("Serving an upload request.");
            updateAction(ACTION_UPLOAD);
            getUploadEngine().upload(artifactoryRequest, artifactoryResponse);
        } catch (CancelException e) {
            log.error("Upload request has been canceled: {}", e.getMessage());
            artifactoryResponse.sendInternalError(e, log);
        } catch (SQLIntegrityException | FileExpectedException | FolderExpectedException | WorkerStopException |
                 UnifiedEventException e) {
            log.debug("Upload request has been failed: {}", e.getMessage());
            artifactoryResponse.sendError(HttpStatus.SC_CONFLICT, e.getMessage(), log);
        } catch (IllegalArgumentException e) {
            log.debug("Upload request has been failed with invalid request argument: {}", e.getMessage());
            artifactoryResponse.sendError(HttpStatus.SC_BAD_REQUEST, e.getMessage(), log);
        } catch (LoadHealerException e) {
            log.debug("Upload request has been blocked by load healer: {}", e.getMessage());
            artifactoryResponse.sendError(TOO_MANY_REQUESTS.getStatusCode(), e.getMessage(), log);
        } catch (Exception e) {
            log.error("Upload request of {} failed due to: ", artifactoryRequest.getRepoPath(), e);
            artifactoryResponse.sendInternalError(e, log);
        }
    }

    private void doDownload(HttpServletRequest request, HttpServletResponse response, String method,
            ArtifactoryRequest artifactoryRequest, ArtifactoryResponse artifactoryResponse) throws IOException {
        updateAction(resolveDownloadAction(artifactoryRequest, artifactoryResponse));
        if (redirectLegacyMetadataRequest(request, response, artifactoryRequest)) {
            return;
        }
        LoadHealerService loadHealerService = getLoadHealerService();
        try (
                RunningActivityHandler parallelDownloadPerPackageTypeActivity = loadHealerService.startActivity(
                        PARALLEL_DOWNLOAD_UPLOAD_PER_PACKAGE_TYPE, CurrentActivity.PACKAGE.get());
                RunningActivityHandler downloadActivity = loadHealerService.startActivity(LIMIT_ACTIVITY,
                        ACTION_DOWNLOAD)
        ) {
            loadHealerService.assertAllows(downloadActivity);
            loadHealerService.assertAllows(parallelDownloadPerPackageTypeActivity);
            RepoRequests.logToContext("Received request");
            getDownloadService().process(artifactoryRequest, artifactoryResponse);
        } catch (FileExpectedException e) {
            // If we try to get a file but encounter a folder and the request does not end with a '/' send a redirect
            // that adds the slash with the request with a 302 status code. In the next request if it is a head request,
            // then it is ok since the resource was found and avoid an infinite redirect situation, however if it is a
            // GET, then return a 404 since it is the incorrect resource to get (we mimic was apache servers are doing).
            // see RTFACT-2738 and RTFACT-3510
            String servletPath = RequestUtils.getServletPathFromRequest(request);
            if (!servletPath.endsWith("/")) {
                String dirPath = HttpUtils.getServletContextUrl(request) + servletPath + "/";
                RepoRequests.logToContext("Redirecting to the directory path '%s'", dirPath);
                response.sendRedirect(dirPath);
            } else if ("head".equals(method)) {
                RepoRequests.logToContext("Handling directory HEAD request ");
            } else {
                RepoRequests.logToContext("Expected file but received a directory - returning a %s response",
                        HttpServletResponse.SC_NOT_FOUND);
                artifactoryResponse.sendError(HttpServletResponse.SC_NOT_FOUND,
                        "Expected file response but received a directory response: " + e.getRepoPath(), log);
            }
        } catch (CancelException e) {
            RepoRequests.logToContext("Request has been canceled", e.getMessage(), e.getErrorCode());
            artifactoryResponse.sendError(e.getErrorCode(), "Download request has been canceled: " + e.getMessage(),
                    log);
            log.debug("Download request has been canceled" + e.getMessage(), e);
        } catch (WorkerStopException wksException) {
            RepoRequests.logToContext("Worker has stopped the request", wksException.getMessage());
            artifactoryResponse.sendError(HttpServletResponse.SC_CONFLICT, wksException.getMessage(), log);
        } catch (LoadHealerException e) {
            RepoRequests.logToContext("Load healer has blocked the request", e.getMessage());
            artifactoryResponse.sendError(TOO_MANY_REQUESTS.getStatusCode(), e.getMessage(), log);
        } catch (RateLimiterException e) {
            RepoRequests.logToContext("Rate limiter has blocked the request", e.getMessage());
            artifactoryResponse.sendError(TOO_MANY_REQUESTS.getStatusCode(), e.getMessage(), log);
        } catch (Exception e) {
            RepoRequests.logToContext("Error handling request: %s - returning a %s response", e.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            if (!(e instanceof IOException) && !artifactoryResponse.isCommitted()) {
                // io exception is handled by the download service
                artifactoryResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Could not process download request: " + e.getMessage(), log);
            }
            log.error("Could not process download request: {}", e.getMessage(), e);
        }
    }

    private void doRepoListing(HttpServletRequest request, HttpServletResponse response, String servletPath,
            ArtifactoryRequest artifactoryRequest) throws ServletException, IOException {
        log.debug("Forwarding internally to an apache-style listing page.");
        updateAction(ACTION_LIST);

        if (!servletPath.endsWith("/")) {
            response.sendRedirect(HttpUtils.getServletContextUrl(request) + servletPath + "/");
            return;
        }
        request.setAttribute(ATTR_ARTIFACTORY_REPOSITORY_PATH, artifactoryRequest.getRepoPath());
        request.setAttribute(ATTR_ARTIFACTORY_REQUEST_PROPERTIES, artifactoryRequest.getProperties());

        RequestDispatcher dispatcher = request.getRequestDispatcher("/api/nativeBrowser");
        dispatcher.forward(request, response);
    }

    private ArtifactoryContext getContext() {
        return ContextHelper.get();
    }

    @VisibleForTesting
    WebdavService getWebdavService() {
        return getContext().beanForType(WebdavService.class);
    }

    private DownloadService getDownloadService() {
        return getContext().beanForType(DownloadService.class);
    }

    private UploadService getUploadEngine() {
        return getContext().beanForType(UploadService.class);
    }

    private BuildFacade getBuildFacade() {
        return getContext().beanForType(BuildFacade.class);
    }

    private static String requestDebugString(HttpServletRequest request) {
        String queryString = request.getQueryString();
        return request.getMethod() + " (" + new HttpAuthenticationDetails(request).getRemoteAddress() + ") " +
                RequestUtils.getServletPathFromRequest(request) + (queryString != null ? "?" + queryString : "");
    }

    private boolean redirectLegacyMetadataRequest(HttpServletRequest request, HttpServletResponse response,
            ArtifactoryRequest artifactoryRequest) throws IOException {
        // redirect to the appropriate REST api for legacy metadata requests
        // for example '/path/to/item:properties' is redirected to 'api/storage/path/to/item?propertiesXml'
        String requestPath = artifactoryRequest.getPath();
        if (NamingUtils.isProperties(requestPath)) {
            RepoPath repoPath = artifactoryRequest.getRepoPath();
            log.debug("Deprecated metadata download detected: {}", request.getRequestURL());
            String location = HttpUtils.getServletContextUrl(request) +
                    "/api/storage/" + repoPath.getRepoKey() + "/" +
                    NamingUtils.stripMetadataFromPath(repoPath.getPath()) + "?" +
                    NamingUtils.getMetadataName(artifactoryRequest.getPath()) + "Xml";
            RepoRequests.logToContext("Redirecting to path '%s'", location);
            response.sendRedirect(BasicHttpUtils.encodeQuery(location));
            return true;
        }
        return false;
    }

    @VisibleForTesting
    HttpServletRequest wrapRequestIfNeeded(HttpServletRequest request) {
        if (isGitLfsRequest(request)) {
            log.debug("Identified '/api/lfs' in incoming ServletRequest path. " +
                    "Wrapping it with a GitLfsMalformedRequestWrapper");
            return new GitLfsMalformedRequestWrapper(request);
        }
        String apiPath = getApiRequestPath(request);
        if (apiPath != null) {
            log.debug("Identified '{}' in incoming ServletRequest path. " +
                    "Wrapping it with a DockerMalformedRequestWrapper", apiPath);
            return new DockerMalformedRequestWrapper(request);
        }
        return request;
    }

    @VisibleForTesting
    boolean isGitLfsRequest(HttpServletRequest request) {
        String lfsApiPath = "/api/lfs";
        String joinedRequestPath = request.getServletPath() + request.getPathInfo();
        return joinedRequestPath.contains(lfsApiPath) || request.getRequestURL().toString().contains(lfsApiPath);
    }

    @Nullable
    @VisibleForTesting
    String getApiRequestPath(HttpServletRequest request) {
        String dockerPath = "/api/docker";
        String ociPath = "/api/oci";
        String joinedRequestPath = request.getServletPath() + request.getPathInfo();
        String requestUrl = request.getRequestURL().toString();
        if (joinedRequestPath.contains(dockerPath) || requestUrl.contains(dockerPath)) {
            return dockerPath;
        }
        if (joinedRequestPath.contains(ociPath) || requestUrl.contains(ociPath)) {
            return ociPath;
        }
        return null;
    }

    /**
     * Determines whether a directory request should be treated as a download request instead of
     * returning a directory listing.
     * <p>
     * This is a special case for generic remote repositories with query parameter propagation enabled.
     * When a request has query parameters and targets a generic remote repository configured to propagate
     * query params, the request should be forwarded to the remote server as a download rather than
     * being handled as a directory listing.
     *
     * @param servletRequest the HTTP servlet request containing the query string
     * @param request        the Artifactory request containing the repository key
     * @return {@code true} if the directory request should be treated as a download (i.e., the request
     *         has query parameters, targets a generic remote repository, and that repository has
     *         query parameter propagation enabled); {@code false} otherwise
     */
    @VisibleForTesting
    boolean shouldDirectoryRequestTreatAsDownload(HttpServletRequest servletRequest,
                                                          ArtifactoryRequest request) {
        if (!ConstantValues.directoryRequestQueryParamsEnabled.getBoolean()) {
            return false;
        }
        if (StringUtils.isBlank(servletRequest.getQueryString())) {
            return false;
        }
        if (getContext() == null || getContext().getRepositoryService() == null) {
            return false;
        }
        RepoConfig config = getContext().getRepositoryService().getRepoConfigByKey(request.getRepoKey());
        if (config == null) {
            return false;
        }
        if (config.getRepoType() == RepoDetailsType.REMOTE && config.getPackageType() == RepoType.Generic) {
            RemoteRepositoryRepoTypeConfig typeConfig = (RemoteRepositoryRepoTypeConfig) config.getRepoTypeConfig();
            return typeConfig.isPropagateQueryParams();
        }
        return false;
    }

    @VisibleForTesting
    static class GitLfsMalformedRequestWrapper extends HttpServletRequestWrapper {
        GitLfsMalformedRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (name.equalsIgnoreCase(HttpHeaders.ACCEPT) || name.equalsIgnoreCase(HttpHeaders.CONTENT_TYPE)) {
                log.debug("Returning fixed Git LFS header {}", name);
                return Collections.enumeration(Lists.newArrayList(GitLfsConstants.LFS_JSON));
            } else {
                return super.getHeaders(name);
            }
        }
    }

    @VisibleForTesting
    static class DockerMalformedRequestWrapper extends HttpServletRequestWrapper {
        DockerMalformedRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (name.equalsIgnoreCase(HttpHeaders.CONTENT_TYPE) && StringUtils.isBlank(getRequest().getContentType())) {
                log.debug("Returning fixed Docker Content-Type header {}", name);
                return Collections.enumeration(Lists.newArrayList("application/octet-stream"));
            } else {
                return super.getHeaders(name);
            }
        }
    }
}