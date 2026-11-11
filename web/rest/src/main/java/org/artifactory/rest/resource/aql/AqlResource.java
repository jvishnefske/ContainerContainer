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

package org.artifactory.rest.resource.aql;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.AqlTooManyRequestsException;
import org.artifactory.aql.model.AqlTrackingInfo;
import org.artifactory.aql.result.AqlRestResult;
import org.artifactory.common.ConstantValues;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author Gidi Shabat
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(AqlResource.PATH_ROOT)
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@ActivityDetails(group = RestApiGroup.SEARCH_AQL_API)
@Slf4j
public class AqlResource {
    public static final String PATH_ROOT = "search/aql";

    @Autowired
    private SecurityService securityService;

    @Autowired
    private AqlService aqlService;

    @Context
    private HttpServletRequest request;

    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Consumes({MediaType.TEXT_PLAIN})
    public Response execute(@QueryParam("compact") boolean compact, String contentQuery) {
        // Only none anonymous users can access AQL
        if (securityService.isAnonymous()) {
            throw new AuthorizationRestException("Only non-anonymous users are allowed to access AQL queries\n");
        }
        //Try to load the query from URL params or attached file
        String query = getQuery(contentQuery);
        if (StringUtils.isBlank(query)) {
            log.warn("Couldn't find the query neither in the request URL and the attached file");
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse(Response.Status.BAD_REQUEST.getStatusCode(),
                            "Couldn't find the query neither in the request URL and the attached file"))
                    .build();
        }

        int querySizeLimit = ConstantValues.aqlSearchQuerySizeLimit.getInt();
        if (query.length() > querySizeLimit) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse(Response.Status.BAD_REQUEST.getStatusCode(),
                            String.format("AQL query is too long; please reduce the query length to less than %s chars",
                                    querySizeLimit)))
                    .build();
        }
        // Execute the query
        try {
            final AqlRestResult restResult = aqlService.executeQueryLazyWithLimit(query);
            // After success query execution prepare the result in stream.
            return aqlService.streamResponse(compact, restResult,
                    new AqlTrackingInfo(AqlResource.PATH_ROOT, query));
        } catch (AqlTooManyRequestsException e) {
            aqlService.computeQueryTemplate(query);
            return Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse(Response.Status.TOO_MANY_REQUESTS.getStatusCode(), "too many requests"))
                    .build();
        }
    }

    private String getQuery(String contentQuery) {
        try {
            String query = contentQuery;
            if (StringUtils.isBlank(query)) {
                // Try to find query in the attached params ()
                query = request.getParameterMap().get("query")[0];
            }
            return query;
        } catch (Exception e) {
            return null;
        }
    }
}
