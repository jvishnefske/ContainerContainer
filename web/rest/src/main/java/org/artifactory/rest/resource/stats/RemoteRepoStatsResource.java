package org.artifactory.rest.resource.stats;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.artifactory.api.rest.constant.StatsRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.remote.stats.RemoteRepoStatsQueryResult;
import org.artifactory.remote.stats.RemoteRepoStatsService;
import org.artifactory.rest.exception.ForbiddenWebAppException;
import org.artifactory.rest.resource.stats.dto.RemoteRepoStatsRequest;
import org.artifactory.rest.resource.stats.dto.RemoteRepoStatsResponse;
import org.artifactory.util.AolUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * REST resource exposing remote repository statistics via GET and POST endpoints.
 * GET uses query parameters; POST uses form-encoded parameters for long filter lists.
 *
 * @author Hussein Alshqerat
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(StatsRestConstants.PATH_ROOT)
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
public class RemoteRepoStatsResource {

    @Autowired
    private RemoteRepoStatsService remoteRepoStatsService;

    private void assertSaasDashboardUser() {
        if (!AolUtils.isAol()) {
            throw new ForbiddenWebAppException(
                    "This endpoint is only available in SaaS environments");
        }
        AolUtils.assertDashboardUser();
    }

    @GET
    @Path(StatsRestConstants.REMOTES)
    @Produces(MediaType.APPLICATION_JSON)
    public RemoteRepoStatsResponse queryRemoteStats(
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam("repoKeys") String repoKeys,
            @QueryParam("packageTypes") String packageTypes,
            @QueryParam("minErrorRate") Float minErrorRate,
            @QueryParam("statusPattern") String statusPattern,
            @QueryParam("includeStatusBreakdown") @DefaultValue("false") boolean includeStatusBreakdown,
            @QueryParam("includeDailyBreakdown") @DefaultValue("false") boolean includeDailyBreakdown,
            @QueryParam("sortBy") @DefaultValue("totalRequests") String sortBy,
            @QueryParam("order") @DefaultValue("desc") String order,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        assertSaasDashboardUser();
        RemoteRepoStatsRequest request = RemoteRepoStatsRequest.from(
                startDate, endDate, repoKeys, packageTypes, minErrorRate,
                statusPattern, includeStatusBreakdown, includeDailyBreakdown,
                sortBy, order, offset, limit);
        request.validate();
        RemoteRepoStatsQueryResult result =
                remoteRepoStatsService.queryStats(request.toDomainRequest());
        return RemoteRepoStatsResponseMapper.toResponse(result);
    }

    @POST
    @Path(StatsRestConstants.REMOTES)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public RemoteRepoStatsResponse queryRemoteStatsPost(
            @FormParam("startDate") String startDate,
            @FormParam("endDate") String endDate,
            @FormParam("repoKeys") String repoKeys,
            @FormParam("packageTypes") String packageTypes,
            @FormParam("minErrorRate") Float minErrorRate,
            @FormParam("statusPattern") String statusPattern,
            @FormParam("includeStatusBreakdown") @DefaultValue("false") boolean includeStatusBreakdown,
            @FormParam("includeDailyBreakdown") @DefaultValue("false") boolean includeDailyBreakdown,
            @FormParam("sortBy") @DefaultValue("totalRequests") String sortBy,
            @FormParam("order") @DefaultValue("desc") String order,
            @FormParam("offset") @DefaultValue("0") int offset,
            @FormParam("limit") @DefaultValue("50") int limit) {
        assertSaasDashboardUser();
        RemoteRepoStatsRequest request = RemoteRepoStatsRequest.from(
                startDate, endDate, repoKeys, packageTypes, minErrorRate,
                statusPattern, includeStatusBreakdown, includeDailyBreakdown,
                sortBy, order, offset, limit);
        request.validate();
        RemoteRepoStatsQueryResult result =
                remoteRepoStatsService.queryStats(request.toDomainRequest());
        return RemoteRepoStatsResponseMapper.toResponse(result);
    }
}
