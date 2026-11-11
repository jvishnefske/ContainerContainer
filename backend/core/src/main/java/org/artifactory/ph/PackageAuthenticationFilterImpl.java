package org.artifactory.ph;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.authentication.ArtifactoryAuthenticationContextFilter;
import org.artifactory.authentication.ArtifactoryAuthenticationType;
import org.artifactory.authentication.ArtifactoryBasicAuthenticationFilter;
import org.artifactory.authentication.context.AuthenticationContext;
import com.jfrog.ph.acl.model.PackageAuthenticationRequestContext;
import com.jfrog.ph.acl.service.PackageAuthenticationAdapter;
import org.artifactory.webapp.servlet.RequestUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import java.io.IOException;

import static jakarta.ws.rs.core.HttpHeaders.WWW_AUTHENTICATE;
import static org.artifactory.authentication.context.AuthenticationContextStatus.ANONYMOUS_ALLOWED;
import static org.artifactory.authentication.context.AuthenticationContextStatus.AUTHENTICATION_ALLOWED;
import static org.artifactory.authentication.context.AuthenticationContextStatus.FORCE_ANONYMOUS;
import static org.artifactory.authentication.context.AuthenticationContextStatus.HAS_ERROR;
import static org.artifactory.authentication.context.AuthenticationContextStatus.NOT_HANDLED;

/**
 * @author shahafg
 */
@Slf4j
public class PackageAuthenticationFilterImpl implements ArtifactoryAuthenticationContextFilter {

    private final PackageAuthenticationAdapter packageAuthenticationAdapter;

    private ArtifactoryBasicAuthenticationFilter basicFilter;

    public PackageAuthenticationFilterImpl(PackageAuthenticationAdapter packageAuthenticationAdapter) {
        this.packageAuthenticationAdapter = packageAuthenticationAdapter;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        ServletContext servletContext = filterConfig.getServletContext();
        ArtifactoryContext context = RequestUtils.getArtifactoryContext(servletContext);
        basicFilter = context.beanForType(ArtifactoryBasicAuthenticationFilter.class);
        log.debug("Initializing finished");
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        basicFilter.doFilter(servletRequest, servletResponse, filterChain);
    }

    @Override
    public AuthenticationContext getAuthenticationContext(ServletRequest request, ServletResponse response)
            throws IOException {
        PackageAuthenticationRequestContext context =
                this.packageAuthenticationAdapter.getContext((HttpServletRequest) request);

        if (context.getResponseError() != null) {
            writeResponseErrorIfNeeded((HttpServletResponse) response, context.getResponseError());
            return AuthenticationContext.builder().authenticationStatus(HAS_ERROR).build();
        } else if (context.isForceAnonymousRequest()) {
            return AuthenticationContext.builder().authenticationStatus(FORCE_ANONYMOUS).build();
        } else if (context.isAuthenticationAllowed()) {
            return AuthenticationContext.builder().authenticationStatus(AUTHENTICATION_ALLOWED)
                    .cacheKey(getCacheKey(request, context)).loginIdentifier(getLoginIdentifier(request, context))
                    .extractedAuthorizationToken(context.getAuthorizationToken()).build();
        } else if (context.isAnonymousRequestAllowed()) {
            return AuthenticationContext.builder().authenticationStatus(ANONYMOUS_ALLOWED).build();
        }

        return AuthenticationContext.builder().authenticationStatus(NOT_HANDLED).build();
    }

    private void writeResponseErrorIfNeeded(HttpServletResponse response, Response responseError) throws IOException {
        if (responseError != null) {
            response.setStatus(responseError.getStatus());

            if (responseError.getMetadata() != null && responseError.getMetadata().get(WWW_AUTHENTICATE) != null) {
                String authValue = responseError.getMetadata().get(WWW_AUTHENTICATE).get(0).toString();
                response.addHeader(WWW_AUTHENTICATE, authValue);
            }

            if (responseError.getEntity() != null) {
                response.getWriter().write(responseError.getEntity().toString() + "\n");
            }
        }
    }

    private String getCacheKey(ServletRequest request, PackageAuthenticationRequestContext context) {
        if (context.getAuthorizationToken() != null) {
            return context.getAuthorizationToken();
        }
        return this.basicFilter.getCacheKey(request);
    }

    private String getLoginIdentifier(ServletRequest request, PackageAuthenticationRequestContext context) {
        if (context.getPrincipleUserName() != null) {
            return context.getPrincipleUserName();
        }
        return this.basicFilter.getLoginIdentifier(request);
    }

    @Override
    public ArtifactoryAuthenticationType getType() {
        return ArtifactoryAuthenticationType.PACKAGES_AUTH;
    }
}