package org.artifactory.rest.resource.release;

import com.jfrog.distribution.handler.acl.bridge.DistributionHandlerBridgeToModule;
import lombok.NonNull;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path(SystemRestConstants.JFROG_RELEASE)
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class TargetBundleRetentionResource {

    private final DistributionHandlerBridgeToModule bridgeToModule;

    @Inject
    TargetBundleRetentionResource(@NonNull DistributionHandlerBridgeToModule bridgeToModule) {
        this.bridgeToModule = bridgeToModule;
    }

    @Path("/target/retention/propagate")
    @RolesAllowed({HaRestConstants.ROLE_HA})
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    @POST
    @NonNull
    public Response propagateOperation(@NonNull String operationModel) {
        String result = bridgeToModule.onPropagated(operationModel);
        return Response.ok().entity(result).build();
    }
}


