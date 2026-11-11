package org.artifactory.rest.resource.upload;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.rest.filter.AllowedPermissionScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Internal-only upload endpoint. Writes are restricted to paths under a registered system
 * folder (e.g. {@code .evidence/}). Supported repo types: LOCAL, REMOTE (writes to the
 * {@code -cache} half), and FEDERATED (writes locally; federation replicates to mirrors).
 * VIRTUAL is rejected — see {@link InternalUploadServiceImpl} for details.
 *
 * <p>Access is gated on two levels:
 * <ul>
 *   <li><b>Boundary</b> — the caller must present an access token carrying the generic service
 *       scope {@value #INTERNAL_UPLOAD_WRITE} (enforced by {@code AllowedPermissionScopeDynamicFeature}).
 *       This keeps the endpoint reachable only by trusted internal services, since it bypasses the
 *       system-folder customer-upload block via {@link org.artifactory.repo.system.SystemFolderUploadMarker}.</li>
 *   <li><b>Fine-grained</b> — the shared upload pipeline still runs
 *       {@code assertValidDeployPathAndPermissions}, so the caller must also hold deploy permission
 *       on the target repository path, exactly like the standard deploy API.</li>
 * </ul>
 *
 * @author Fawaz Atwa
 */
@Path("internal/upload")
@Component
@Consumes(MediaType.APPLICATION_OCTET_STREAM)
@Produces(MediaType.APPLICATION_JSON)
@AllowedPermissionScope(InternalUploadResource.INTERNAL_UPLOAD_WRITE)
public class InternalUploadResource {

    /**
     * Generic internal-service write scope for system-folder uploads. Not tied to any specific
     * system folder (e.g. {@code .evidence}); any trusted internal service that needs to deposit
     * files into a system folder authenticates with this scope.
     */
    public static final String INTERNAL_UPLOAD_WRITE = "internal:upload:w";

    private final InternalUploadServiceImpl uploadService;

    @Autowired
    public InternalUploadResource(InternalUploadServiceImpl uploadService) {
        this.uploadService = uploadService;
    }

    /**
     * @param repoKey  logical key of the target repository — LOCAL or REMOTE. For remote repos,
     *                 do NOT include the {@code -cache} suffix; it is appended internally.
     *                 For local repos the key is used as-is.
     * @param filePath must start with a recognized system-folder prefix
     * @return {@code 201 Created} with an {@link InternalUploadResult} body describing the stored
     * artifact (resolved path, size, checksums)
     */
    @PUT
    @Path("/{repoKey}/{filePath: .+}")
    public Response upload(
            @PathParam("repoKey") String repoKey,
            @PathParam("filePath") String filePath,
            @NonNull InputStream content) throws RepoRejectException, IOException {
        InternalUploadResult result = uploadService.upload(repoKey, filePath, content);
        return Response.status(Response.Status.CREATED).entity(result).build();
    }
}
