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

package org.artifactory.webapp.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ArtifactoryContext;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;

/**
 * Delegating filter from a delayed context filter to {@link SessionRepositoryFilter}
 *
 * @author Shay Yaakov
 */
@Slf4j
public class SessionFilter extends DelayedFilterBase {

    private SessionRepositoryFilter delegate;
    private boolean isDestroyed;

    @Override
    public void initLater(FilterConfig filterConfig) throws ServletException {
        log.info("Initializing session filter");
        ServletContext servletContext = filterConfig.getServletContext();
        ArtifactoryContext context = RequestUtils.getArtifactoryContext(servletContext);
        SessionRepository sessionRepository = context.beanForType(SessionRepository.class);
        delegate = new SessionRepositoryFilter(sessionRepository);
    }

    @Override
    public void destroy() {
        isDestroyed = true;
        log.info("Into destroy method of session filter");
        if (delegate != null) {
            log.info("Destroying session filter");
            delegate.destroy();
        }
    }

    @Override
    public void doFilter(final ServletRequest req, final ServletResponse resp, final FilterChain chain)
            throws IOException, ServletException {
        if (shouldSkipFilter(req)) {
            chain.doFilter(req, resp);
            return;
        }
        if (isDestroyed) {
            log.warn("Session filter is destroyed, skipping filter chain");
            return;
        } else if (delegate == null) {
            if (shouldSkipFilterChain(req)) {
                log.warn("Session filter is readiness or PreStop request, skipping filter chain");
                sendUnavailableResponse((HttpServletResponse) resp, "Session filter is not initialized!");
                return;
            }
            throw new ServletException(
                    "Session filter is not initialized! Thread: " + Thread.currentThread().getName());
        }
        delegate.doFilter(req, resp, chain);
    }

    private boolean shouldSkipFilterChain(final ServletRequest req) {
        return (req instanceof HttpServletRequest servletRequest) &&
                (isReadinessRequest(servletRequest) || isPreStopRequest(servletRequest));
    }
}