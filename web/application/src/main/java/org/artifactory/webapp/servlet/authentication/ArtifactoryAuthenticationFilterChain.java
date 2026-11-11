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

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.iterators.EnumerationIterator;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.authentication.ArtifactoryAuthenticationContextFilter;
import org.artifactory.authentication.ArtifactoryAuthenticationFilter;
import org.artifactory.authentication.context.AuthenticationContext;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.UserInfo;
import org.artifactory.util.HttpUtils;
import org.artifactory.webapp.servlet.HttpArtifactoryRequest;
import org.artifactory.webapp.servlet.RequestUtils;
import org.artifactory.webapp.servlet.authentication.filtersorter.AuthenticationFilterListSorter;
import org.artifactory.webapp.servlet.model.ArtifactoryFilterContextHolderImpl;
import org.artifactory.webapp.servlet.model.ArtifactoryFilterHolder;
import org.artifactory.webapp.servlet.model.ArtifactoryFilterHolderImpl;
import org.artifactory.webapp.servlet.util.ServletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import jakarta.annotation.Nullable;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.artifactory.authentication.context.AuthenticationContextStatus.NOT_HANDLED;
import static org.artifactory.util.HttpUtils.sendErrorResponse;

/**
 * @author freds
 */
public class ArtifactoryAuthenticationFilterChain {
    private static final Logger log = LoggerFactory.getLogger(ArtifactoryAuthenticationFilterChain.class);

    private SecurityService securityService;

    private BasicAuthenticationEntryPoint authenticationEntryPoint;

    private final List<Filter> authenticationFilters = new ArrayList<>();

    private AuthenticationFilterListSorter artifactoryAuthenticationSorter;

    private FilterConfig filterConfig;

    public ArtifactoryAuthenticationFilterChain(BasicAuthenticationEntryPoint authenticationEntryPoint) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.artifactoryAuthenticationSorter = new AuthenticationFilterListSorter();
    }

    public void addFilters(Collection<ArtifactoryAuthenticationFilter> filters,
            Collection<ArtifactoryAuthenticationContextFilter> contextFilters) {
        contextFilters = Optional.ofNullable(contextFilters).orElseGet(ArrayList::new);
        filters = Optional.ofNullable(filters).orElseGet(ArrayList::new);
        this.authenticationFilters.addAll(
                artifactoryAuthenticationSorter.orderArtifactoryFilters(filters, contextFilters));
    }

    /**
     * Find the correct Artifactory Authentication filter for the request
     *
     * @param request The HTTP request object
     * @return object of FilterContext that holds the information from the filter and the
     * filter itself.
     */
    public ArtifactoryFilterHolder acceptFilter(ServletRequest request, ServletResponse response) throws IOException {
        List<ArtifactoryFilterHolder> matchedFiltersHolder = Lists.newArrayList();
        for (Filter filter : authenticationFilters) {
            ArtifactoryFilterHolder filterHolder = acceptFilter(filter, request, response);
            if (filterHolder != null) {
                matchedFiltersHolder.add(filterHolder);
            }
        }
        if (matchedFiltersHolder.size() > 1) {
            if (log.isDebugEnabled()) {
                String method =
                        request instanceof HttpServletRequest ? ((HttpServletRequest) request).getMethod() : "?METHOD?";
                String uri = request instanceof HttpServletRequest ? ((HttpServletRequest) request).getRequestURI() :
                        "?URI?";
                String filterNames = Joiner.on("\n")
                        .join(matchedFiltersHolder.stream().map(f -> f.getFilter().getClass().getName()).iterator());
                String headerNames = request instanceof HttpServletRequest ?
                        Joiner.on(", ").join(new EnumerationIterator(((HttpServletRequest) request).getHeaderNames())) :
                        "?HEADERS?";
                log.debug("Ignored filters for request {} {}:\n{}\n\nHeaders: {}", method, uri, filterNames,
                        headerNames);
            }
        }
        matchedFiltersHolder = priorityForceAnonymous(matchedFiltersHolder);
        return (matchedFiltersHolder.isEmpty()) ? null : matchedFiltersHolder.getFirst();
    }

    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
        for (Filter filter : authenticationFilters) {
            filter.init(filterConfig);
        }
    }

    /**
     * This function returns a list of ArtifactoryFilterHolder that force anonymous authentication. If no anonymous
     * authentication is forced, the function will return what it gets.
     */
    private List<ArtifactoryFilterHolder> priorityForceAnonymous(List<ArtifactoryFilterHolder> matchedFiltersHolder) {
        List<ArtifactoryFilterHolder> result =
                matchedFiltersHolder.stream().filter(ArtifactoryFilterHolder::isForcedAnonymous).toList();
        return result.isEmpty() ? matchedFiltersHolder : result;
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, String loginIdentifier,
            ArtifactoryFilterHolder filterHolder, final FilterChain servletChain) throws IOException, ServletException {
        FilterChain chainWithAdditive = (request, response) -> {
            try {
                AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
                addonsManager.addonByType(PluginsAddon.class)
                        .executeAdditiveRealmPlugins(new HttpArtifactoryRequest((HttpServletRequest) request));
                if (ArtifactoryHome.get().getSysConfig().helper().exposeApplicationHeaders() ||
                        (isVersionHeaderNeeded((HttpServletResponse) response) && !UserInfo.isAnonymous(
                                SecurityContextHolder.getContext().getAuthentication().getName()))) {
                    ServletUtils.addSensitiveHeadersToResponse(request, response,
                            RequestUtils.getArtifactoryContext(filterConfig.getServletContext()));
                }
                servletChain.doFilter(request, response);
            } catch (AuthenticationException e) {
                ContextHelper.get().beanForType(BasicAuthenticationEntryPoint.class)
                        .commence((HttpServletRequest) request, (HttpServletResponse) response, e);
            }
        };

        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        long accessTime = HttpUtils.getSessionAccessTime(httpServletRequest);

        try {
            filterHolder.doFilter(servletRequest, servletResponse, chainWithAdditive);

            HttpServletResponse response = (HttpServletResponse) servletResponse;

            if (response.getStatus() == 401) {
                log.debug("Filter responded with code {}", response.getStatus());
                interceptLoginFailure(loginIdentifier, accessTime);
            } else if (response.getStatus() < 400 && response.getStatus() >= 200) {
                log.debug("Filter responded with code {}, registering authentication success!", response.getStatus());
                // intercept successful login
                if (!Strings.isNullOrEmpty(loginIdentifier)) {
                    getSecurityService().interceptLoginSuccess(loginIdentifier);
                }
            } else {
                log.debug("Filter responded with code {}, skipping result interception", response.getStatus());
            }
        } catch (IllegalArgumentException e) {
            String message = String.format("User authentication has failed due to %s", e.getMessage());
            log.warn(message, e);
            sendErrorResponse(httpServletResponse, SC_NOT_FOUND, message);
        } catch (AuthenticationException e) {
            log.debug("User authentication has failed", e);
            interceptLoginFailure(loginIdentifier, accessTime);
            authenticationEntryPoint.commence(httpServletRequest, httpServletResponse, e);
        }
    }

    public void destroy() {
        for (Filter filter : authenticationFilters) {
            filter.destroy();
        }
    }

    private void interceptLoginFailure(String loginIdentifier, long accessTime) {
        if (!Strings.isNullOrEmpty(loginIdentifier)) {
            log.debug("registering authentication failure!");
            getSecurityService().interceptLoginFailure(loginIdentifier, accessTime);
        }
    }

    private SecurityService getSecurityService() {
        if (securityService == null) {securityService = ContextHelper.get().beanForType(SecurityService.class);}
        return securityService;
    }

    private boolean isVersionHeaderNeeded(HttpServletResponse response) {
        return response.getStatus() != 401 && response.getStatus() < 500;
    }

    /**
     * The function checks the type of the filter and checks if it's ArtifactoryAuthenticationContextFilter or
     * ArtifactoryAuthenticationFilter.
     *
     * If the filter is from type ArtifactoryAuthenticationContextFilter and the context was back as anonymous
     * allowed or the request is with authentication params or an error was returned, so the filter holder of
     * ArtifactoryFilterContextHolderImpl will be return with the filter and the context of the request inside the
     * filter holder.
     *
     * If the filter is from type ArtifactoryAuthenticationFilter and was accepted, the filter type of
     * ArtifactoryFilterHolderImpl will be return with the filter inside.
     */
    @Nullable
    private ArtifactoryFilterHolder acceptFilter(Filter filter, ServletRequest request, ServletResponse response)
            throws IOException {
        if (filter instanceof ArtifactoryAuthenticationContextFilter contextAuthenticationFilter) {
            AuthenticationContext context = contextAuthenticationFilter.getAuthenticationContext(request, response);
            if (context.getAuthenticationStatus() != NOT_HANDLED) {
                return new ArtifactoryFilterContextHolderImpl(contextAuthenticationFilter, context);
            }
        }

        if (filter instanceof ArtifactoryAuthenticationFilter authenticationFilter &&
                authenticationFilter.acceptFilter(request)) {
            return new ArtifactoryFilterHolderImpl(authenticationFilter);
        }
        return null;
    }
}
