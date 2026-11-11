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
package org.artifactory.rest.resource.system;

import org.artifactory.activity.ActivityDetails;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.metric.DatabaseMetrics;
import org.jfrog.storage.metric.MetricsDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Resource that provides and control various metrics.
 *
 * @author Yossi Shaul
 */
@Component
@Path(SystemRestConstants.PATH_METRICS)
@RolesAllowed(AuthorizationService.ROLE_ADMIN)
@ActivityDetails(group = RestApiGroup.SYSTEM_INFO_API)
public class MetricsResource {
    private static final Logger log = LoggerFactory.getLogger(MetricsResource.class);

    @Qualifier("dataSource")
    private DataSource dataSource;
    private ArtifactoryJdbcHelper jdbcHelper;

    @Autowired
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Autowired
    public void setJdbcHelper(ArtifactoryJdbcHelper jdbcHelper) {
        this.jdbcHelper = jdbcHelper;
    }

    /**
     * @return Summary report of the sql metrics.
     */
    @GET
    @Path("db")
    @Produces({MediaType.TEXT_PLAIN})
    public Response getDbMetrics() {
        if (isResourceBlocked()) {
            return Response.status(HttpServletResponse.SC_UNAUTHORIZED).build();
        }

        Optional<DatabaseMetrics> metrics = getDbMetricsIfEnabled();
        if (metrics.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            String textReport = metrics.get().textReport();
            return Response.ok().entity(textReport).build();
        }
    }

    /**
     * Enables database metrics.
     */
    @PUT
    @Path("db/enable")
    @Produces({MediaType.TEXT_PLAIN})
    public Response enableDbMetrics() {
        if (isResourceBlocked()) {
            return Response.status(HttpServletResponse.SC_UNAUTHORIZED).build();
        }

        Optional<DatabaseMetrics> metrics = getDbMetricsIfEnabled();
        if (metrics.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            metrics.get().enableMetrics();
            return Response.noContent().build();
        }
    }

    /**
     * Disables database metrics.
     */
    @PUT
    @Path("db/disable")
    @Produces({MediaType.TEXT_PLAIN})
    public Response disableDbMetrics() {
        if (isResourceBlocked()) {
            return Response.status(HttpServletResponse.SC_UNAUTHORIZED).build();
        }

        Optional<DatabaseMetrics> metrics = getDbMetricsIfEnabled();
        if (metrics.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            metrics.get().disableMetrics();
            return Response.noContent().build();
        }
    }

    /**
     * @return Resets the database metrics.
     * @see DatabaseMetrics#reset()
     */
    @PUT
    @Path("db/reset")
    public Response resetDbMetrics() {
        if (isResourceBlocked()) {
            return Response.status(HttpServletResponse.SC_UNAUTHORIZED).build();
        }

        Optional<DatabaseMetrics> metrics = getDbMetricsIfEnabled();
        if (metrics.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            metrics.get().reset();
            return Response.noContent().build();
        }
    }

    /**
     * @return Summary report of the sql metrics.
     */
    @GET
    @Path("sql")
    @Produces({MediaType.TEXT_PLAIN})
    public Response getSqlMetrics() {
        if (isResourceBlocked()) {
            return Response.status(HttpServletResponse.SC_UNAUTHORIZED).build();
        }

        String report = jdbcHelper.getTracer().report();
        return Response.ok().entity(report).build();
    }

    /**
     * Enables sql tracing.
     */
    @PUT
    @Path("sql/enable")
    @Produces({MediaType.TEXT_PLAIN})
    public Response enableSqlTracing() {
        if (isResourceBlocked()) {
            return Response.status(HttpServletResponse.SC_UNAUTHORIZED).build();
        }

        jdbcHelper.getTracer().enableTracing();
        return Response.noContent().build();
    }

    /**
     * Disables sql tracing.
     */
    @PUT
    @Path("sql/disable")
    @Produces({MediaType.TEXT_PLAIN})
    public Response disableSqlTracing() {
        if (isResourceBlocked()) {
            return Response.status(HttpServletResponse.SC_UNAUTHORIZED).build();
        }

        jdbcHelper.getTracer().disableTracing();
        return Response.noContent().build();
    }

    /**
     * Resets the sql tracing.
     */
    @PUT
    @Path("sql/reset")
    @Produces({MediaType.TEXT_PLAIN})
    public Response resetSqlTracing() {
        if (isResourceBlocked()) {
            return Response.status(HttpServletResponse.SC_UNAUTHORIZED).build();
        }

        jdbcHelper.getTracer().resetTracing();
        return Response.noContent().build();
    }

    private Optional<DatabaseMetrics> getDbMetricsIfEnabled() {
        DatabaseMetrics metrics = null;
        try {
            if (dataSource.isWrapperFor(MetricsDataSource.class)) {
                metrics = dataSource.unwrap(MetricsDataSource.class).getMetrics();
            }
        } catch (SQLException e) {
            log.error("Failed to get database metrics: {}", e.getMessage());
            log.debug("Failed to get database metrics", e);
        }
        return Optional.ofNullable(metrics);
    }

    private boolean isResourceBlocked() {
        AolService aolService = ContextHelper.get().beanForType(AolService.class);
        return aolService.isAol() && !aolService.isDashboardUser();
    }
}