package org.artifactory.rest.resource.system;

import org.artifactory.activity.ActivityDetails;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.throtlling.qrl.service.QueryRateLimiterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;

/**
 * @author Andrey Tashlitsky
 */
@Component
@Path(SystemRestConstants.PATH_QRL)
@RolesAllowed(AuthorizationService.ROLE_ADMIN)
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
public class QueryRateLimiterResource extends RateLimiterResource {

    static final String QRL_NAME = "Query rate limiter";

    @Autowired
    QueryRateLimiterResource(QueryRateLimiterService qrlService) {
        super(qrlService);
    }

    String getName() {
        return QRL_NAME;
    }
}
