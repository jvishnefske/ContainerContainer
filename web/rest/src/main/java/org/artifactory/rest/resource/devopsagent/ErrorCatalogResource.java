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

package org.artifactory.rest.resource.devopsagent;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.resource.devopsagent.dto.ErrorCatalogEventsResponse;
import org.artifactory.rest.resource.devopsagent.dto.ErrorCatalogSearchResponse;
import org.artifactory.rest.services.devopsagent.ErrorCatalogEventsRequest;
import org.artifactory.rest.services.devopsagent.ErrorCatalogRestService;
import org.artifactory.rest.services.devopsagent.ErrorCatalogSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Agentic read surface for the deduplicated error_catalog templates table (RTDEV-85639,
 * api-documentation v2). Top-level JAX-RS resource mounted at {@code /api/devopsagent/errors}.
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path("devopsagent/errors")
@Slf4j
@RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
public class ErrorCatalogResource {

    private final ErrorCatalogRestService service;

    @Autowired
    public ErrorCatalogResource(ErrorCatalogRestService service) {
        this.service = service;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(
            @QueryParam("id") String idsCsv,
            @QueryParam("severity") String severitiesCsv,
            @QueryParam("logger_class") String loggerClassesCsv,
            @QueryParam("exception_class") String exceptionClassesCsv,
            @QueryParam("occurrence_count_from") Long occurrenceCountFrom,
            @QueryParam("occurrence_count_to") Long occurrenceCountTo,
            @QueryParam("first_occurrence_from") Long firstOccurrenceFrom,
            @QueryParam("first_occurrence_to") Long firstOccurrenceTo,
            @QueryParam("last_occurrence_from") Long lastOccurrenceFrom,
            @QueryParam("last_occurrence_to") Long lastOccurrenceTo,
            @QueryParam("search") String search,
            @QueryParam("match_all") @DefaultValue("false") boolean matchAll,
            @QueryParam("limit") @DefaultValue("1000") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {
        ErrorCatalogSearchRequest request = new ErrorCatalogSearchRequest(
                idsCsv, severitiesCsv, loggerClassesCsv, exceptionClassesCsv,
                occurrenceCountFrom, occurrenceCountTo,
                firstOccurrenceFrom, firstOccurrenceTo,
                lastOccurrenceFrom, lastOccurrenceTo,
                search, matchAll, limit, offset);
        ErrorCatalogSearchResponse body = service.search(request);
        return Response.ok(body).build();
    }

    /**
     * Raw events drill-down — agentic surface introduced by RTDEV-86991, api-documentation v2.
     * Class-level {@link RolesAllowed} on this resource also guards this method; intentionally no
     * per-method override (see api-doc v2 § 3.1 and verification v1 item #24).
     */
    @GET
    @Path("events")
    @Produces(MediaType.APPLICATION_JSON)
    public Response events(
            @QueryParam("template_ids") String templateIdsCsv,
            @QueryParam("occurred_from") Long occurredFrom,
            @QueryParam("occurred_to") Long occurredTo,
            @QueryParam("page_token") String pageToken,
            @QueryParam("limit") @DefaultValue("100") int limit) {
        ErrorCatalogEventsRequest request = new ErrorCatalogEventsRequest(
                templateIdsCsv, occurredFrom, occurredTo, pageToken, limit);
        ErrorCatalogEventsResponse body = service.fetchEvents(request);
        return Response.ok(body).build();
    }
}
