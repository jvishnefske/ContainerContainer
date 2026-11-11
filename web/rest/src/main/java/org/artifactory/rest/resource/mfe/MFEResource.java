package org.artifactory.rest.resource.mfe;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpStatus;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.web.FeatureFlagService;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.rest.resource.mfe.server.MFEFileServer;
import org.springframework.stereotype.Component;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Component
@Path("v1/mfe")
@RequiredArgsConstructor
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
public class MFEResource {

    private final MFEFileServer mfeFileServer;
    private final FeatureFlagService featureFlagService;
    private final MfeProjectRegistry mfeProjectRegistry;

    @GET
    @Path("{mfeName}/version")
    @Produces(APPLICATION_JSON)
    public Response getMfeVersion(@PathParam("mfeName") String mfeName) {
        // Validate that the MFE name is valid and exists in the registry
        MfeName mfeNameEnum = MfeName.fromValue(mfeName);
        if (mfeNameEnum == null || mfeProjectRegistry.findProjectByMfeName(mfeNameEnum) == null) {
            return Response.status(HttpStatus.SC_NOT_FOUND).entity("Could not find provided mfe name: " + mfeName)
                    .build();
        }
        try {
            String version = mfeFileServer.getMfeVersion(mfeName);
            String featureFlagsHash = featureFlagService.featureFlagsHash();
            VersionResponse versionResponse = new VersionResponse(version, featureFlagsHash);
            return Response.ok().entity(versionResponse).build();
        } catch (NotFoundException e) {
            return Response.status(HttpStatus.SC_NOT_FOUND).entity(e.getMessage()).build();
        }
    }
}
