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

package org.artifactory.request;

import lombok.NonNull;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.request.ArtifactoryResponseBase;
import org.artifactory.api.rest.constant.ArtifactRestConstants;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RealRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.fs.RepoResource;
import org.artifactory.mime.NamingUtils;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.request.range.RangeAwareContext;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.resource.ZipEntryResource;
import org.artifactory.security.AccessLogger;
import org.artifactory.security.utils.AuthenticationUtils;
import org.artifactory.traffic.TrafficEntry;
import org.artifactory.traffic.TrafficService;
import org.artifactory.traffic.TrafficUtils;
import org.artifactory.traffic.entry.DownloadEntry;
import org.artifactory.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.util.UriUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.api.request.ArtifactoryHeaders.ORIGIN_REMOTE_PATH;
import static org.artifactory.api.rest.constant.ArtifactRestConstants.MT_ITEM_PROPERTIES;
import static org.artifactory.request.range.ResponseWithRangeSupportHelper.createRangeAwareContext;
import static org.artifactory.util.RepoResourceUtils.isContentEncoded;

/**
 * @author yoavl
 */
public final class RequestResponseHelper {
    private static final Logger log = LoggerFactory.getLogger(RequestResponseHelper.class);
    private static final Pattern PATTERN_JAVA_AGENT = Pattern.compile("[Jj]ava/(.+)");

    private TrafficService trafficService;
    private SecurityService securityService;
    private Function<InputStream, CountingInputStream> streamFactory;

    public RequestResponseHelper(TrafficService service, SecurityService securityService) {
        this(service, securityService, CountingInputStream::new);
    }

    public RequestResponseHelper(TrafficService service, SecurityService securityService,
            Function<InputStream, CountingInputStream> streamFactory) {
        trafficService = service;
        this.securityService = securityService;
        this.streamFactory = streamFactory;
    }

    private static void addDebugLog(long actualLength, long resLenght, RepoPath repoPath) {
        if (log.isDebugEnabled()) {
            log.debug("Sending back body response for '{}'. Original resource size: {}, actual size: {}.",
                    repoPath, resLenght, actualLength);
        }
    }

    public void sendBodyResponse(ArtifactoryResponse response, RepoResource res, ResourceStreamHandle handle,
            InternalRequestContext requestContext, boolean triggerDownloadInterceptors) throws IOException {
        // Try to get range headers
        String rangesString = tryToGetRangeHeaderInsensitive(HttpHeaders.RANGE, requestContext.getRequest());
        String ifRangesString = tryToGetRangeHeaderInsensitive(HttpHeaders.IF_RANGE, requestContext.getRequest());

        String originalMimeType = res.getMimeType();
        String artifactPath = res instanceof ZipEntryResource ? ((ZipEntryResource) res).getEntryPath() :
                res.getResponseRepoPath().getPath();
        //modify the returned Content-Type header if the request coming from a browser
        String modifiedMimeType = getModifiedMimeType(artifactPath,
                res.getResponseRepoPath().getRepoKey(), requestContext.getRequest(), response);
        String mimeType = modifiedMimeType != null ? modifiedMimeType : originalMimeType;
        InputStream inputStream = handle.getInputStream();
        // Get Actual file actualLength (Note that it might not be equal to the res.getSize)
        long actualLength = handle.getSize();
        AccessLogger.downloaded(res.getRepoPath());
        addDebugLog(actualLength, res.getSize(), res.getRepoPath());
        // Ensure valid content length
        if (actualLength <= 0) {
            actualLength = !isContentEncoded(res) ? res.getSize() : -1;
        }
        // Get artifact last modified date
        long lastModified = res.getLastModified();
        // Get artifact sha1
        String sha1 = res.getInfo().getSha1();
        String userAgent = getRequestUserAgent(requestContext);
        // Create range aware data for the response
        RangeAwareContext context = createRangeAwareContext(inputStream, actualLength, rangesString, ifRangesString,
                mimeType, lastModified, sha1, userAgent);
        // If request range not satisfiable update response status end return
        if (context.getStatus() == HttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE) {
            response.setHeader(HttpHeaders.CONTENT_RANGE, context.getContentRange());
            response.setStatus(context.getStatus());
            return;
        }
        // Update response with repo resource info
        updateResponseFromRepoResource(response, res);
        // update content length with range aware content length
        response.setContentLength(context.getContentLength());
        // update content type with range aware content type
        response.setContentType(context.getContentType());
        // update headers with range aware headers
        if (context.getContentRange() != null) {
            response.setHeader(HttpHeaders.CONTENT_RANGE, context.getContentRange());
        }
        // Set response status
        if (context.getStatus() > 0) {
            response.setStatus(context.getStatus());
        }
        // Get range aware input stream
        inputStream = context.getInputStream();
        // Get current time for logs
        long start = System.currentTimeMillis();
        setHeaderForNonAscii(response, res);
        setOriginRemotePathHeader(response, res);
        // Send range aware input stream
        sendStream(inputStream, userAgent, response, res.getRepoPath(), res.getSize(),
                context.getContentLength(), triggerDownloadInterceptors);
        if (triggerDownloadInterceptors) {
            fireDownloadTrafficEvent(requestContext, response, res.getRepoPath(), context.getContentLength(), start);
        }
    }

    public String getModifiedMimeType(String filePath, String repoKey, Request request,
            ArtifactoryResponse response) {
        boolean browserRequest = BrowserRequestHelper.isBrowserRequest(request);
        if (browserRequest && !contentBrowsingDisabled(repoKey)) {
            String contentSecurityPolicy = ConstantValues.contentBrowsingContentSecurityPolicy.getString();
            if (StringUtils.isNotEmpty(contentSecurityPolicy)) {
                response.setHeader("Content-Security-Policy", contentSecurityPolicy);
            }
            return BrowserRequestHelper.getMimeType(filePath);
        }
        return null;
    }

    public static String tryToGetRangeHeaderInsensitive(String name, Request request) {
        String header = request.getHeader(name);
        if (StringUtils.isBlank(header)) {
            header = request.getHeader(name.toLowerCase());
        }
        return header;
    }

    /**
     * Tests that the client's "User-Agent" header value does not match the pattern of older Maven versions (prior to
     * and including 2.0.9) that don't support Maven 3 type metadata which includes a list of snapshot versions.
     *
     * @return True if the client "User-Agent" header value does not match the pattern of older Maven versions
     */
    public static boolean clientSupportsM3SnapshotVersions(@NonNull Request request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (StringUtils.isBlank(userAgent)) {
            return true;
        }

        RequestClientType requestClientType = extractRequestClientType(request);
        if (requestClientType == RequestClientType.WHARF_IVY || requestClientType == RequestClientType.APACHE_IVY) {
            return false;
        }

        /*
         * Maven versions older that 2.0.9 (including) sent only the java version in the user agent; that's how we know
         * if the metadata should be filtered for compatibility
         */
        Matcher agentVersionMatcher = PATTERN_JAVA_AGENT.matcher(userAgent);
        return !agentVersionMatcher.matches();
    }

    /**
     * Try to extract the client type of the request
     */
    @NonNull
    public static RequestClientType extractRequestClientType(@NonNull Request request) {
        Enumeration<String> headerValues = request.getHeaderValues(HttpHeaders.USER_AGENT);
        if (headerValues == null) {
            return RequestClientType.UNKNOWN;
        }

        Iterator<String> headersIterator = headerValues.asIterator();
        while (headersIterator.hasNext()) {
            String value = headersIterator.next();
            if (value.startsWith("Debian APT-HTTP/")) {
                return RequestClientType.DEBIAN;
            }
            if (value.startsWith("Wharf Ivy/")) {
                return RequestClientType.WHARF_IVY;
            }
            if (value.startsWith("Apache Ivy/")) {
                return RequestClientType.APACHE_IVY;
            }
            // add more here

        }
        return RequestClientType.UNKNOWN;
    }

    public void sendBodyResponseForChecksum(ArtifactoryResponse response, RepoPath repoPath,
                                            String content, Request request, boolean expirable) throws IOException {
        // Make sure that the response is not empty
        if (content == null) {
            RuntimeException exception = new RuntimeException("Cannot send null response");
            response.sendInternalError(exception, log);
            throw exception;
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            String path = repoPath.getPath();

            String originalMimeType = NamingUtils.getMimeTypeByPathAsString(path);
            //modify the returned Content-Type header if the request coming from a browser
            String modifiedMimeType = getModifiedMimeType(repoPath.getPath(), repoPath.getRepoKey(), request, response);
            String mimeType = modifiedMimeType != null ? modifiedMimeType : originalMimeType;
            response.setContentType(mimeType);
            int bodySize = bytes.length;
            response.setLastModified(System.currentTimeMillis());
            AccessLogger.downloaded(repoPath);
            // Try to get range header
            String rangesString = tryToGetRangeHeaderInsensitive(HttpHeaders.RANGE, request);
            // Create range aware data for the response
            // If-Range is not supported
            String userAgent = HttpUtils.getRequestUserAgent(request);
            RangeAwareContext context =
                    createRangeAwareContext(is, bodySize, rangesString, null, mimeType, -1, null, userAgent);
            // If request range not satisfiable update response status end return
            if (context.getStatus() == HttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE) {
                response.setHeader(HttpHeaders.CONTENT_RANGE, context.getContentRange());
                response.setStatus(context.getStatus());
                return;
            }
            // update content length with range aware content length
            response.setContentLength(context.getContentLength());
            // update content type with range aware content type
            response.setContentType(context.getContentType());
            // update headers with range aware headers
            if (context.getContentRange() != null) {
                response.setHeader(HttpHeaders.CONTENT_RANGE, context.getContentRange());
            }
            // Get range aware input stream
            InputStream inputStream = context.getInputStream();
            // Set response status
            if (context.getStatus() > 0) {
                response.setStatus(context.getStatus());
            }
            if (expirable) {
                noCache(response);
            }
            // Get current time for logs
            long start = System.currentTimeMillis();
            sendStream(inputStream, userAgent, response, repoPath, bodySize, context.getContentLength(), true);
            fireDownloadTrafficEvent(request, response, repoPath, context.getContentLength(), start);
        }
    }

    private void fireDownloadTrafficEvent(Request request, ArtifactoryResponse response, RepoPath repoPath, long size,
            long start) {
        if (!(response instanceof InternalArtifactoryResponse)) {
            String remoteAddress = HttpUtils.getRemoteClientAddress();
            String userAgent = "";
            if (AuthenticationUtils
                    .isXrayAuthenticationUser(securityService.currentUsername(),
                            SecurityContextHolder.getContext().getAuthentication())) {
                userAgent = getUserAgentIfXrayUser(request, securityService.currentUsername(),
                        SecurityContextHolder.getContext().getAuthentication());
                if (trafficService.isActive() && StringUtils.isEmpty(userAgent)) {
                    log.warn("Xray user without user agent {} {} {} {}", repoPath.getId(), size,
                            System.currentTimeMillis() - start, remoteAddress);
                }
            }
            DownloadEntry downloadEntry = new DownloadEntry(
                    repoPath.getId(), size, System.currentTimeMillis() - start, remoteAddress, userAgent);
            trafficService.handleTrafficEntry(downloadEntry);
        }
    }

    private void fireDownloadTrafficEvent(InternalRequestContext requestContext, ArtifactoryResponse response,
            RepoPath repoPath, long size,
            long start) {
        if (!(response instanceof InternalArtifactoryResponse)) {
            String remoteAddress = HttpUtils.getRemoteClientAddress();
            String userAgent = "";
            if (AuthenticationUtils
                    .isXrayAuthenticationUser(securityService.currentUsername(),
                            SecurityContextHolder.getContext().getAuthentication())) {
                userAgent = getUserAgentIfXrayUser(requestContext);
                if (trafficService.isActive() && StringUtils.isEmpty(userAgent)) {
                    log.warn("Xray user without user agent {} {} {} {}", repoPath.getId(), size,
                            System.currentTimeMillis() - start, remoteAddress);
                    userAgent = "Xray/NA";
                }
            }
            DownloadEntry downloadEntry = new DownloadEntry(
                    repoPath.getId(), size, System.currentTimeMillis() - start, remoteAddress, userAgent);
            trafficService.handleTrafficEntry(downloadEntry);
        }
    }

    public void sendHeadResponse(ArtifactoryResponse response, RepoResource res) {
        sendHeadResponse(response, res, null);
    }

    public void sendHeadResponse(ArtifactoryResponse response, RepoResource res, ResourceStreamHandle handle) {
        log.debug("{}: Sending HEAD meta-information", res.getRepoPath());
        if (!isContentLengthSet(response)) {
            long length = (handle != null && handle.getSize() > 0) ? handle.getSize() : res.getSize();
            response.setContentLength(length);
        }
        updateResponseFromRepoResource(response, res);
        response.setContentType(res.getMimeType());
        response.setStatus(response.getStatus()); // we need this to initialize the 'time-of-set-status'
        setHeaderForNonAscii(response, res);
        setOriginRemotePathHeader(response, res);
        response.sendSuccess();
    }

    private void setOriginRemotePathHeader(ArtifactoryResponse response, RepoResource res) {
        String originRemotePath = res.getOriginRemotePath();
        if (isNotBlank(originRemotePath)) {
            response.setHeader(ORIGIN_REMOTE_PATH, originRemotePath);
        }
    }

    private void setHeaderForNonAscii(ArtifactoryResponse response, RepoResource res) {
        String filename = res.getInfo().getName(); // The filename only, NOT the full path
        if (filename != null && !StringUtils.isAsciiPrintable(filename)) {
            final String encodedFilename = UriUtils.encode(filename, StandardCharsets.UTF_8)
                    .replace("+", "%20");
            final String dispositionType = contentBrowsingDisabled(res.getResponseRepoPath().getRepoKey())
                    ? "attachment" : "inline";
            final String contentDisposition = String.format("%s; filename*=UTF-8''%s",
                                                            dispositionType,
                                                            encodedFilename);
            response.setHeader("Content-Disposition", contentDisposition);
        }
    }

    public void sendNotModifiedResponse(ArtifactoryResponse response, RepoResource res) {
        log.debug("{}: Sending NOT-MODIFIED response", res.toString());
        response.setStatus(HttpStatus.SC_NOT_MODIFIED);
        sendEmptyResponse(response, res);
    }

    public void sendEmptyResponse(ArtifactoryResponse response, RepoResource res) {
        log.debug("{}: Sending EMPTY response", res.toString());
        response.setContentLength(0);
        updateResponseFromRepoResource(response, res);
        response.setContentType(res.getMimeType());
        response.flush();
    }

    public void updateResponseForProperties(ArtifactoryResponse response, RepoResource res,
            String content, MediaType mediaType, InternalRequestContext requestContext) throws IOException {
        RepoPath propsDownloadRepoPath;
        String contentType;
        if (mediaType.equals(MediaType.APPLICATION_XML)) {
            propsDownloadRepoPath = RepoPathFactory.create(res.getRepoPath().getRepoKey(),
                    res.getRepoPath().getPath() + "?" + ArtifactRestConstants.PROPERTIES_XML_PARAM);
            contentType = mediaType.getType();
        } else if (mediaType.equals(MediaType.APPLICATION_JSON)) {
            propsDownloadRepoPath = RepoPathFactory.create(res.getRepoPath().getRepoKey(),
                    res.getRepoPath().getPath() + "?" + ArtifactRestConstants.PROPERTIES_PARAM);
            contentType = MT_ITEM_PROPERTIES;
        } else {
            response.sendError(HttpStatus.SC_BAD_REQUEST, "Media Type " + mediaType + " not supported!", log);
            return;
        }
        // props generated xml and json always browsable
        setBasicHeaders(response, res, false);
        noCache(response);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            int bodySize = bytes.length;
            AccessLogger.downloaded(propsDownloadRepoPath);
            // Try to get range header
            String rangesString = tryToGetRangeHeaderInsensitive(HttpHeaders.RANGE, requestContext.getRequest());
            String ifRangesString = tryToGetRangeHeaderInsensitive(HttpHeaders.IF_RANGE, requestContext.getRequest());
            // Get artifact last modified date
            long lastModified = res.getLastModified();
            // Get artifact sha1
            String sha1 = res.getInfo().getSha1();
            String userAgent = getRequestUserAgent(requestContext);
            // Create range aware data for the response
            RangeAwareContext context = createRangeAwareContext(is, bodySize, rangesString, ifRangesString, contentType,
                    lastModified, sha1, userAgent);
            // If request range not satisfiable update response status end return
            if (context.getStatus() == HttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE) {
                response.setHeader(HttpHeaders.CONTENT_RANGE, context.getContentRange());
                response.setStatus(context.getStatus());
                return;
            }
            // update content length with range aware content length
            response.setContentLength(context.getContentLength());
            // update content type with range aware content type
            response.setContentType(context.getContentType());
            // update headers with range aware headers
            if (context.getContentRange() != null) {
                response.setHeader(HttpHeaders.CONTENT_RANGE, context.getContentRange());
            }
            // Set response status
            if (context.getStatus() > 0) {
                response.setStatus(context.getStatus());
            }
            // Get range aware input stream
            InputStream inputStream = context.getInputStream();
            // Get current time for logs
            long start = System.currentTimeMillis();
            // Send stream
            sendStream(inputStream, userAgent, response, propsDownloadRepoPath, bodySize,
                    context.getContentLength(), true);
            // Fire Download traffic event
            fireDownloadTrafficEvent(requestContext, response, propsDownloadRepoPath, context.getContentLength(),
                    start);
        }
    }

    private void sendStream(
            InputStream inputStream,
            String userAgent,
            ArtifactoryResponse response,
            RepoPath repoPath,
            long fullSize,
            long requestedSize,
            boolean performDownloadActions) throws IOException {
        CountingInputStream countingInputStream = streamFactory.apply(inputStream);
        if (performDownloadActions) {
            response.sendStream(countingInputStream,
                    byteCount -> fireDownloadTrafficEventV2(
                            userAgent, response, repoPath, fullSize, requestedSize, byteCount));
        } else {
            response.sendStream(countingInputStream);
        }

    }

    private void updateResponseFromRepoResource(ArtifactoryResponse response, RepoResource res) {
        setBasicHeaders(response, res, contentBrowsingDisabled(res.getResponseRepoPath().getRepoKey()));
        if (res.isExpirable()) {
            noCache(response);
        }
    }

    private boolean isContentLengthSet(ArtifactoryResponse response) {
        return response.getContentLength() != -1;
    }

    private void setBasicHeaders(ArtifactoryResponse response, RepoResource res, boolean contentBrowsingDisabled) {
        response.setLastModified(res.getLastModified());
        RepoResourceInfo info = res.getInfo();
        // set the sha1 as the eTag and the sha1 header
        String sha1 = info.getSha1();
        response.setEtag(sha1);
        response.setSha1(sha1);
        response.setSha2(info.getSha2());
        response.setMd5(info.getMd5());
        response.setRangeSupport("bytes");
        if (response instanceof ArtifactoryResponseBase) {
            String fileName = info.getName();
            if (res instanceof ZipEntryResource zipEntryResource) {
                // The filename is the zip entry inside the zip
                fileName = zipEntryResource.getZipEntryName();
            }
            ((ArtifactoryResponseBase) response).setFilename(fileName);

            // content disposition is not set only for archive resources when archived browsing is enabled
            if (contentBrowsingDisabled) {
                if (ConstantValues.responseDisableContentDispositionFilename.getBoolean()) {
                    ((ArtifactoryResponseBase) response).setContentDispositionAttachment(null);
                } else {
                    ((ArtifactoryResponseBase) response).setContentDispositionAttachment(fileName);
                }
            }
        }
    }

    private void noCache(ArtifactoryResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    private boolean contentBrowsingDisabled(String repoKey) {
        boolean result = true;
        RepositoryService repositoryService = ContextHelper.get().getRepositoryService();
        RepoDescriptor repoDescriptor = repositoryService.repoDescriptorByKey(repoKey);
        if (repoDescriptor != null) {
            if (repoDescriptor instanceof RealRepoDescriptor) {
                result = !((RealRepoDescriptor) repoDescriptor).isArchiveBrowsingEnabled();
            }
        }
        // We return true by default if we couldn't get the flag from the descriptor
        return result;
    }

    private String getUserAgentIfXrayUser(InternalRequestContext requestContext) {
        boolean isXrayUser = AuthenticationUtils
                .isXrayAuthenticationUser(securityService.currentUsername(),
                        SecurityContextHolder.getContext().getAuthentication());
        if (!isXrayUser) {
            return "";
        }

        if (requestContext instanceof NullRequestContext) {
            if (trafficService.isActive()) {
                log.info("Xray user with NullRequestContext");
            }
            return "";
        }

        Request request = requestContext.getRequest();
        if (request == null) {
            return "";
        }

        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (trafficService.isActive() && StringUtils.isEmpty(userAgent)) {
            log.info("Xray user without User-Agent");
        }

        return userAgent;
    }

    public static String getUserAgentIfXrayUser(Request request, String userName, Authentication authentication) {
        boolean isXrayUser = AuthenticationUtils
                .isXrayAuthenticationUser(userName, authentication);
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (isXrayUser && StringUtils.isEmpty(userAgent)) {
            log.info("Xray user without User-Agent");
        }
        return isXrayUser ? userAgent : "";
    }

    private void fireDownloadTrafficEventV2(String userAgent, ArtifactoryResponse response, RepoPath repoPath,
            long fullSize, long requestedSize, long transferredSize) {
        if (!(response instanceof InternalArtifactoryResponse)) {
            String remoteAddress = HttpUtils.getRemoteClientAddress();
            String username = securityService.currentUsername();
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            TrafficEntry.TrafficActor actor = TrafficUtils.getTrafficActor(userAgent, username, authentication);
            TrafficEntry entry = TrafficEntry.builder()
                    .action(TrafficEntry.TrafficAction.DOWNLOAD)
                    .actor(actor)
                    .redirectTarget(TrafficEntry.TrafficTarget.DIRECT)
                    .userAddress(remoteAddress)
                    .repoPath(repoPath.getId())
                    .traceId(MDC.get("uber-trace-id"))
                    .size(fullSize)
                    .requestedSize(requestedSize)
                    .transferredSize(transferredSize)
                    .build();
            trafficService.handleTrafficEntry(entry);
        }
    }

    public static String getRequestUserAgent(InternalRequestContext requestContext) {
        if (requestContext instanceof NullRequestContext) {
            return "";
        }
        Request request = requestContext.getRequest();
        if (request == null) {
            return "";
        }
        return HttpUtils.getRequestUserAgent(request);
    }

    public static String getHeader(Request request, String name) {
        return getHeader(request, name, null);
    }

    public static String getHeader(Request request, String name, String defaultValue) {
        Enumeration<String> values = request.getHeaderValues(name);
        if (values != null && values.hasMoreElements()) {
            return values.nextElement();
        }
        return defaultValue;
    }

}