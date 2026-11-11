package org.artifactory.rest.resource.trash;

import lombok.NonNull;
import org.apache.http.HttpStatus;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.repo.service.trash.rdtt.RemoveDirsTrashcanTimeRequestModel;
import org.artifactory.repo.service.trash.rdtt.RemoveDirsTrashcanTimeService;
import org.jfrog.storage.common.prune.PruneResponseModel;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.Objects;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path("deleteDirectoryTrashProperty")
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
public class RemoveDirsTrashcanTimeResource {
    private final RemoveDirsTrashcanTimeService service;

    @Inject
    RemoveDirsTrashcanTimeResource(@NonNull RemoveDirsTrashcanTimeService service) {
        this.service = service;
    }

    @POST
    @Path("start")
    public Response startRemoveDirsTrashcanTimeService(
            @Nullable @Valid RemoveDirsTrashcanTimeRequestModel request) {
        request = Objects.requireNonNullElse(request, RemoveDirsTrashcanTimeRequestModel.EMPTY);
        service.execute(request.getBatchSize(), request.getPauseBetweenBatchesMs(), request.getBatchesCount());
        return Response.status(HttpStatus.SC_ACCEPTED)
                .entity(new PruneResponseModel("Remove Directories Trashcan Time task has been submitted"))
                .build();
    }
}
