package org.artifactory.webapp.servlet.model;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.authentication.ArtifactoryAuthenticationContextFilter;
import org.artifactory.authentication.context.AuthenticationContext;
import org.artifactory.security.SimpleUser;
import org.springframework.security.core.Authentication;

import java.io.IOException;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.artifactory.authentication.context.AuthenticationContextStatus.ANONYMOUS_ALLOWED;
import static org.artifactory.authentication.context.AuthenticationContextStatus.AUTHENTICATION_ALLOWED;
import static org.artifactory.authentication.context.AuthenticationContextStatus.FORCE_ANONYMOUS;
import static org.artifactory.authentication.context.AuthenticationContextStatus.HAS_ERROR;

/**
 * @author shahafg
 */
@Getter
public class ArtifactoryFilterContextHolderImpl implements ArtifactoryFilterHolder {

    private final AuthenticationContext authenticationContext;
    private final ArtifactoryAuthenticationContextFilter filter;

    public ArtifactoryFilterContextHolderImpl(@Nonnull ArtifactoryAuthenticationContextFilter filter,
            @Nonnull AuthenticationContext authenticationContext) {
        this.filter = filter;
        this.authenticationContext = authenticationContext;
    }

    @Override
    public String getCacheKey(ServletRequest request) {
        return authenticationContext.getCacheKey();
    }

    @Override
    public String getLoginIdentifier(ServletRequest request) {
        return authenticationContext.getLoginIdentifier();
    }

    @Override
    public boolean isAcceptedFilter(ServletRequest request) {
        return authenticationContext.getAuthenticationStatus() == AUTHENTICATION_ALLOWED;
    }

    @Override
    public boolean isAnonymousAllowed() {
        return authenticationContext.getAuthenticationStatus() == ANONYMOUS_ALLOWED;
    }

    @Override
    public boolean hasErrorResponse() {
        return authenticationContext.getAuthenticationStatus() == HAS_ERROR;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest servletRequest = (HttpServletRequest) request;
        String newBasicAuthValue = authenticationContext.getExtractedAuthorizationToken();
        if (StringUtils.isNotBlank(newBasicAuthValue)) {
            servletRequest = new HttpServletRequestWrapper(servletRequest) {
                @Override
                public String getHeader(String name) {
                    if (AUTHORIZATION.equalsIgnoreCase(name)) {
                        return newBasicAuthValue;
                    }
                    return super.getHeader(name);
                }
            };
        }
        filter.doFilter(servletRequest, response, chain);
    }

    @Override
    public boolean doesRequireReAuthentication(ServletRequest request, Authentication authentication) {
        Object principal = authentication.getPrincipal();
        String userName = this.authenticationContext.getLoginIdentifier();
        return (principal != null) && !((SimpleUser) principal).getUsername().equals(userName);
    }

    @Override
    public boolean isForcedAnonymous() {
        return this.authenticationContext.getAuthenticationStatus() == FORCE_ANONYMOUS;
    }
}