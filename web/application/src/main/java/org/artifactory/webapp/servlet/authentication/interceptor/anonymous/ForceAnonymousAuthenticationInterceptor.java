package org.artifactory.webapp.servlet.authentication.interceptor.anonymous;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Interceptors that are called by {@link org.artifactory.webapp.servlet.AccessFilter#doFilterInternal}
 * and signify if a request should be allowed to authenticate as Anonymous by the
 * {@link org.artifactory.webapp.servlet.authentication.ArtifactoryAuthenticationFilterChain}
 *
 * Note that {@link ForceAnonymousAuthenticationInterceptor} differs from {@link AnonymousAuthenticationInterceptor}
 * by getting called prior the authentication filter chain.
 * That means that a request accepted by this interceptor will always get an anonymous authentication and the other
 * authentication filters will get ignored.
 *
 * @author BarakH
 */
public interface ForceAnonymousAuthenticationInterceptor extends AnonymousAuthenticationInterceptor {

    boolean accept(HttpServletRequest request);
}
