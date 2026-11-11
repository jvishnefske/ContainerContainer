package org.artifactory.webapp.servlet.model;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.Getter;
import org.artifactory.authentication.ArtifactoryAuthenticationFilter;
import org.springframework.security.core.Authentication;

import java.io.IOException;

/**
 * @author shahafg
 */
@Getter
public class ArtifactoryFilterHolderImpl implements ArtifactoryFilterHolder {

    public final ArtifactoryAuthenticationFilter filter;

    public ArtifactoryFilterHolderImpl(@Nonnull ArtifactoryAuthenticationFilter filter) {
        this.filter = filter;
    }

    public boolean isAnonymousAllowed() {
        return false;
    }

    @Override
    public String getCacheKey(ServletRequest request) {
        return filter.getCacheKey(request);
    }

    @Override
    public String getLoginIdentifier(ServletRequest request) {
        return filter.getLoginIdentifier(request);
    }

    @Override
    public boolean isAcceptedFilter(ServletRequest request) {
        return filter.acceptFilter(request);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        filter.doFilter(request, response, chain);
    }

    @Override
    public boolean doesRequireReAuthentication(ServletRequest request, Authentication authentication) {
        return filter.requiresReAuthentication(request, authentication);
    }

    @Override
    public boolean hasErrorResponse() {
        return false;
    }

    @Override
    public boolean isForcedAnonymous() {
        return false;
    }
}
