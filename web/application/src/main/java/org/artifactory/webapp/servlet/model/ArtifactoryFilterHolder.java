package org.artifactory.webapp.servlet.model;

import org.springframework.security.core.Authentication;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;

/**
 * The interface was created to provide an unified agreement between the Authentication filters and the AccessFilter
 * .class for providing an information from the filter and to have additional manipulation on the authentication data.
 *
 * @author shahafg
 */
public interface ArtifactoryFilterHolder {

    /**
     * @return the extracted cache key of the request.
     */
    String getCacheKey(ServletRequest request);

    /**
     * @return the accepted filter of the request authentication.
     */
    Filter getFilter();

    /**
     * @return the login identifier of the user from the request that in used in AccessFilter.class as part of the
     * authentication flow.
     */
    String getLoginIdentifier(ServletRequest request);

    /**
     * @return an answer if the filter was accepted because the request is authenticated and the holder fills the
     * right filter.
     */
    boolean isAcceptedFilter(ServletRequest request);

    /**
     * @return an answer if the filter classified the request as anonymous and the access to artifactory for the request
     * is allowed.
     */
    boolean isAnonymousAllowed();

    /**
     * Preforms a doFilter on the request to make the request access to the request destination.
     */
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException;

    /**
     * @return an answer if the authentication data and the request are equal or not, if the information is
     * identical, we should return false because nothing was changed.
     */
    boolean doesRequireReAuthentication(ServletRequest request, Authentication authentication);

    /**
     * @return an answer if the authentication filter returns an error.
     */
    boolean hasErrorResponse();

    /**
     * @return an answer if the filter classified the request as forced anonymous and the access to artifactory for
     * the request is allowed.
     */
    boolean isForcedAnonymous();
}
