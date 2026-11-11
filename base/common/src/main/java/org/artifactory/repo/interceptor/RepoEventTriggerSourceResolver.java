package org.artifactory.repo.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.security.SecurityService;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.ServiceTokenPrefixes;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.jfrog.access.token.JwtAccessToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Shared trigger-source resolver for repository CUD events. Both synchronous BEFORE Worker dispatch and asynchronous
 * AFTER publishing must use this resolver so the wire value is classified by the same TR1 precedence contract.
 */
@Slf4j
@Component
public class RepoEventTriggerSourceResolver {

    private static final String REQUEST_AGENT_HEADER = "Request-Agent";
    private static final String JFROG_AI_AGENT_HEADER = "X-JFrog-AI-Agent";
    private static final String USER_AI_AGENT_HEADER = "X-AI-Agent";
    private static final String UI_REQUEST_AGENT = "artifactoryui";
    private static final String UI_USER_AGENT_TOKEN = "jfrog-frontend";

    private final SecurityService securityService;

    public RepoEventTriggerSourceResolver(SecurityService securityService) {
        this.securityService = securityService;
    }

    public String resolve() {
        return resolveSource().name();
    }

    RepoEventTriggerSource resolveSource() {
        // Token layer: authoritative cryptographic signals, ordered most-specific first (DC11).
        RepoEventTriggerSource tokenSource = resolveByAuthenticationToken();
        if (tokenSource != null && tokenSource.isAiAgent()) {
            return tokenSource;
        }

        // AI-agent HTTP headers: explicit self-identification overrides non-AI token heuristics.
        HttpServletRequest request = RequestThreadLocal.getRequest();
        String userAgent = request != null
                ? StringUtils.lowerCase(StringUtils.defaultString(RequestThreadLocal.getUserAgent()))
                : null;
        RepoEventTriggerSource httpSource = resolveFromHttpContext(request, userAgent);
        if (httpSource != null) {
            return httpSource;
        }

        // Non-AI token classification (UI issuer, named tools, internal service).
        if (tokenSource != null) {
            return tokenSource;
        }

        // No HTTP context = background/scheduled/no-request-thread -> system internal.
        if (request == null) {
            return RepoEventTriggerSource.SYSTEM_INTERNAL;
        }

        return resolveHttpFallback(request, userAgent);
    }

    private RepoEventTriggerSource resolveFromHttpContext(HttpServletRequest request, String userAgent) {
        if (request == null) {
            return null;
        }
        if (isJfrogAiAgent(request, userAgent)) {
            return RepoEventTriggerSource.JFROG_AI_AGENT;
        }
        if (isUserAiAgent(request, userAgent)) {
            return RepoEventTriggerSource.USER_AI_AGENT;
        }
        return null;
    }

    private RepoEventTriggerSource resolveHttpFallback(HttpServletRequest request, String userAgent) {
        String requestUri = StringUtils.lowerCase(StringUtils.defaultString(request.getRequestURI()));
        if (isTerraform(requestUri, userAgent)) {
            return RepoEventTriggerSource.TERRAFORM;
        }
        if (isJfrogCli(userAgent)) {
            return RepoEventTriggerSource.JFROG_CLI;
        }
        if (isUi(request, requestUri, userAgent)) {
            return RepoEventTriggerSource.JFROG_UI;
        }
        if (isSystemPrincipal()) {
            return RepoEventTriggerSource.SYSTEM_INTERNAL;
        }
        return StringUtils.isBlank(userAgent) && StringUtils.isBlank(requestUri)
                ? RepoEventTriggerSource.UNKNOWN
                : RepoEventTriggerSource.REST_API;
    }

    private boolean isSystemPrincipal() {
        return SecurityService.USER_SYSTEM.equals(securityService.currentUsername());
    }

    private RepoEventTriggerSource resolveByAuthenticationToken() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (!(authentication instanceof AccessTokenAuthentication accessTokenAuthentication)) {
            return null;
        }
        String tokenSubject = tokenSubject(accessTokenAuthentication);
        // 1–2: AI agents first — most specific token signals (DC11).
        if (containsAny(tokenSubject, "jfrog-ai", "jfrog-mcp")) {
            return RepoEventTriggerSource.JFROG_AI_AGENT;
        }
        if (containsAny(tokenSubject, "cursor/", "claude/", "mcp-", "ai-agent")) {
            return RepoEventTriggerSource.USER_AI_AGENT;
        }
        // 3: UI — frontend token issuer is cryptographic proof of UI origin. Must precede
        // isInternalServiceToken: the jffe@ subject prefix appears in ServiceTokenPrefixes,
        // so without this ordering the issuer check would never be reached.
        if (AuthenticationHelper.isJFrogFrontendAuthentication(authentication)) {
            return RepoEventTriggerSource.JFROG_UI;
        }
        // 4–5: Specific tool tokens.
        if (containsAny(tokenSubject, "terraform")) {
            return RepoEventTriggerSource.TERRAFORM;
        }
        if (containsAny(tokenSubject, "jfrog-cli", "setup-jfrog-cli")) {
            return RepoEventTriggerSource.JFROG_CLI;
        }
        // 6: Internal service token — heuristic catch-all for service-to-service calls; less specific
        // than any named signal above so it is checked last.
        if (isInternalServiceToken(accessTokenAuthentication)) {
            return RepoEventTriggerSource.SYSTEM_INTERNAL;
        }
        return null;
    }

    private static boolean isInternalServiceToken(AccessTokenAuthentication authentication) {
        String subject = tokenSubject(authentication);
        if (StringUtils.isBlank(subject) || !subject.contains("@")) {
            return false;
        }
        String prefix = subject.substring(0, subject.indexOf('@'));
        return ServiceTokenPrefixes.TOKEN_PREFIX_TO_SERVICE.containsKey(prefix);
    }

    private static String tokenSubject(AccessTokenAuthentication authentication) {
        JwtAccessToken accessToken = authentication.getAccessToken();
        return accessToken == null ? null : StringUtils.lowerCase(accessToken.getSubject());
    }

    private static boolean isJfrogAiAgent(HttpServletRequest request, String userAgent) {
        return hasHeader(request, JFROG_AI_AGENT_HEADER)
                || containsAny(userAgent, "jfrog-ai", "jfrog-mcp");
    }

    private static boolean isUserAiAgent(HttpServletRequest request, String userAgent) {
        return hasHeader(request, USER_AI_AGENT_HEADER)
                || containsAny(userAgent, "agent/cursor", "agent/claude", "mcp/", "mcp-client");
    }

    private static boolean isTerraform(String requestUri, String userAgent) {
        return requestUri.contains("/api/terraform/")
                || containsAny(userAgent, "terraform-provider-artifactory", "terraform-provider");
    }

    private static boolean isJfrogCli(String userAgent) {
        return userAgent.startsWith("jfrog/")
                || containsAny(userAgent, "jfrog-cli", "setup-jfrog-cli");
    }

    private static boolean isUi(HttpServletRequest request, String requestUri, String userAgent) {
        return UI_REQUEST_AGENT.equalsIgnoreCase(request.getHeader(REQUEST_AGENT_HEADER))
                || requestUri.contains("/ui/api/")
                || userAgent.contains(UI_USER_AGENT_TOKEN);
    }

    private static boolean hasHeader(HttpServletRequest request, String headerName) {
        return StringUtils.isNotBlank(request.getHeader(headerName));
    }

    private static boolean containsAny(String value, String... markers) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
