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

package org.artifactory.webapp.servlet.authentication;

import org.apache.http.HttpHeaders;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.security.SecurityService;
import org.artifactory.authentication.ArtifactoryAuthenticationFilter;
import org.artifactory.authentication.ArtifactoryAuthenticationType;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.sapi.security.SecurityConstants;
import org.artifactory.security.access.AccessService;
import org.artifactory.webapp.servlet.RequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

import static org.artifactory.authentication.AuthenticationFilterUtils.isRequestContainsAuthentication;

/**
 * @author Eli Givoni
 */
public class ArtifactoryRememberMeFilter implements ArtifactoryAuthenticationFilter {

    private RememberMeAuthenticationFilter rememberMeDelegateFilter;
    private SecurityService securityService;
    private AccessService accessService;

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setAccessService(AccessService accessService) {
        this.accessService = accessService;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        ArtifactorySystemProperties properties =
                ((ArtifactoryHome) filterConfig.getServletContext().getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR))
                        .getArtifactoryProperties();
        ConstantValues disableRememberMeProp = ConstantValues.securityDisableRememberMe;
        boolean disableRememberMe = properties.getBooleanProperty(disableRememberMeProp);
        if (!disableRememberMe) {
            ServletContext servletContext = filterConfig.getServletContext();
            ArtifactoryContext context = RequestUtils.getArtifactoryContext(servletContext);
            rememberMeDelegateFilter = context.beanForType(RememberMeAuthenticationFilter.class);
            rememberMeDelegateFilter.init(filterConfig);
        }
    }

    public boolean requiresReAuthentication(ServletRequest request, Authentication authentication) {
        return false;
    }

    @Override
    public boolean acceptFilter(ServletRequest request) {
        if (rememberMeDelegateFilter == null) {
            return false;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        boolean cookieAuthFixEnabled = ConstantValues.securityRememberMeCookieAuthFixEnabled.getBoolean();

        if (!carriesRememberMeCookie(httpRequest, cookieAuthFixEnabled)) {
            return false;
        }

        // Remember-me is ordered ahead of every credential filter in AuthenticationFilterListSorter and only the
        // first matching filter is selected, so it has to yield to whichever filter owns the request's credential -
        // otherwise it claims a request it cannot authenticate and the resource runs with no user.
        return !cookieAuthFixEnabled
                || !isRequestContainsAuthentication(httpRequest, securityService, accessService);
    }

    private static boolean carriesRememberMeCookie(HttpServletRequest request, boolean cookieAuthFixEnabled) {
        Cookie[] cookies = request.getCookies();

        if ((cookies == null) || (cookies.length == 0)) {
            return false;
        }

        String expectedCookieName = cookieAuthFixEnabled
                ? SecurityConstants.REMEMBER_ME_COOKIE_NAME
                : AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY;
        for (Cookie cookie : cookies) {
            if (expectedCookieName.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getCacheKey(ServletRequest request) {
        return ((HttpServletRequest) request).getHeader(HttpHeaders.AUTHORIZATION);
    }

    /**
     * @param request The http request
     * @return Login identifier such as user, sessionId, apiKey, etc.
     */
    @Override
    public String getLoginIdentifier(ServletRequest request) {
        // TODO: [mp] check what authorization identifier is used in this context
        return getCacheKey(request);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (rememberMeDelegateFilter != null) {
            rememberMeDelegateFilter.doFilter(request, response, chain);
        }
    }

    @Override
    public void destroy() {
        if (rememberMeDelegateFilter != null) {
            rememberMeDelegateFilter.destroy();
        }
    }

    @Override
    public ArtifactoryAuthenticationType getType() {
        return ArtifactoryAuthenticationType.REMEMBER_ME;
    }
}
