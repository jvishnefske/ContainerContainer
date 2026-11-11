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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.artifactory.api.context.ArtifactoryContext;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.BlockingQueue;

import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.artifactory.api.rest.constant.SystemRestConstants.PATH_READINESS;

/**
 * @author Yoav Landman
 */
public abstract class DelayedFilterBase implements DelayedInit, Filter {

    private FilterConfig filterConfig;
    private static final String PRESTOP_PROBES_URL = "/artifactory/api/v1/system/preStop";

    @Override
    @SuppressWarnings({"unchecked"})
    public final void init(FilterConfig filterConfig) throws ServletException {
        BlockingQueue<Filter> waiters = (BlockingQueue<Filter>) filterConfig.getServletContext()
                .getAttribute(APPLICATION_CONTEXT_LOCK_KEY);
        this.filterConfig = filterConfig;
        if (waiters != null) {
            waiters.add(this);
        } else {
            //Servlet 2.5 lazy filter initing
            delayedInit();
        }
    }

    @Override
    public void delayedInit() throws ServletException {
        initLater(filterConfig);
    }

    private String getFilterName() {
        return filterConfig.getFilterName();
    }

    /**
     * If {@link DelayedInit#FILTER_SHORTCUT_ATTR} is set check if the current filter should be skipped.
     */
    boolean shouldSkipFilter(final ServletRequest req) {
        String filterShortcut = (String) req.getAttribute(FILTER_SHORTCUT_ATTR);
        return filterShortcut != null && !getFilterName().equals(filterShortcut);
    }

    protected abstract void initLater(FilterConfig config) throws ServletException;

    @Nullable
    protected ArtifactoryContext getArtifactoryContext() {
        return filterConfig != null ? RequestUtils.getArtifactoryContext(filterConfig.getServletContext()) : null;
    }

    public boolean isReadinessRequest(@Nonnull HttpServletRequest request) {
        return PATH_READINESS.equals(request.getRequestURI());
    }

    public boolean isPreStopRequest(@Nonnull HttpServletRequest request) {
        return PRESTOP_PROBES_URL.equals(request.getRequestURI());
    }

    public void sendUnavailableResponse(HttpServletResponse response, String message) throws IOException {
        String jsonString = "{\"code\": \"UNAVAILABLE\", \"message\": \"" + message + "\"}";
        response.setStatus(SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        out.println(jsonString);
        out.close();
    }
}