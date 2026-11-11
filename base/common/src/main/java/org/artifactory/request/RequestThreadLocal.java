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

import com.google.common.annotations.VisibleForTesting;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.artifactory.common.ConstantValues;

import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * @author Lior Azar
 */
@Slf4j
public class RequestThreadLocal {

    private static ThreadLocal<RequestThreadLocalContext> context = new ThreadLocal<>();

    public static final String FEDERATION_SERVICE_USER_AGENT = "artifactory-federation-service";

    @Getter(lazy = true)
    private static final List<Pattern> unlimitedUserAgentPatterns = generateUserAgentPatterns();

    @Getter(lazy = true)
    private static final List<Pattern> universalLimitExclusionUserAgentPatterns =
            generateLimitExclusionUserAgentPatterns();

    private RequestThreadLocal() {
    }

    /**
     * Executes {@code accessor}, which reads from (or writes to) the bound {@link HttpServletRequest},
     * guarding against {@link IllegalStateException}. Tomcat throws this when the request facade has
     * already been recycled - e.g. a request captured on one thread and rebound on another via
     * {@link #bind(RequestWrapper)} after the originating HTTP call has already completed
     * (see RTDEV-87329). Falls back to {@code fallback} and logs a warning instead of letting the
     * exception propagate and break unrelated callers (e.g. DB reads gating on
     * {@code UniversalLimitHandlerUtility}).
     */
    private static <T> T safeRequestAccess(Supplier<T> accessor, T fallback, String action) {
        try {
            return accessor.get();
        } catch (IllegalStateException e) {
            log.warn("Failed to {}, cause: {} falling back to default", action, e.getMessage());
            return fallback;
        }
    }

    /** {@link #safeRequestAccess(Supplier, Object, String)} for void, request-mutating actions. */
    private static void safeRequestAccess(Runnable action, String description) {
        try {
            action.run();
        } catch (IllegalStateException e) {
            log.warn("Failed to {}, cause: {}", description, e.getMessage());
        }
    }

    public static void bind(RequestWrapper requestWrapper) {
        context.set(RequestThreadLocalContext.create(requestWrapper));
    }

    public static void unbind() {
        context.remove();
    }

    public static HttpServletRequest getRequest() {
        RequestThreadLocalContext requestThreadLocalContext = context.get();
        return requestThreadLocalContext != null ? requestThreadLocalContext.getRequestThreadLocal().getRequest() :
                null;
    }

    public static DownloadPerfCounters getDownloadPerfCounters() {
        return getDownloadPerfCounters(false);
    }

    public static DownloadPerfCounters getDownloadPerfCounters(boolean createIfNotExists) {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        return safeRequestAccess(() -> {
            DownloadPerfCounters downloadPerfCounters =
                    (DownloadPerfCounters) request.getAttribute(DownloadPerfCounters.class.getName());
            if (downloadPerfCounters == null && createIfNotExists) {
                downloadPerfCounters = new DownloadPerfCounters();
                request.setAttribute(DownloadPerfCounters.class.getName(), downloadPerfCounters);
            }
            return downloadPerfCounters;
        }, null, "get or create download perf counters");
    }

    public static RedirectInfo getRedirectInfo() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        return safeRequestAccess(() -> {
            RedirectInfo redirectInfo = (RedirectInfo) request.getAttribute(RedirectInfo.class.getName());
            if (redirectInfo == null) {
                redirectInfo = new RedirectInfo();
                request.setAttribute(RedirectInfo.class.getName(), redirectInfo);
            }
            return redirectInfo;
        }, null, "get or create redirect info");
    }

    /**
     * Gets or creates a request-scoped attribute by class type.
     * This is a generic helper method that retrieves an attribute from the current HttpServletRequest,
     * or creates a new instance if it doesn't exist. The attribute is stored in the HttpServletRequest
     * attributes to ensure it's scoped to the current request and automatically cleared when the request completes.
     *
     * @param clazz the class type of the attribute to retrieve or create
     * @param supplier a supplier function to create a new instance if the attribute doesn't exist
     * @param <T> the type of the attribute
     * @return the attribute value if found or newly created, null if request is not available
     */
    public static <T> T getOrCreateRequestAttribute(Class<T> clazz, Supplier<T> supplier) {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        // Guard only getAttribute/setAttribute so a supplier-thrown IllegalStateException propagates
        // unchanged. Recycled-request failures still fall back to null (RTDEV-87329).
        T attribute;
        try {
            attribute = (T) request.getAttribute(clazz.getName());
        } catch (IllegalStateException e) {
            log.warn("Failed to get request attribute '{}', cause: {} falling back to default",
                    clazz.getName(), e.getMessage());
            return null;
        }
        if (attribute == null) {
            attribute = supplier.get();
            try {
                request.setAttribute(clazz.getName(), attribute);
            } catch (IllegalStateException e) {
                log.warn("Failed to set request attribute '{}', cause: {}", clazz.getName(), e.getMessage());
                return null;
            }
        }
        return attribute;
    }

    /** Sets a request-scoped attribute; no-op when no HTTP request is bound. */
    public static void setRequestAttribute(String key, Object value) {
        HttpServletRequest request = getRequest();
        if (request != null) {
            safeRequestAccess(() -> request.setAttribute(key, value), "set request attribute '" + key + "'");
        }
    }

    /**
     * Returns a request-scoped attribute typed as {@code T}, or {@code null} when the attribute
     * is absent, no HTTP request is bound, or the stored value is not an instance of {@code type}.
     */
    public static <T> T getRequestAttribute(String key, Class<T> type) {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        return safeRequestAccess(() -> {
            Object value = request.getAttribute(key);
            return type.isInstance(value) ? type.cast(value) : null;
        }, null, "get request attribute '" + key + "'");
    }

    public static String getClientAddress() {
        RequestThreadLocalContext requestThreadLocalContext = context.get();
        if (requestThreadLocalContext != null) {
            RequestWrapper requestWrapper = requestThreadLocalContext.getRequestThreadLocal();
            try {
                return requestWrapper.getClientAddress();
            } catch (URISyntaxException | IllegalStateException e) {
                log.warn("{}, cause: {} switching to empty remote address client", e.getMessage(), e);
                return "";
            }
        }
        return "";
    }

    public static String getUserAgent() {
        RequestThreadLocalContext requestThreadLocalContext = context.get();
        if (requestThreadLocalContext != null) {
            HttpServletRequest request = requestThreadLocalContext.getRequestThreadLocal().getRequest();
            return safeRequestAccess(
                    () -> Optional.ofNullable(request.getHeader(HttpHeaders.USER_AGENT)).orElse(""),
                    "", "read request user-agent");
        }
        return "";
    }

    public static boolean isInternalUser() {
        RequestThreadLocalContext requestThreadLocalContext = context.get();
        if (requestThreadLocalContext != null) {
            HttpServletRequest request = requestThreadLocalContext.getRequestThreadLocal().getRequest();
            return safeRequestAccess(() -> isInternalUser(request.getHeader(HttpHeaders.USER_AGENT)),
                    false, "read request user-agent for internal-user check");
        }
        return false;
    }

    private static boolean isInternalUser(String userAgentHeader) {
        return userAgentHeader != null
                && isUnlimitedUserAgent(userAgentHeader);
    }

    /**
     * Returns {@code true} when the current request's {@code User-Agent} identifies one of the
     * internal clients configured in {@link ConstantValues#limitAllQueriesExemptUserAgents},
     * exempting the caller from the universal row-count limit enforced by
     * {@code UniversalLimitHandlerUtility}. The configured symbolic names (pipeline, xray,
     * artifactory, distribution, federation) are expanded by
     * {@link #generateLimitExclusionUserAgentPatterns()} into the same anchored User-Agent patterns
     * used by the AQL {@link #isUnlimitedUserAgent(String)} path, so an unrelated agent that merely
     * contains a symbolic name as a substring (e.g. {@code MozillaXray}) is not exempt.
     */
    public static boolean isExcludedFromUniversalLimit() {
        RequestThreadLocalContext requestThreadLocalContext = context.get();
        if (requestThreadLocalContext != null) {
            HttpServletRequest request = requestThreadLocalContext.getRequestThreadLocal().getRequest();
            return safeRequestAccess(() -> isLimitExclusionUserAgent(request.getHeader(HttpHeaders.USER_AGENT)),
                    false, "read request user-agent for universal-limit exclusion check");
        }
        return false;
    }

    /**
     * Returns {@code true} when the given {@code userAgent} matches one of the anchored patterns
     * produced from {@link ConstantValues#limitAllQueriesExemptUserAgents} (case-insensitive).
     * Returns {@code false} for a {@code null} or blank user-agent.
     */
    @VisibleForTesting
    static boolean isLimitExclusionUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        String lowerCasedUserAgent = userAgent.toLowerCase();
        return getUniversalLimitExclusionUserAgentPatterns().stream()
                .anyMatch(pattern -> pattern.matcher(lowerCasedUserAgent).matches());
    }

    public static String getRequestPath() {
        RequestThreadLocalContext requestThreadLocalContext = context.get();
        if (requestThreadLocalContext != null) {
            HttpServletRequest request = requestThreadLocalContext.getRequestThreadLocal().getRequest();
            return safeRequestAccess(() -> Optional.ofNullable(request.getPathInfo()).orElse(""),
                    "", "read request path");
        }
        return "";
    }

    public static String getBaseUrl() {
        RequestThreadLocalContext requestThreadLocalContext = context.get();
        if (requestThreadLocalContext != null) {
            RequestWrapper requestWrapper = requestThreadLocalContext.getRequestThreadLocal();
            return safeRequestAccess(requestWrapper::getBaseUrl, "", "read request base URL");
        }
        return "";
    }

    public static boolean shouldStream() {
        RequestThreadLocalContext requestThreadLocalContext = context.get();
        if (requestThreadLocalContext != null) {
            HttpServletRequest request = requestThreadLocalContext.getRequestThreadLocal().getRequest();
            String streamHeader = safeRequestAccess(
                    () -> Optional.ofNullable(request.getHeader("stream")).orElse("false"),
                    "false", "read request stream header");
            return Boolean.parseBoolean(streamHeader);
        }
        return false;
    }

    public static int scanFactor() {
        RequestThreadLocalContext requestThreadLocalContext = context.get();
        int scanFactor = 1;
        if (requestThreadLocalContext != null) {
            HttpServletRequest request = requestThreadLocalContext.getRequestThreadLocal().getRequest();
            String streamHeader = safeRequestAccess(
                    () -> Optional.ofNullable(request.getHeader("scan-factor")).orElse("1"),
                    "1", "read request scan-factor header");
            try {
                scanFactor = Integer.parseInt(streamHeader.trim());
            } catch (NumberFormatException e) {
                log.warn("scan-factor must be an integer", e);
            }
        }
        return scanFactor > 1 ? scanFactor : Math.max(ConstantValues.aqlQueryScanFactor.getInt(), 1);
    }

    public static boolean isUnlimitedUserAgent(String userAgent) {
        final String lowerCasedUserAgent = userAgent.toLowerCase();
        return getUnlimitedUserAgentPatterns().stream()
                .anyMatch(pattern -> pattern.matcher(lowerCasedUserAgent).matches());
    }

    static List<Pattern> generateUserAgentPatterns() {
        return compileInternalUserAgentPatterns(ConstantValues.aqlQueriesUnlimitedAgents.getList());
    }

    /**
     * Mirrors {@link #generateUserAgentPatterns()} for the universal-limit exemption: expands the
     * symbolic names configured in {@link ConstantValues#limitAllQueriesExemptUserAgents} into the
     * same anchored User-Agent {@link Pattern}s. Unlike the AQL list this property defaults to empty
     * (no exemptions) and is read on each call so operators can reconfigure it without a restart.
     */
    static List<Pattern> generateLimitExclusionUserAgentPatterns() {
        return compileInternalUserAgentPatterns(ConstantValues.limitAllQueriesExemptUserAgents.getList());
    }

    /**
     * Expands the symbolic agent names (pipeline, xray, artifactory, distribution, federation) into
     * the concrete, anchored {@link Pattern}s that identify each internal client by its User-Agent.
     * Unrecognized values are ignored. Matching is performed with {@link java.util.regex.Matcher#matches()}
     * against a lower-cased User-Agent, so these patterns are intentionally anchored to avoid matching
     * unrelated agents that merely contain a symbolic name as a substring (e.g. {@code MozillaXray}).
     */
    private static List<Pattern> compileInternalUserAgentPatterns(List<String> symbolicAgentNames) {
        Set<String> patterns = new LinkedHashSet<>();
        symbolicAgentNames.stream()
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(String::toLowerCase)
                .forEach(agent -> {
                    switch (agent) {
                        case "pipeline" -> {
                            patterns.add("pipelines api");
                            patterns.add("jfrog-pipelines:.*");
                        }
                        case "xray" -> patterns.add("xray\\/.*");
                        case "artifactory" -> patterns.add("artifactory\\/.*");
                        case "distribution" -> patterns.add("artifactory-client-java\\/.*");
                        case "federation" -> patterns.add(FEDERATION_SERVICE_USER_AGENT);
                        default -> log.debug("Unrecognized symbolic agent name '{}' in user-agent exemption property;" +
                                " no pattern will be added for it.", agent);
                    }
                });
        return patterns.stream().map(Pattern::compile).toList();
    }

}