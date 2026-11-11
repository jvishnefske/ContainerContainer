package org.artifactory.ui.rest.resource.admin.monitoring.federated;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.event.migration.EventTableRepoKeyMigrationImpl;
import org.artifactory.federation.FederatedStatusService;
import org.artifactory.federation.JpdFederationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

import static org.apache.http.HttpStatus.SC_OK;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path("federation/monitor")
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Slf4j
public class FederatedMonitorUIResource {

    private FederatedStatusService federatedStatusService;
    private EventTableRepoKeyMigrationImpl eventTableRepoKeyMigrationImpl;

    @Autowired
    public void setFederatedStatusService(FederatedStatusService federatedStatusService) {
        this.federatedStatusService = federatedStatusService;
    }

    @Autowired
    public void setEventTableRepoKeyMigrationImpl(EventTableRepoKeyMigrationImpl eventTableRepoKeyMigrationImpl) {
        this.eventTableRepoKeyMigrationImpl = eventTableRepoKeyMigrationImpl;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllJPDFederationStatus() {
        if (!eventTableRepoKeyMigrationImpl.getIfEventTableRepoKeyMigrationFinished()) {
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .entity("Monitoring is unavailable, migration in progress").build();
        }
        List<JpdFederationStatus> jpdFederationStatuses = federatedStatusService.getAllJpdFederationStatus();
        return Response.status(SC_OK).entity(jpdFederationStatuses).build();
    }
}