package org.artifactory.rest.resource.devopsagent.requestlog;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.apache.http.HttpStatus;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.constant.DevopsAgentRestConstants;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.rest.resource.devopsagent.requestlog.dto.OutboundRequestLogEntryModel;
import org.artifactory.rest.resource.devopsagent.requestlog.dto.RequestLogEntryModel;
import org.artifactory.rest.resource.devopsagent.requestlog.dto.RequestLogListResponseModel;
import org.artifactory.rest.resource.devopsagent.requestlog.dto.RequestLogModelMapper;
import org.artifactory.rest.resource.devopsagent.requestlog.dto.RequestLogQueryParamParser;
import org.artifactory.rest.resource.devopsagent.requestlog.dto.TraceCorrelationResponseModel;
import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchResult;
import org.artifactory.storage.db.devopsagent.requestlog.model.TraceResult;
import org.artifactory.storage.db.devopsagent.requestlog.service.OutboundRequestLogService;
import org.artifactory.storage.db.devopsagent.requestlog.service.RequestLogException;
import org.artifactory.storage.db.devopsagent.requestlog.service.RequestLogService;
import org.springframework.stereotype.Component;

/**
 * Inbound and outbound HTTP request-log endpoints — agentic / AI-troubleshooting consumers only.
 * Auto-discovered by {@code JerseyApplication}'s package scan. Returns 501 when the gate
 * ({@code RequestLogService#isFeatureEnabled()}) is closed.
 */
@Component
@Path(DevopsAgentRestConstants.PATH_REQUESTS)
@RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
@Slf4j
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
public class RequestLogsResource {

    @GET
    @Path(DevopsAgentRestConstants.PATH_REQUESTS_INBOUND)
    @Produces(MediaType.APPLICATION_JSON)
    public Response searchInbound(
            @QueryParam("time_from") String timeFrom,
            @QueryParam("time_to") String timeTo,
            @QueryParam("min_duration_ms") String minDurationMs,
            @QueryParam("search") String search,
            @QueryParam("match_all") String matchAll,
            @QueryParam("limit") String limit,
            @QueryParam("offset") String offset,
            @QueryParam("cursor") String cursor) {
        try {
            SearchCriteria criteria = RequestLogQueryParamParser.parse(
                    timeFrom, timeTo, minDurationMs, /* repoName */ null,
                    search, matchAll, limit, offset, cursor);
            SearchResult<RequestLogRow> result = service().searchInbound(criteria);
            RequestLogListResponseModel<RequestLogEntryModel> body = new RequestLogListResponseModel<>(
                    RequestLogModelMapper.toInboundModels(result.rows()),
                    result.hasMore(),
                    result.nextCursor() == null ? null : result.nextCursor().encode());
            log.debug("agentic_api.request_logs.search_inbound rows={} hasMore={}",
                    body.logs().size(), body.hasMore());
            return Response.ok(body).build();
        } catch (RequestLogException e) {
            return toErrorResponse(e);
        }
    }

    @GET
    @Path(DevopsAgentRestConstants.PATH_REQUESTS_OUTBOUND)
    @Produces(MediaType.APPLICATION_JSON)
    public Response searchOutbound(
            @QueryParam("time_from") String timeFrom,
            @QueryParam("time_to") String timeTo,
            @QueryParam("min_duration_ms") String minDurationMs,
            @QueryParam("repo_name") String repoName,
            @QueryParam("search") String search,
            @QueryParam("match_all") String matchAll,
            @QueryParam("limit") String limit,
            @QueryParam("offset") String offset,
            @QueryParam("cursor") String cursor) {
        try {
            SearchCriteria criteria = RequestLogQueryParamParser.parse(
                    timeFrom, timeTo, minDurationMs, repoName,
                    search, matchAll, limit, offset, cursor);
            SearchResult<OutboundRequestLogRow> result = outboundService().searchOutbound(criteria);
            RequestLogListResponseModel<OutboundRequestLogEntryModel> body =
                    new RequestLogListResponseModel<>(
                            RequestLogModelMapper.toOutboundModels(result.rows()),
                            result.hasMore(),
                            result.nextCursor() == null ? null : result.nextCursor().encode());
            log.debug("agentic_api.request_logs.search_outbound rows={} hasMore={}",
                    body.logs().size(), body.hasMore());
            return Response.ok(body).build();
        } catch (RequestLogException e) {
            return toErrorResponse(e);
        }
    }

    private static final long TRACE_WINDOW_DEFAULT_MS = 24L * 60L * 60L * 1000L;

    @GET
    @Path(DevopsAgentRestConstants.PATH_REQUESTS_TRACE + "/{traceId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response trace(
            @PathParam("traceId") String traceId,
            @QueryParam("time_from") String timeFrom,
            @QueryParam("time_to") String timeTo) {
        try {
            Long from = RequestLogQueryParamParser.parseOptionalLong(timeFrom, "time_from");
            Long to = RequestLogQueryParamParser.parseOptionalLong(timeTo, "time_to");
            TraceResult result = service().findTrace(traceId, from, to);
            long now = System.currentTimeMillis();
            long defaultedFrom = from != null ? from : now - TRACE_WINDOW_DEFAULT_MS;
            long defaultedTo = to != null ? to : now;
            List<OutboundRequestLogEntryModel> outboundModels =
                    RequestLogModelMapper.toOutboundModels(result.outbound());
            TraceCorrelationResponseModel body = new TraceCorrelationResponseModel(
                    traceId,
                    defaultedFrom,
                    defaultedTo,
                    RequestLogModelMapper.toInboundModels(result.inbound()),
                    result.truncatedInbound(),
                    outboundModels,
                    result.truncatedOutbound());
            log.debug("agentic_api.request_logs.trace traceId={} inbound={} outbound={}",
                    traceId, body.inbound().size(), body.outbound().size());
            return Response.ok(body).build();
        } catch (RequestLogException e) {
            return toErrorResponse(e);
        }
    }

    private static Response toErrorResponse(RequestLogException e) {
        if (e.getStatusCode() >= 500) {
            log.error("agentic_api.request_logs.internal_error", e);
        } else if (e.getStatusCode() == HttpStatus.SC_NOT_IMPLEMENTED) {
            log.info("agentic_api.request_logs.disabled reason={}", e.getMessage());
        } else {
            log.warn("agentic_api.request_logs.validation_failed reason={}", e.getMessage());
        }
        return Response.status(e.getStatusCode())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorResponse(e.getStatusCode(), e.getMessage()))
                .build();
    }

    private static RequestLogService service() {
        return ContextHelper.get().beanForType(RequestLogService.class);
    }

    private static OutboundRequestLogService outboundService() {
        return ContextHelper.get().beanForType(OutboundRequestLogService.class);
    }
}
