package org.artifactory.rest.resource.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nullable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.storage.service.ProjectBinarySizeService;
import org.artifactory.storage.service.ProjectSizeEntry;
import org.artifactory.util.AolUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * REST resource for project binary storage
 *
 * @author Evgeny Kurtser
 */
@Component
@RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
@Path("internal/storage/projects")
@Slf4j
public class ProjectBinaryStorageResource {
    private final ProjectBinarySizeService projectBinaryStorageService;

    public ProjectBinaryStorageResource(ProjectBinarySizeService projectBinaryStorageService) {
        this.projectBinaryStorageService = projectBinaryStorageService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllProjectsBinaryStorage() {
        if (!checkOnlySuperuserAllowed()) {
            return Response.status(HttpStatus.SC_UNAUTHORIZED).build();
        }
        try {
            List<ProjectBinarySizeService.ProjectBinarySizeInfo> allInfo =
                    projectBinaryStorageService.getAllProjectsBinaryStorage();

            List<ProjectBinaryStorageModel> models = allInfo.stream()
                    .map(info -> new ProjectBinaryStorageModel(
                            info.project(),
                            info.projectSizeStatus(),
                            info.calculationTimestamp()
                    ))
                    .toList();

            ProjectsBinaryStorageModel response = new ProjectsBinaryStorageModel(models);
            return Response.ok(response).build();
        } catch (Exception e) {
            log.error("Failed to get all projects binary storage", e);
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .entity("Failed to retrieve projects binary storage: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("{projectKey}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProjectBinaryStorage(@PathParam("projectKey") String projectKey) {
        if (!checkOnlySuperuserAllowed()) {
            return Response.status(HttpStatus.SC_UNAUTHORIZED).build();
        }
        try {
            ProjectBinarySizeService.ProjectBinarySizeInfo info =
                    projectBinaryStorageService.getProjectBinarySizeInfo(projectKey);
            if (info == null) {
                return Response.status(HttpStatus.SC_NOT_FOUND)
                        .entity("Binary storage calculation not found for project: " + projectKey)
                        .build();
            }

            ProjectBinaryStorageModel model = new ProjectBinaryStorageModel(
                    info.project(),
                    info.projectSizeStatus(),
                    info.calculationTimestamp()
            );

            return Response.ok(model).build();
        } catch (Exception e) {
            log.error("Failed to get project binary storage for project: {}", projectKey, e);
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .entity("Failed to retrieve project binary storage: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Endpoint to trigger calculation of all projects binary storage.
     * This endpoint is considered an internal API to ProjectBinaryStorage, intended for testing purposes only
     * and should not be used by external code.
     */
    @VisibleForTesting
    @POST
    @Path("calculate")
    @Produces(MediaType.APPLICATION_JSON)
    public Response calculateProjectsBinaryStorage() {
        if (!checkOnlySuperuserAllowed()) {
            return Response.status(HttpStatus.SC_UNAUTHORIZED).build();
        }
        try {
            projectBinaryStorageService.calculateAllProjectsSizes();
            return Response.ok("Project binary storage calculation started").build();
        } catch (Exception e) {
            log.error("Failed to start project binary storage calculation", e);
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .entity("Failed to start project binary storage calculation: " + e.getMessage())
                    .build();
        }
    }

    private boolean checkOnlySuperuserAllowed() {
        if (ConstantValues.projectBinaryStorageCalculationEnforceDashboardUser.getBoolean()) {
            try {
                AolUtils.assertDashboardUser();
            } catch (BadRequestException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Model for all projects binary storage information
     * This record could have been private, but I prefer to keep it public to be consistent
     * with the idea that REST models are public.
     */
    public record ProjectsBinaryStorageModel(
            @JsonProperty("projects") List<ProjectBinaryStorageModel> projects
    ) {
    }

    /**
     * Model for project binary storage information.
     * This record could have been private, but I prefer to keep it public to be consistent
     * with the idea that REST models are public.
     *
     * @param project              the project key
     * @param status               status of the binary storage calculation
     * @param binariesSize         size in bytes if status is true, null otherwise
     * @param calculationTimestamp timestamp of when the size was calculated
     * @see ProjectSizeEntry.CalcStatus
     */
    public record ProjectBinaryStorageModel(
            @JsonProperty("project") String project,
            @JsonProperty("status") String status,
            @JsonProperty("binariesSize") @Nullable Long binariesSize,
            @JsonProperty("calculationTimestamp") long calculationTimestamp
    ) {
        public ProjectBinaryStorageModel(String project, ProjectSizeEntry projectSizeHolder, long calcTimestamp) {
            this(
                    project,
                    projectSizeHolder.status().getName(),
                    projectSizeHolder.isSuccess() ? projectSizeHolder.projectSize() : null,
                    calcTimestamp
            );
        }
    }
}