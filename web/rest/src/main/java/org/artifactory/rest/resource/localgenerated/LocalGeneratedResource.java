package org.artifactory.rest.resource.localgenerated;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.cache.expirable.CacheExpiryImpl;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.annotation.Nonnull;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@Path("localgenerated/filter")
@RolesAllowed(AuthorizationService.ROLE_ADMIN)
public class LocalGeneratedResource {

    private final RepositoryService repositoryService;

    private final CacheExpiryImpl cacheExpiry;

    @Autowired
    public LocalGeneratedResource(RepositoryService repositoryService, CacheExpiryImpl cacheExpiry) {
        this.repositoryService = repositoryService;
        this.cacheExpiry = cacheExpiry;
    }

    @POST
    @Path("paths")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response filterPaths(@RequestBody LocalGeneratedPathsModel model) {
        String repoKey = model.repoKey();
        RepoType repoType = getRepoType(repoKey);
        List<String> filteredPaths = getPaths(model).stream()
                .filter(path -> !cacheExpiry.isLocalGenerated(repoType, repoKey, path))
                .toList();
        return Response.ok(new LocalGeneratedPathsModel(model.repoKey(), filteredPaths)).build();
    }

    @Nonnull
    private static List<String> getPaths(LocalGeneratedPathsModel model) {
        return Optional.ofNullable(model.paths())
                .orElseThrow(() -> new BadRequestException("No paths provided to check"));
    }

    private RepoType getRepoType(String repoKey) {
        RepoDescriptor descriptor = repositoryService.repoDescriptorByKey(repoKey);
        if (descriptor == null) {
            String msg = String.format("Repository '%s' does not exist, unable to check local generated", repoKey);
            throw new NotFoundException(msg);
        }
        return descriptor.getType();
    }
}
