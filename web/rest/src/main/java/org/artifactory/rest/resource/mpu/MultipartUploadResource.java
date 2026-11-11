package org.artifactory.rest.resource.mpu;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.mpu.MultipartStatusResult;
import org.artifactory.mpu.MultipartUploadService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.filter.AllowedPermissionScope;
import org.artifactory.util.HttpUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import java.io.IOException;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.artifactory.api.security.AuthorizationService.ROLE_ADMIN;
import static org.artifactory.api.security.AuthorizationService.ROLE_USER;
import static org.artifactory.mpu.MultipartUploadService.MPU_SCOPE;

/**
 * @author Andrey Tashlitsky
 */
@Component
@Path("v1/uploads")
@RequiredArgsConstructor
public class MultipartUploadResource {

    private final MultipartUploadService mpuService;

    @POST
    @Path("create")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({ROLE_ADMIN, ROLE_USER})
    public Response create(@QueryParam("repoKey") String repoKey, @QueryParam("repoPath") String repoPath,
            @QueryParam("partSizeMB") int partSizeMB) {
        assertRepoKeyAndPathNotNull(repoKey, repoPath);
        String token = mpuService.generateToken(repoKey, repoPath, partSizeMB);
        CreateResponse response = new CreateResponse(token);
        return Response.ok().entity(response).build();
    }

    @GET
    @Path("config")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({ROLE_ADMIN, ROLE_USER})
    public Response isSupported(@Context HttpServletRequest request) {
        String requestUserAgent = HttpUtils.getRequestUserAgent(request);
        if (StringUtils.isNotBlank(requestUserAgent) && requestUserAgent.startsWith("jfrog-cli-go/")) {
            String version = requestUserAgent.substring("jfrog-cli-go/".length());
            // Need to verify cli version since we had a bug which only admin users could have use the feature
            if (isVersionLessThanRequiredVersion(version)) {
                return Response.ok().entity(new ConfigResponse(false)).build();
            }
        }
        boolean isSupported = mpuService.isFeatureEnabled();
        ConfigResponse response = new ConfigResponse(isSupported);
        return Response.ok().entity(response).build();
    }

    private boolean isVersionLessThanRequiredVersion(String version) {
        String[] versionParts = version.split("\\.");
        String[] targetVersionParts = ConstantValues.mpuJfrogCliMinRequiredVersion.getString().split("\\.");
        try {
            for (int i = 0; i < Math.min(versionParts.length, targetVersionParts.length); i++) {
                int versionPart = Integer.parseInt(versionParts[i]);
                int targetVersionPart = Integer.parseInt(targetVersionParts[i]);
                if (versionPart < targetVersionPart) {
                    return true;
                } else if (versionPart > targetVersionPart) {
                    return false;
                }
            }
            return versionParts.length < targetVersionParts.length;
        } catch (Exception e) {
            return false;
        }
    }

    private void assertRepoKeyAndPathNotNull(String repoKey, String repoPath) {
        if (repoKey == null || repoPath == null) {
            throw new BadRequestException("Query param repoKey or repoPath is null");
        }
    }

    @POST
    @Path("urlPart")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @AllowedPermissionScope(MPU_SCOPE)
    public Response urlPart(@QueryParam("partNumber") Integer partNumber) {
        assertPartNumberNotNull(partNumber);
        String url = mpuService.urlPart(partNumber);
        UrlResponse urlResponse = new UrlResponse(url);
        return Response.ok().entity(urlResponse).build();
    }

    private void assertPartNumberNotNull(Integer partNumber) {
        if (partNumber == null) {
            throw new BadRequestException("Query param partNumber is null");
        }
    }

    @POST
    @Path("complete")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @AllowedPermissionScope(MPU_SCOPE)
    public Response complete(@Context HttpServletResponse response, @QueryParam("sha1") String sha1)
            throws IOException {
        assertSha1NotNull(sha1);
        mpuService.complete(response, sha1);
        return Response.accepted().build();
    }

    private void assertSha1NotNull(String sha1) {
        if (sha1 == null) {
            throw new BadRequestException("Query param sha1 is null");
        }
    }

    @POST
    @Path("status")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @AllowedPermissionScope(MPU_SCOPE)
    public Response status() {
        MultipartStatusResult status = mpuService.status();
        StatusResponse response;
        if (StringUtils.isBlank(status.message())) {
            response =
                    new StatusResponse(status.task().getStatus().getDisplayName(), status.task().getProgress(),
                            status.token());
        } else {
            response = new StatusResponse(status.task().getStatus().getDisplayName(), status.message());
        }
        return Response.ok().entity(response).build();
    }

    @POST
    @Path("abort")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @AllowedPermissionScope(MPU_SCOPE)
    public Response abort() {
        mpuService.abort();
        return Response.noContent().build();
    }
}
