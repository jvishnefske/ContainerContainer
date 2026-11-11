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

package org.artifactory.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.core.Response;
import lombok.val;
import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.util.InetAddressUtils;
import org.apache.tomcat.util.http.parser.Ranges;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.constant.RestConstants;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.DescriptorAware;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.HeaderByChecksumType;
import org.artifactory.request.HeadersMultiMap;
import org.artifactory.request.RepoRequests;
import org.artifactory.request.Request;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.jfrog.build.api.Build;
import org.jfrog.client.util.PathUtils;
import org.jfrog.common.StreamSupportUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import static io.netty.util.internal.StringUtil.EMPTY_STRING;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.http.HttpStatus.SC_MOVED_TEMPORARILY;
import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.api.context.ArtifactoryContext.CONTEXT_ID_PROP;
import static org.artifactory.common.ConstantValues.artifactoryProductRevision;
import static org.artifactory.common.ConstantValues.artifactoryProductVersion;
import static org.artifactory.util.BasicHttpUtils.encodeQuery;
import static org.jfrog.client.util.PathUtils.addTrailingSlash;
import static org.jfrog.client.util.PathUtils.normalizeUrl;

/**
 * @author yoavl
 */
public abstract class HttpUtils {

    public static final String WEBAPP_URL_PATH_PREFIX = "webapp";
    public static final String UNIFIED_JFROG_PLATFORM_UI = "/ui/";
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    public static final String REPOS_URL_PATH_PREFIX = "/repos/tree/General/";
    private static final String EMPTY_CONTEXT_PATH = "empty";
    private static final String ARTIFACTORY_VERSION_HEADER_PREFIX = "Artifactory/";
    private static final Set<Integer> acceptedStatuses = Set.of(SC_OK, SC_MOVED_TEMPORARILY);
    public static final int MAX_CUSTOM_HEADER_NAME_LENGTH = 256;
    public static final int MAX_CUSTOM_HEADER_VALUE_LENGTH = 4096;
    private static final Logger log = LoggerFactory.getLogger(HttpUtils.class);
    private static final Pattern RFC_7230_HEADER_NAME_TOKEN =
            Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");
    private static final Pattern REGEX_SPECIAL_CHARS = Pattern.compile("[.*+?\\[\\]()^$|\\\\]");
    private static final UrlValidator OVERRIDE_BASE_URL_VALIDATOR = new UrlValidator("http", "https");

    private static String userAgent;

    private HttpUtils() {
        // utility class
    }

    public static String getArtifactoryUserAgent() {
        if (userAgent == null) {
            String artifactoryVersion = artifactoryProductVersion.getString();
            if (artifactoryVersion.startsWith("$") || artifactoryVersion.endsWith("SNAPSHOT")) {
                artifactoryVersion = "development";
            }
            userAgent = String.format(ARTIFACTORY_VERSION_HEADER_PREFIX + "%s %s", artifactoryVersion,
                    artifactoryProductRevision.getString());
        }
        return userAgent;
    }

    public static boolean isValidRfc7230HeaderName(String headerName) {
        return StringUtils.isNotBlank(headerName) && RFC_7230_HEADER_NAME_TOKEN.matcher(headerName).matches();
    }

    public static boolean hasRegexSpecialChars(String value) {
        return StringUtils.isNotEmpty(value) && REGEX_SPECIAL_CHARS.matcher(value).find();
    }

    /**
     * Extracts the version from the version header
     * Note: was tested only on the User-Agent header
     *
     * @return the extracted version or empty optional if version is in bad format
     */
    public static Optional<ArtifactoryServiceVersion> extractVersionFromHeader(String versionHeader) {
        if (StringUtils.isBlank(versionHeader) || !versionHeader.startsWith(ARTIFACTORY_VERSION_HEADER_PREFIX)) {
            return Optional.empty();
        }

        String serverVersion = StringUtils.removeStart(versionHeader, ARTIFACTORY_VERSION_HEADER_PREFIX);
        if (serverVersion.startsWith("development")) {
            return Optional.ofNullable(ArtifactoryServiceVersion.getCurrent());
        }
        try {
            String[] versionParts = serverVersion.split(" ");
            String version = versionParts[0];
            long revision;
            if (versionParts.length > 1) {
                revision = Long.parseLong(versionParts[1]);
            } else {
                revision = ArtifactoryServiceVersionProvider.getRevisionFromVersion(version);
            }
            return Optional.of(ArtifactoryServiceVersionProvider.get(version, revision));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public static String getRequestUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return StreamSupportUtils.enumerationToStream(request.getHeaders(HttpHeaders.USER_AGENT))
                .findFirst()
                .orElse(null);
    }

    public static String getRequestUserAgent(@Nonnull Request request) {
        return StreamSupportUtils.enumerationToStream(request.getHeaderValues(HttpHeaders.USER_AGENT))
                .findFirst()
                .orElse(null);
    }

    /**
     * Reset the cached Artifactory user agent string (required after upgrade)
     */
    public static void resetArtifactoryUserAgent() {
        userAgent = null;
    }

    public static String getRemoteClientAddress(@Nonnull HttpServletRequest request) throws URISyntaxException {
        //Check if there is a remote address coming from a proxied request
        //(http://httpd.apache.org/docs/2.2/mod/mod_proxy.html#proxypreservehost)
        return validateRemoteClientAddress(extractXForwardedForHeader(request).orElse(request.getRemoteAddr()));
    }

    public static String validateRemoteClientAddress(@Nonnull String remoteAddress) throws URISyntaxException {
        if (isValidIp(remoteAddress)) {
            return remoteAddress;
        }
        if (isValidIpWithPort(remoteAddress)) {
            return remoteAddress;
        }
        throw new URISyntaxException("Remote address is invalid", remoteAddress);
    }

    private static boolean isValidIp(String address) {
        return StringUtils.isBlank(address) || InetAddressUtils.isIPv4Address(address) ||
                InetAddressUtils.isIPv6Address(removeBrackets(address));
    }

    private static boolean isValidIpWithPort(String address) throws URISyntaxException {
        try {
            val uri = new URI("scheme://" + address);
            val portValid = uri.getPort() != -1;
            if (!portValid) {
                return false;
            }
            var host = uri.getHost();
            if (host != null) {
                host = removeBrackets(host);
            }
            return isValidIp(host);
        } catch (URISyntaxException e) {
            throw new URISyntaxException("Remote address contains invalid characters", e.getMessage());
        }
    }

    private static String removeBrackets(String host) {
        val bracketed = host.startsWith("[") && host.endsWith("]");
        return bracketed ?
                host.replace("[", "").replace("]", "") :
                host;
    }

    public static Optional<String> extractXForwardedForHeader(@Nonnull HttpServletRequest request) {
        String header = request.getHeader(X_FORWARDED_FOR);
        //Header X_FORWARDED_FOR might contain multiple entries <client>, <proxy1>, <proxy2> -> take the first (client)
        return (StringUtils.isNotBlank(header)) ?
                Optional.of(new StringTokenizer(header, ",").nextToken()) : Optional.empty();
    }

    public static String getArtifactoryUrlFromRequestIfPresent() {
        HttpServletRequest request = RequestThreadLocal.getRequest();
        if (request != null) {
            String url = HttpUtils.getServletContextUrl(request);
            if (StringUtils.isNotBlank(url)) {
                return url;
            }
        }
        return "";
    }

    public static String getServletContextUrl(HttpServletRequest httpRequest) {
        return getServletContextUrl(ContextHelper.get(), httpRequest);
    }

    /**
     * Resolves the servlet context URL with preferred resolution to an URL with direct access to Artifactory (if
     * possible).
     * In case the URL detected contains a port number that is the Router port, it is switched to Artifactory's port.
     * In other cases - URL is Artifactory's port / behind reverse proxy: we do not switch ports.
     *
     * For example (Artifactory's port here is 8081, Router is 8082):
     * http://acme.me/      --> http://acme.me/
     * http://acme.me:7000/ --> http://acme.me:7000/
     * http://acme.me:8081/ --> http://acme.me:8081/
     * http://acme.me:8082/ --> http://acme.me:8081/
     */
    public static String getServletContextUrlArtifactoryPreferred(HttpServletRequest httpRequest) {
        final ArtifactoryContext artifactoryContext = ContextHelper.get();
        String servletContextUrl = getServletContextUrl(artifactoryContext, httpRequest);
        try {
            URL url = new URL(servletContextUrl);
            int routerPort = ArtifactoryHome.get().getSysConfig().helper().getRouterExternalPort();
            // NOTE: In case this method usage will be enlarged in the future - this call should be cached.
            int artifactoryPort = artifactoryContext.beanForType(CentralConfigService.class).getListeningPort();
            if (url.getPort() == -1 || artifactoryPort == -1 || url.getPort() != routerPort) {
                // Nothing to change, as the request is either direct to artifactory/behind reverse proxy
                return servletContextUrl;
            }
            // The request is to the router's port, switching directly to Artifactory's port
            URL alteredUrl = new URL(url.getProtocol(), url.getHost(), artifactoryPort, url.getPath());
            log.trace("Changed URL port of '{}' from {} to {}", servletContextUrl, url.getPort(), artifactoryPort);
            return alteredUrl.toExternalForm();
        } catch (MalformedURLException e) {
            log.error("Failed to resolve URL: {}", e.getMessage(), e);
            return servletContextUrl;
        }
    }

    /**
     * RTFACT-19523
     *
     * - new Platform customers should configure their base url *without* the '/artifactory'
     * - old customers may upgrade to the platform while having the '/artifactory' sufix in their base url
     * - old customers may also upgrade to the platform while having '/someName' sufix in their base url
     *
     * in either case we need to return a url that eventually points to Artifactory
     * an exception we make is where we need to return a url that doesn't
     * contain the contextPath at all. this is the case where we want to return a url that
     * points directly to the platform. examples are OAuth redirect-uri and other UI related responses
     */
    public static String getServletContextUrl(ArtifactoryContext artifactoryContext, HttpServletRequest httpRequest) {
        log.trace("Resolving Artifactory base url");
        String jfrogOverrideBaseUrl = httpRequest.getHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL);
        if (isTrustedOverrideBaseUrl(jfrogOverrideBaseUrl)) {
            log.trace("Found {} header, {}", ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL, jfrogOverrideBaseUrl);
            return addTrailingSlash(jfrogOverrideBaseUrl) +
                    PathUtils.trimLeadingSlashes(artifactoryContext.getContextPath());
        }

        String artOverrideBaseUrl = httpRequest.getHeader(ArtifactoryRequest.ARTIFACTORY_OVERRIDE_BASE_URL);
        if (isTrustedOverrideBaseUrl(artOverrideBaseUrl)) {
            log.trace("Found {} header, {}", ArtifactoryRequest.ARTIFACTORY_OVERRIDE_BASE_URL, artOverrideBaseUrl);
            // original artifactory request url overrides request and base url
            return artOverrideBaseUrl;
        }
        CentralConfigService centralConfigService = artifactoryContext.getCentralConfig();
        String baseUrl;
        String requestURI = httpRequest.getRequestURI();
        if (StringUtils.isNotBlank(requestURI) && requestURI.contains("api/v1/service_trust/pairing/federated-repo")) {
            baseUrl = centralConfigService.getDescriptor().getFederatedRepoUrlBaseOrDefault();
        } else {
            baseUrl = centralConfigService.getDescriptor().getUrlBase();
        }
        String contextPath = getContextPathFromBaseUrl(baseUrl);
        String url = getArtifactoryBaseUrl(httpRequest, baseUrl);
        if (url != null) {
            log.trace("Getting base url from configuration, {}", url);
            if (isBlank(contextPath)) {
                contextPath = ConstantValues.contextPath.getString();
                return PathUtils.trimTrailingSlashes(PathUtils.trimTrailingSlashes(url) + contextPath);
            } else {
                return PathUtils.trimTrailingSlashes(url);
            }
        }

        return getServerUrl(httpRequest) + httpRequest.getContextPath();
    }

    private static String getContextPathFromBaseUrl(String url) {
        url = PathUtils.trimTrailingSlashes(url);
        if (isBlank(url)) {
            return null;
        }
        if (!url.contains("/")) {
            return null;
        }
        url = url.replaceFirst("http://|https://", "");
        int contextPathStart = url.lastIndexOf('/');
        if (contextPathStart == -1) {
            return null;
        }
        return url.substring(contextPathStart + 1);
    }

    /**
     * @return Platform base URL without trailing slash
     */
    public static String getPlatformServletContextUrl(HttpServletRequest httpRequest) {
        return PathUtils.trimTrailingSlashes(getPlatformServletContextUrl(ContextHelper.get(), httpRequest));
    }

    private static String getPlatformServletContextUrl(ArtifactoryContext artifactoryContext,
            HttpServletRequest httpRequest) {
        log.trace("Resolving mothership base url...");
        String jfrogOverrideBaseUrl = httpRequest.getHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL);
        if (isTrustedOverrideBaseUrl(jfrogOverrideBaseUrl)) {
            log.trace("Found {} header, {}", ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL, jfrogOverrideBaseUrl);
            return addTrailingSlash(jfrogOverrideBaseUrl);
        }
        CentralConfigService centralConfigService = artifactoryContext.getCentralConfig();
        int listeningPort = centralConfigService.getListeningPort();
        String baseUrl = centralConfigService.getDescriptor().getJFrogUrlBase(listeningPort);
        String url = getArtifactoryBaseUrl(httpRequest, baseUrl);
        if (url != null) {
            log.trace("Getting base url from configuration, {}", url);
            return url;
        }

        String serverUrl = PathUtils.trimTrailingSlashes(getServerUrl(httpRequest));
        if (serverUrl.endsWith(":" + listeningPort)) {
            serverUrl = StringUtils.replace(serverUrl, ":" + listeningPort,
                    ":" + ArtifactoryHome.get().getSysConfig().helper().getRouterExternalPort());
        }
        return serverUrl;
    }

    /**
     * 1. In cases of override (i.e customer name as context path in SaaS) - return the context path with a leading
     * slash
     * 2. In case of override to an empty context path - return an empty String
     * 3. Default - return "/artifactory"
     *
     * @return Custom context path
     */
    public static String getOverrideContextPath() {
        try {
            String contextPath = PathUtils.trimLeadingSlashes(ConstantValues.overrideContextPath.getString());
            contextPath = PathUtils.trimTrailingSlashes(contextPath);
            contextPath = EMPTY_CONTEXT_PATH.equalsIgnoreCase(contextPath) ? "" : "/" + contextPath;
            return contextPath;
        } catch (IllegalStateException e) {
            log.error("Unable to get override context path, {}", e.getMessage(), e);
        }
        return "/artifactory";
    }

    /**
     * Returns an uri with a deduplicated double slashes. for example http://example.com//test -> http://example.com/test
     * @param uri the uri to deduplicate
     * @return a deduplicated uri
     */
    public static String deduplicateDoubleSlashesFromUri(String uri) {
        String[] parts = uri.split("://");
        if (parts.length < 2) {
            return uri;
        }
        String scheme = parts[0] + "://";
        String apiPath = parts[1].replaceAll("//+", "/");
        return scheme+apiPath;
    }

    private static String getArtifactoryBaseUrl(HttpServletRequest httpRequest, String baseUrl) {
        if (!StringUtils.isEmpty(baseUrl)) {
            String scheme = httpRequest.getScheme();
            if (baseUrl.startsWith(scheme)) {
                return baseUrl;
            } else {
                int idx = baseUrl.indexOf("://");
                if (idx > 0) {
                    return scheme + "://" + baseUrl.substring(idx + 3);
                } else {
                    return scheme + "://" + baseUrl;
                }
            }
        }
        return null;
    }

    public static String getRestApiUrl(HttpServletRequest request) {
        return getServletContextUrl(request) + "/" + RestConstants.PATH_API;
    }

    public static String getServerUrlFromContext() {
        HttpServletRequest request = RequestThreadLocal.getRequest();
        return getServerUrl(request);
    }

    private static String getServerUrl(HttpServletRequest httpRequest) {
        String forwardedHostUrl = getForwardedHostUrl(httpRequest);
        if (forwardedHostUrl != null) {
            return forwardedHostUrl;
        }
        log.trace("Resolving base url from request");
        int port = httpRequest.getServerPort();
        String scheme = httpRequest.getScheme();
        if (isDefaultPort(scheme, port)) {
            return scheme + "://" + httpRequest.getServerName();
        }
        return scheme + "://" + httpRequest.getServerName() + ":" + port;
    }

    private static String getForwardedHostUrl(HttpServletRequest httpRequest) {
        String forwardedHost = httpRequest.getHeader("x-forwarded-host");
        String forwardedScheme = httpRequest.getHeader("x-forwarded-proto");
        if (StringUtils.isNotBlank(forwardedHost) && StringUtils.isNotBlank(forwardedScheme)) {
            String forwardedHostUrl = forwardedScheme + "://" + forwardedHost;
            if (isTrustedOverrideBaseUrl(forwardedHostUrl)) {
                log.trace("Found {} header, {}", "x-forwarded-host", forwardedHost);
                return forwardedHostUrl;
            }
        }
        return null;
    }

    /**
     * Validates override base URL headers before they are used to construct download and registry URLs.
     * Uses {@link UrlValidator} (http/https, host) and additionally rejects fragment/query injection
     * (e.g. {@code https://evil.com#}) which {@link UrlValidator} does not cover.
     */
    public static boolean isTrustedOverrideBaseUrl(@Nullable String overrideBaseUrl) {
        if (StringUtils.isBlank(overrideBaseUrl)) {
            return false;
        }
        String trimmed = overrideBaseUrl.trim();
        try {
            OVERRIDE_BASE_URL_VALIDATOR.validate(trimmed);
            URI uri = new URI(trimmed);
            return uri.getUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (UrlValidator.UrlValidationException | URISyntaxException e) {
            log.warn("Rejected untrusted override base URL: {}", e.getMessage());
            return false;
        }
    }

    private static boolean isDefaultPort(String scheme, int port) {
        switch (port) {
            case 80:
                return "http".equalsIgnoreCase(scheme);
            case 443:
                return "https".equalsIgnoreCase(scheme);
            default:
                return false;
        }
    }

    public static String getSha1Checksum(ArtifactoryRequest request) {
        String sha1Header = request.getHeader(ArtifactoryRequest.CHECKSUM_SHA1);
        return sha1Header != null ? sha1Header : getChecksum(ChecksumType.sha1, request);
    }

    public static String getSha256Checksum(ArtifactoryRequest request) {
        String sha2Header = request.getHeader(ArtifactoryRequest.CHECKSUM_SHA256);
        return sha2Header != null ? sha2Header : getChecksum(ChecksumType.sha256, request);
    }

    public static String getMd5Checksum(ArtifactoryRequest request) {
        String md5Header = request.getHeader(ArtifactoryRequest.CHECKSUM_MD5);
        return md5Header != null ? md5Header : getChecksum(ChecksumType.md5, request);
    }

    private static String getChecksum(ChecksumType checksumType, ArtifactoryRequest request) {
        String checksumHeader = request.getHeader(ArtifactoryRequest.CHECKSUM);
        if (StringUtils.isNotBlank(checksumHeader) && checksumHeader.length() == checksumType.length()) {
            return checksumHeader;
        }
        return null;
    }

    public static boolean isExpectedContinue(ArtifactoryRequest request) {
        String expectHeader = request.getHeader("Expect");
        if (isBlank(expectHeader)) {
            return false;
        }
        // some clients make the C lowercase even when passed uppercase
        return expectHeader.contains("100-continue") || expectHeader.contains("100-Continue");
    }

    public static String getContextId(ServletContext servletContext) {
        String contextId = Objects.toString(servletContext.getAttribute(CONTEXT_ID_PROP), null);
        if (isBlank(contextId)) {
            contextId = PathUtils.trimLeadingSlashes(servletContext.getContextPath());
        }
        return contextId;
    }

    /**
     * @param response to check the status of
     * @return True if the response is not empty (200 or 302 or 304 (depending on config)
     */
    public static boolean isResponseOkOrRedirectedMovedTemporarilyCode(@Nullable Response response) {
        return response != null &&
                (acceptedStatuses.contains(response.getStatus()) || response.getStatus() == HttpStatus.SC_NOT_MODIFIED);
    }


    /**
     * Calculate a unique id for the VM to support Artifactories with the same ip (e.g. across NATs)
     */
    public static String getHostId() {
        return ArtifactoryHome.get().getHostId();
    }

    /**
     * @param response The response to get the body from
     * @return Returns the response body input stream or null is there is none.
     */
    @Nullable
    public static InputStream getResponseBody(HttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        return entity == null ? null : entity.getContent();
    }


    /**
     * Finds the host part and returns whatever comes after, if not empty. If there is a path to this address, it is
     * always returned with a leading slash.
     *
     * @param address - the full URL to encode
     * @return the address path, or an empty string if the address has no path
     */

    static String getRequestPath(String address) {
        String hostPathDelimiter;
        String path;
        if (!address.contains("://") && !address.startsWith("/")) {
            // In a scheme-less address the first forward slash is the delimiter between the host and the path.
            // Hostnames, IPv4 & IPv6 addresses should never contains a slash.
            hostPathDelimiter = "/";
            path = "/" + StringUtils.substringAfter(address, hostPathDelimiter);
        } else {
            hostPathDelimiter = "://";
            String host = StringUtils.substringBetween(address, hostPathDelimiter, "/");
            if (isBlank(host)) {
                // no host, no path
                log.debug("Address '{}' is invalid, returning empty path", address);
                return "";
            }
            // scheme://host/{path}
            path = StringUtils.substringAfter(address, host);
        }
        if (isBlank(path)) {
            return "";
        }
        return path;
    }

    private static String getAddressHost(String address) {
        String hostPathDelimiter;
        String host;
        if (!address.contains("://")) {
            // In a scheme-less address the first forward slash is the delimiter between the host and the path.
            // Hostnames, IPv4 & IPv6 addresses should never contains a slash.
            hostPathDelimiter = "/";
            host = StringUtils.substringBefore(address, hostPathDelimiter);
        } else {
            hostPathDelimiter = "://";
            host = StringUtils.substringBetween(address, hostPathDelimiter, "/");
        }
        if (isBlank(host)) {
            log.warn("No host found in address: '{}', returning empty string", address);
            return "";
        }
        return host;
    }

    /**
     * Encodes only the path of a given full URL using {@link HttpUtils#getRequestPath(String)}
     * and {@link BasicHttpUtils#encodeQuery(String)}. The scheme, hostname and port are left untouched.
     * Unlike {@link BasicHttpUtils#encodeQuery(String)}, this method is compatible with both IPv6 and IPv4 addresses.
     *
     * @param url - the full URL to encode
     */

    public static String encodeUrl(String url) {
        String requestPath = getRequestPath(url);
        if (requestPath.equals("/") || requestPath.equals("")) {
            return url;
        }
        return url.substring(0, url.length() - requestPath.length()) + encodeQuery(requestPath);
    }

    /**
     * @param port the port
     * @return true if the given port is an implicit port (443 or 80)
     */

    public static boolean isImplicitPort(String port) {
        return port.equals("443") || port.equals("80");
    }

    /**
     * Heuristically detect whether the given address can represent an IPv6 address.
     * This method does NOT guarantee that the input represents a valid IPv6 address, it merely checks whether the
     * address contains at least two colons in it, or three if the hostname contains auth information.
     *
     * @param address - the address
     * @return true if the given address could represent an IPv6 address
     */

    public static boolean isIPv6Address(String address) {
        if (getAddressHost(address).contains("@")) {
            // if the hostname itself contains the commercial at sign, this URL has auth info embedded.
            return address.matches(".*:.*:.*:.*");
        }
        return address.matches(".*:.*:.*");
    }

    /**
     * Removes leading/trailing square brackets if needed.
     *
     * @param address - the address
     * @return the address without the leading/trailing square brackets, or the original address.
     */

    public static String trimSquareBracketsIfNeeded(String address) {
        if (address.charAt(0) == '[' && address.charAt(address.length() - 1) == ']') {
            address = address.substring(1, address.length() - 1);
        }
        return address;
    }

    /**
     * Additional to the traditional escaping and encoding string regarded as within the path component of an
     * URI with the default protocol charset it also encodes colon character.
     * Do not encode full url like http://bla/bla, the colon will be converted to %3A
     */
    public static String encodeWithinPath(String unescaped) {
        try {
            unescaped = URIUtil.encodeWithinPath(unescaped);
            unescaped = unescaped.replace(":", "%3A");
            return URIUtil.encodeWithinPath(unescaped);
        } catch (HttpException e) {
            // Nothing to do here, we will return the un-escaped value.
            log.warn("Could not encode path '{}' with UTF-8 charset, returning the un-escaped value.", unescaped);
        }
        return unescaped;
    }

    /**
     * Additional to the traditional escaping and decoding string regarded as within the path component of an
     * URI with the default protocol charset it also decodes colon character.
     */
    public static String decodeUri(String encodedUri) {
        try {
            return URIUtil.decode(encodedUri, "UTF-8");
        } catch (HttpException e) {
            // Nothing to do here, we will return the un-escaped value.
            log.warn("Could not decode uri '{}' with UTF-8 charset, returning the encoded value.", encodedUri);
        }
        return encodedUri;
    }

    public static String decodeUrlWithURLDecoder(String url) {
        try {
            return URLDecoder.decode(url, CharEncoding.UTF_8);
        } catch (UnsupportedEncodingException e) {
            log.error("Could not decode uri '{}' with UTF-8 charset, returning the encoded value.", url);
        }
        return url;
    }

    /**
     * encodes to application/x-www-form-urlencoded
     * beware of encoding full url, ex: http://bla/bla.txt -> http%3A%2F%2Fbla%2Fbla.txt
     * test test -> test+test
     * test:test -> test%3Atest
     * do not use when encoding for native browsing, Ivi client expect space char to be encoded as %20 and not `+`
     * when resolving dependencies
     */
    public static String encodeUrlWithURLEncoder(String url) {
        try {
            return URLEncoder.encode(url, CharEncoding.UTF_8);
        } catch (UnsupportedEncodingException e) {
            log.error("Could not encode uri '{}' with UTF-8 charset, returning the original value.", url);
        }
        return url;
    }

    /**
     * Removes the query parameters from the given url
     *
     * @param url URL string with query parameters, e.g. "http://hello/world?lang=java&run=1"
     * @return new string object without the query parameters, e.g. "http://hello/world". If no query elements found the
     * original string is returned.
     */
    public static String stripQuery(String url) {
        int i = url.indexOf('?');
        if (i > -1) {
            return url.substring(0, i);
        } else {
            return url;
        }
    }

    public static String getHeaderIgnoreCase(Map<String, String> headersMap, String headerName) {
        if (headersMap == null || headerName == null) {
            return null;
        }
        return headersMap.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase(headerName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    public static String adjustRefererValue(Map<String, String> headersMap, String headerVal) {
        //Append the artifactory user agent to the referer
        if (headerVal == null) {
            //Fallback to host
            headerVal = getHeaderIgnoreCase(headersMap, HttpHeaders.HOST);
            if (headerVal == null) {
                //Fallback to unknown
                headerVal = "UNKNOWN";
            }
        }
        if (!headerVal.startsWith("http")) {
            headerVal = "http://" + headerVal;
        }
        try {
            java.net.URL uri = new java.net.URL(headerVal);
            //Only use the uri up to the path part
            headerVal = uri.getProtocol() + "://" + uri.getAuthority();
        } catch (MalformedURLException e) {
            //Nothing
        }
        headerVal += "/" + HttpUtils.getArtifactoryUserAgent();
        return headerVal;
    }

    /**
     * Extracts the content length from the response header, or return -1 if the content-length field was not found.
     *
     * @param response The response
     * @return Content length in bytes or -1 if header not found
     */
    public static long getContentLength(HttpResponse response) {
        if (ConstantValues.useRangeForReplacedGet.getBoolean() && response.containsHeader(HttpHeaders.CONTENT_RANGE)) {
            return getContentLengthForRangeRequest(response);
        }
        Header contentLengthHeader = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLengthHeader != null) {
            return extractContentLengthFromHeader(contentLengthHeader.getValue());
        } else {
            return -1;
        }
    }

    /**
     * @param contextUrl      is of syntax ip:port/artifactory (i.e 127.0.0.1:8084/artifactory)
     * @param artifactoryPort if not -1, will replace the port with the param value
     * @return ip:port, i.e 127.0.0.1:8084
     */
    public static String getServerAndPortFromContext(String contextUrl, int artifactoryPort) {
        String[] contextUrlParts = contextUrl.split("/");
        if (contextUrlParts.length >= 2) {
            return replacePortIfNeeded(artifactoryPort, contextUrlParts[0]);
        } else {
            return EMPTY_STRING;
        }
    }

    /**
     * @param contextUrl      is of syntax ip:port/artifactory (i.e 127.0.0.1:8084/artifactory)
     * @param artifactoryPort if not -1, will replace the port with the param value
     * @return http://ip:port, i.e http://127.0.0.1:8084
     */
    public static String getApacheServerAndPortFromContext(String contextUrl, int artifactoryPort) {
        String[] contextUrlParts = contextUrl.split("/");
        if (contextUrlParts.length >= 2) {
            return "http://" + replacePortIfNeeded(artifactoryPort, contextUrlParts[0]);
        } else {
            return EMPTY_STRING;
        }
    }

    private static String replacePortIfNeeded(int artifactoryPort, String contextUrlPart) {
        int indexOfPort = contextUrlPart.indexOf(':');
        if (artifactoryPort == -1 || indexOfPort == -1) {
            return contextUrlPart;
        }
        String ip = contextUrlPart.substring(0, indexOfPort);
        log.trace("Changed URL port of '{}' to {}", contextUrlPart, artifactoryPort);
        return ip + ":" + artifactoryPort;
    }

    /**
     * Return content length as long (required for uploaded files > 2GB).
     * The servlet api can only return this as int.
     *
     * @param request The request
     * @return The content length in bytes or -1 if not found
     */
    public static long getContentLength(HttpServletRequest request) {
        return extractContentLengthFromHeader(request.getHeader(HttpHeaders.CONTENT_LENGTH));
    }

    private static long extractContentLengthFromHeader(String lengthHeader) {
        long contentLength;
        if (lengthHeader != null) {
            try {
                contentLength = Long.parseLong(lengthHeader);
            } catch (NumberFormatException e) {
                log.trace("Bad Content-Length value {}", lengthHeader);
                contentLength = -1;
            }
        } else {
            contentLength = -1;
        }
        return contentLength;
    }

    public static void sendErrorResponse(HttpServletResponse response, int statusCode, String message)
            throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(statusCode);
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        ErrorResponse errorResponse = new ErrorResponse(statusCode, message);
        response.getWriter().write(mapper.writeValueAsString(errorResponse));
    }

    public static boolean isAbsolute(String url) {
        try {
            URI uri = new URIBuilder(escapeColonSymbol(url)).build();
            return uri.isAbsolute();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static String getRemoteClientAddress() {
        if (ConstantValues.test.getBoolean()) {
            return "127.0.0.1";
        }
        String remoteClientAddress = RequestThreadLocal.getClientAddress();
        return Objects.requireNonNullElse(remoteClientAddress, "");
    }

    public static String resolveResponseRemoteAddress(CloseableHttpResponse response) {
        try {
            Field connHolderField = response.getClass().getDeclaredField("connHolder");
            log.trace("connHolderField {}", connHolderField);
            connHolderField.setAccessible(true);
            Object connHolder = connHolderField.get(response);
            log.trace("connHolder {}", connHolder);
            Field managedConnField = connHolder.getClass().getDeclaredField("managedConn");
            log.trace("managedConnField {}", managedConnField);
            managedConnField.setAccessible(true);
            ManagedHttpClientConnection managedConn = (ManagedHttpClientConnection) managedConnField.get(
                    connHolder);
            log.trace("managedConn {}", managedConn);
            Socket socket = managedConn.getSocket();
            log.trace("socket {}", socket);
            InetAddress inetAddress = socket.getInetAddress();
            log.trace("inetAddress {}", inetAddress);
            String hostAddress = inetAddress.getHostAddress();
            if (hostAddress == null) {
                log.trace("Host address is null. Empty resolved address will be returned");
            }
            return hostAddress == null ? StringUtils.EMPTY : hostAddress;
        } catch (Throwable throwable) {
            log.trace("Error resolving response remote address. Empty resolved address will be returned", throwable);
            return StringUtils.EMPTY;
        }
    }

    public static String resolveURIAddress(URI uriAddress) {
        try {
            return InetAddress.getByName(uriAddress.getHost()).getHostAddress();
        } catch (UnknownHostException e) {
            log.error("Error resolving response remote address: '{}', Empty resolved address will be returned",
                    uriAddress);
            return StringUtils.EMPTY;
        }
    }

    public static String createBuildInfoLink(Build build) {
        String artifactoryUrl = ContextHelper.get().beanForType(
                CentralConfigService.class).getDescriptor().getServerUrlForEmail();
        if (isBlank(artifactoryUrl)) {
            return build.getName() + ":" + build.getNumber();
        } else {
            try {
                String href = String.join("/",
                        PathUtils.trimTrailingSlashes(artifactoryUrl),
                        PathUtils.trimSlashes(UNIFIED_JFROG_PLATFORM_UI),
                        "builds",
                        // Do a manual "encoding" of spaces for the build name. This is due to the fact that if the mail
                        // is sent to a Gmail account it will automatically insert '+' for every space, and not its
                        //'%20'
                        // hex representation, this will cause a broken link. see more here:
                        // http://www.google.fr/support/forum/p/gmail/thread?tid=53a5c616a0324d96&hl=en
                        build.getName().replace(" ", "%20"),
                        build.getNumber());

                return "<a href=\"" + href + "\"" + " target=\"blank\">" + build.getName() + ":" + build.getNumber()
                        + "</a>";
            } catch (Exception e) {
                return build.getName() + ":" + build.getNumber();
            }
        }
    }

    public static String createLinkToBrowsableArtifact(RepoPath repoPath, String linkLabel) {
        String artifactoryUrl = PathUtils
                .trimTrailingSlashes(ContextHelper.get().beanForType(CentralConfigService.class).getDescriptor()
                        .getServerUrlForEmail());
        if (isBlank(artifactoryUrl)) {
            return linkLabel;
        } else {
            String url =
                    artifactoryUrl + PathUtils.trimTrailingSlashes(UNIFIED_JFROG_PLATFORM_UI) + REPOS_URL_PATH_PREFIX
                            + encodeQuery(repoPath.toPath());
            return "<a href=" + url + " target=\"blank\"" + ">" + linkLabel + "</a>";
        }
    }

    /**
     * Extracts session access time if session exist,
     * if session is null, returns System.currentTimeMillis
     *
     * @return session access time
     */
    public static long getSessionAccessTime(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        return session != null ?
                (
                        session.getLastAccessedTime() == 0 ?
                                session.getCreationTime()
                                :
                                session.getLastAccessedTime()
                )
                :
                System.currentTimeMillis();
    }

    /**
     * Checks whether request targeted for changePassword api
     * or if admin triggered "unExpirePasswordForAllUsers" action
     *
     * @return true if request targeted for "changePassword"
     */
    public static boolean isChangePasswordRequest(ServletRequest servletRequest) {
        String uri = ((HttpServletRequest) servletRequest).getRequestURI();
        String invokedMethod = PathUtils.getLastPathElement(uri);
        return "changePassword".equals(invokedMethod) || "unExpirePasswordForAllUsers".equals(invokedMethod);
    }

    public static boolean isValidUrl(String url) {
        try {
            new URL(url);
            String urlSeparator = "://";
            return url.contains(urlSeparator);
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public static long getLastModified(HttpResponse response) {
        Header lastModifiedHeader = response.getFirstHeader(HttpHeaders.LAST_MODIFIED);
        if (lastModifiedHeader == null) {
            return -1;
        }
        String lastModifiedString = lastModifiedHeader.getValue();
        lastModifiedString = normalizeDate(lastModifiedString);
        Date lastModifiedDate = DateUtils.parseDate(lastModifiedString);
        if (lastModifiedDate != null) {
            return lastModifiedDate.getTime();
        } else {
            log.warn("Unable to parse Last-Modified header : {}", lastModifiedString);
            return System.currentTimeMillis();
        }
    }

    private static String normalizeDate(String lastModifiedString) {
        if (lastModifiedString != null) {
            lastModifiedString = lastModifiedString.trim();
            lastModifiedString = Strings.CS.removeStart(lastModifiedString, "\"");
            lastModifiedString = Strings.CS.removeEnd(lastModifiedString, "\"");
        }
        return lastModifiedString;
    }

    public static String getEtag(HttpResponse response) {
        Header etagHeader = response.getFirstHeader(HttpHeaders.ETAG);
        if (etagHeader == null) {
            return "";
        }
        return etagHeader.getValue();
    }

    public static Set<ChecksumInfo> getChecksums(HttpResponse response) {
        Set<ChecksumInfo> remoteChecksums = Sets.newHashSet();
        addChecksumIfExists(response, ChecksumType.md5, remoteChecksums);
        addChecksumIfExists(response, ChecksumType.sha1, remoteChecksums);
        addChecksumIfExists(response, ChecksumType.sha256, remoteChecksums);
        return remoteChecksums;
    }

    /**
     * Normalized base url from central config with attached override context path
     * and attached repo key. Returns null in case empty baseUrl or repoKey.
     *
     * @return base url with override context path and repo key
     */
    public static String getBaseUrlWithOverrideContextPathAndRepoKey(String repoKey) {
        return StringUtils.isEmpty(repoKey) ? null : Optional.ofNullable(ContextHelper.get())
                .map(ArtifactoryContext::getCentralConfig)
                .map(DescriptorAware::getDescriptor)
                .map(CentralConfigDescriptor::getUrlBase)
                .filter(StringUtils::isNotEmpty)
                .map(l -> (normalizeUrl(l).endsWith(HttpUtils.getOverrideContextPath())) ?
                        addTrailingSlash(l) + addTrailingSlash(repoKey) :
                        normalizeUrl(l) + addTrailingSlash(HttpUtils.getOverrideContextPath())
                                + addTrailingSlash(repoKey))
                .orElse(null);
    }

    /**
     * Adds the remote checksum by {@param type} if it exists as a header on the {@param response}.
     */
    protected static void addChecksumIfExists(HttpResponse response, ChecksumType type,
            Set<ChecksumInfo> remoteChecksums) {
        Header checksumHeader = response.getFirstHeader(HeaderByChecksumType.headerForChecksum(type));
        if (checksumHeader != null) {
            remoteChecksums.add(new ChecksumInfo(type, checksumHeader.getValue(), null));
        }
    }

    public static String escapePlusSymbol(String url) {
        return url.replace("+", "%2B");
    }

    public static String escapeSemicolonSymbol(String url) {
        return url.replace(";", "%3B");
    }

    public static String addMatrixParamToUrl(String url, String name, Set<String> values) {
        StringBuilder stringBuilder = new StringBuilder(url);
        if (name == null) {
            throw new IllegalArgumentException("Property name should not be null");
        }
        if (values == null) {
            throw new IllegalArgumentException("Property value should not be null");
        }
        if (values.isEmpty()) {
            return url;
        }
        values.forEach(
                propValue -> stringBuilder.append(";").append(encodeUrlWithURLEncoder(name)).append("=")
                        .append(encodeUrlWithURLEncoder(propValue)));

        return stringBuilder.toString();
    }

    /**
     * For a given url, escapes all ":" symbols, except those ":" that are followed by a double-slash "//". So, a colon
     * will not be escaped if it's a part of "://" sequence.
     *
     * @param url a url that requires escaping of ":" symbols
     * @return an escaped url or a <code>null</code> if the input url was <code>null</code>
     */
    @VisibleForTesting
    static String escapeColonSymbol(String url) {
        if (url != null) {
            url = url.replaceAll(":(?!\\/\\/)", "%3A");
        }
        return url;
    }


    /**
     * This logic was implemented in each package resource instead of using a common place.
     *
     * @return get artifactory base url
     */
    public static String getArtifactoryBaseUrl(@Nullable HttpServletRequest request) {
        if (request != null) {
            return HttpUtils.getServletContextUrl(request);
        }
        String url = ContextHelper.get().getCentralConfig().getDescriptor().getUrlBase();
        if (StringUtils.isNotBlank(url)) {
            String context = ConstantValues.contextPath.getString();
            url = url.endsWith(context) ? url : url + context;
            return url;
        }
        return RepoRequests.getServletContextUrl();
    }

    /**
     * Checks if a value for header exists in the incoming request.
     * If exists, adds it to the outgoing request headers map. Otherwise, adds the passed value.
     */
    public static void addHeader(Request incomingRequest, HeadersMultiMap headers, String name, String value) {
        Enumeration<String> values = incomingRequest.getHeaderValues(name);
        if (values.hasMoreElements()) {
            headers.addAll(name, values);
            return;
        }
        headers.addHeader(name, value);
    }

    /**
     * For range requests we're getting actual content length in CoContent-Range header
     * with format like "bytes 0-1/1256", where the first part (0-1) is requested range and the second (1256) an actual
     *
     * @param response response to get content length from.
     * @return actual content length for range requests.
     */
    public static long getContentLengthForRangeRequest(HttpResponse response) {
        Header header = response.getFirstHeader(HttpHeaders.CONTENT_RANGE);
        if (header == null) {
            return -1;
        }
        String value = header.getValue();
        if (value == null) {
            return -1;
        }
        String[] parts = value.split("/");
        if (parts.length < 2) {
            return -1;
        }
        return extractContentLengthFromHeader(parts[1]);
    }

    public static RangesSummary summarizeRangesRequested(String rangeHeader, @Nullable Long totalSize) {
        if (rangeHeader == null || rangeHeader.isEmpty()) {
            return null;
        }

        try {
            Ranges ranges = Ranges.parse(new StringReader(rangeHeader));
            if (ranges == null || !"bytes".equals(ranges.getUnits())) {
                return null;
            }

            List<Ranges.Entry> entries = ranges.getEntries();
            long rangesCount = entries.size();

            long minOffset = Long.MAX_VALUE;
            long totalRequestedSize = 0;
            boolean sizeUnknown = false;

            for (Ranges.Entry entry : entries) {
                long start = entry.getStart();
                long end = entry.getEnd();

                if (start == -1) {
                    // Suffix range: bytes=-N
                    if (totalSize != null) {
                        start = Math.max(0, totalSize - end);
                        end = totalSize - 1;
                    } else {
                        totalRequestedSize += end; // In reality can be less if the whole file is smaller
                        minOffset = Long.MAX_VALUE;
                        continue;
                    }
                } else if (end == -1) {
                    // Prefix range: bytes=N-
                    if (totalSize != null) {
                        end = totalSize - 1;
                    } else {
                        sizeUnknown = true;
                    }
                }

                if (start < minOffset) {
                    minOffset = start;
                }

                if (end >= start) {
                    totalRequestedSize += (end - start + 1);
                }
            }

            long offset = minOffset == Long.MAX_VALUE ? -1 : minOffset;
            return new RangesSummary(rangesCount, offset, sizeUnknown ? -1 : totalRequestedSize);
        } catch (Exception e) {
            log.debug("Failed to parse Range header: {}", rangeHeader, e);
            return null;
        }
    }

    /**
     * A summary of byte range(s) requested in a range request.
     * @param rangesCount - The number of ranges requested in the Range header.
     * @param offset The starting byte offset of the first range. For suffix requests,
     *               the value will be available only when we know the total size of the requested resource,
     *               otherwise we return -1. For multiple range requests, we return the minimal
     *               of their start offsets (-1 if one of them cannot be computed).
     *               <ul>
     *                  <li>Regular byte range request: <c>Range: bytes=1000-5000</c> -> 1000</li>
     *                  <li>Prefix byte range request: <c>Range: bytes=2000-</c> -> 1000</li>
     *                  <li>Suffix byte range request: <c>Range: bytes=-5000</c> -> -1</li>
     *                  <li>Multiple ranges request: <c>Range: bytes=0-500, 1000-1500</c> -> 0</li>
     *               </ul>
     * @param size The sum of the requested range sizes.
     *             <ul>
     *                  <li>Regular byte range request: <c>Range: bytes=1000-5000</c> -> 4000
     *                  <li>Prefix byte range request: <c>Range: bytes=2000-</c> -> omitted if we do not know the size
     *                  of the artifact, the {size}-{start} value otherwise.</li>
     *                  <li>Suffix byte range request: <c>Range: bytes=-5000</c> -> 5000 (or less if the size of
     *                  the artifact is known and less than 5000).</li>
     *                  <li>Multiple ranges request: <c>Range: bytes=0-500, 1000-1500</c> - The sum of all
     *                  range sizes -> 1000</li>
     *             </ul>
     */
    public record RangesSummary(long rangesCount, long offset, long size) {
    }

    /**
     * Returns the subdomain of the server URL from the request.
     * If the server URL is malformed or does not contain a subdomain, it returns an empty string.
     * examples:
     * - "https://entplus.jfrog.io/artifactory/libs-staging" -> "entplus"
     * - "https://www.entplus.jfrog.io/artifactory/libs-staging" -> "entplus"
     * - "http://localhost:8082/artifactory" -> "localhost"
     * - "http://" -> ""
     *
     * @param httpRequest The HTTP request containing the server URL.
     * @return The subdomain of the server URL or an empty string if not found.
     */
    public static String getSubdomain(HttpServletRequest httpRequest) {
        String serverUrl = getServerUrl(httpRequest);
        try {
            URL url = new URIBuilder(serverUrl).build().toURL();
            String[] parts = url.getHost().split("\\.", 3);
            if (parts.length > 1 && Set.of("www", "www2").contains(parts[0].toLowerCase())) {
                return parts[1];
            } else if (parts.length > 0) {
                return parts[0];
            }
        } catch (Exception e) {
            log.warn("Error parsing URL: '{}', returning empty host", serverUrl, e);
        }
        return StringUtils.EMPTY;
    }
}
