package org.artifactory.rest.resource.repositories;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.model.RepoConfig;
import org.jfrog.security.common.AuthorizationRoles;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Objects;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.artifactory.repo.system.SystemRepos.isSupportBundle;
import static org.artifactory.util.RepoPathUtils.isCache;
import static org.artifactory.util.RepoPathUtils.isTrash;

/**
 * A resource to manage all repository key pairs
 *
 * @author Omri Ziv
 */
@RolesAllowed({AuthorizationRoles.ROLE_ADMIN, AuthorizationRoles.ROLE_USER})
public class RepositoryKeyPairsResource {

    private static final String KEY_PAIR_DOES_NOT_EXISTS_MSG = "Key Pair does not exist";
    private final MutableCentralConfigDescriptor configDescriptor;
    private final RepositoryService repositoryService;
    private final String repoKey;

    public RepositoryKeyPairsResource(CentralConfigService centralConfigService,
            RepositoryService repositoryService, String repoKey) {
        this.configDescriptor = centralConfigService.getMutableDescriptor();
        this.repositoryService = repositoryService;
        this.repoKey = repoKey;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("promote")
    public Response promote() {
        RepoConfig repoConfig = getRepoConfig(repoKey);
        if (Objects.isNull(repoConfig)) {
            return Response.status(BAD_REQUEST).build();
        }
        if (Objects.isNull(repoConfig.getSecurityConfig().getSecondaryKeyPairRef())) {
            return Response.status(BAD_REQUEST)
                    .entity("Nothing to promote")
                    .build();
        }
        repoConfig.getSecurityConfig().promoteKeyPair();
        repositoryService.upsertRepoConfig(repoConfig);
        return Response.ok()
                .entity("Secondary Key Pair of repo '" + repoKey + "' promoted.")
                .build();
    }

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setKey(String keyName) {
        RepoConfig repoConfig = getRepoConfig(repoKey);
        if (Objects.isNull(repoConfig)) {
            return Response.status(BAD_REQUEST).build();
        }
        KeyPairDescriptor keyPairDescriptor = configDescriptor.getKeyPair(keyName);
        if (Objects.isNull(keyPairDescriptor)) {
            return Response.status(BAD_REQUEST)
                    .entity(KEY_PAIR_DOES_NOT_EXISTS_MSG)
                    .build();
        }
        if (keyPairDescriptor.getKey().equals(repoConfig.getSecurityConfig().getPrimaryKeyPairRef())) {
            return Response.ok()
                    .entity(String.format("Key Pair '%s' of repo '%s' was set as primary.", keyName, repoKey))
                    .build();
        }
        if (Objects.isNull(repoConfig.getSecurityConfig().getPrimaryKeyPairRef())) {
            repoConfig.getSecurityConfig().setPrimaryKeyPairRef(keyPairDescriptor.getKey());
            repositoryService.upsertRepoConfig(repoConfig);
            return Response.ok()
                    .entity(String.format("Key Pair '%s' of repo '%s' was set as primary.", keyName, repoKey))
                    .build();
        } else {
            repoConfig.getSecurityConfig().setSecondaryKeyPairRef(keyPairDescriptor.getKey());
            repositoryService.upsertRepoConfig(repoConfig);
            return Response.ok()
                    .entity(String.format("Key Pair '%s' of repo '%s' was set as secondary.", keyName, repoKey))
                    .build();
        }
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{keyName: .+}")
    public Response deleteKey(@PathParam("keyName") String keyName) {
        RepoConfig repoConfig = getRepoConfig(repoKey);
        if (Objects.isNull(repoConfig)) {
            return Response.status(BAD_REQUEST).build();
        }
        KeyPairDescriptor keyPairDescriptor = configDescriptor.getKeyPair(keyName);
        if (Objects.isNull(keyPairDescriptor)) {
            return Response.status(BAD_REQUEST)
                    .entity(KEY_PAIR_DOES_NOT_EXISTS_MSG)
                    .build();
        }
        if (keyPairDescriptor.getKey().equals(repoConfig.getSecurityConfig().getPrimaryKeyPairRef())) {
            repoConfig.getSecurityConfig().setPrimaryKeyPairRef(null);
            repoConfig.getSecurityConfig().promoteKeyPair();
            repositoryService.upsertRepoConfig(repoConfig);
            return Response.ok()
                    .entity(String.format("Primary Key Pair '%s' of repo '%s' removed.", keyName, repoKey))
                    .build();

        } else if (keyPairDescriptor.getKey().equals(repoConfig.getSecurityConfig().getSecondaryKeyPairRef())) {
            repoConfig.getSecurityConfig().setSecondaryKeyPairRef(null);
            repositoryService.upsertRepoConfig(repoConfig);
            return Response.ok()
                    .entity(String.format("Secondary Key Pair '%s' of repo '%s' removed.", keyName, repoKey))
                    .build();
        } else {
            return Response.status(BAD_REQUEST)
                    .entity("Repo '" + repoKey + "' Not containing Key Pair '" + keyName + "'")
                    .build();
        }
    }

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("primary")
    public Response setPrimary(String primaryKeyName) {
        RepoConfig repoConfig = getRepoConfig(repoKey);
        if (Objects.isNull(repoConfig)) {
            return Response.status(BAD_REQUEST).build();
        }
        KeyPairDescriptor keyPairDescriptor = configDescriptor.getKeyPair(primaryKeyName);
        if (Objects.isNull(keyPairDescriptor)) {
            return Response.status(BAD_REQUEST)
                    .entity(KEY_PAIR_DOES_NOT_EXISTS_MSG)
                    .build();
        }
        repoConfig.getSecurityConfig().setPrimaryKeyPairRef(keyPairDescriptor.getKey());
        repositoryService.upsertRepoConfig(repoConfig);
        return Response.ok()
                .entity("Key Pair '" + primaryKeyName + "' of repo '" + repoKey + "' was set as primary.")
                .build();
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("primary")
    public Response deletePrimary() {
        RepoConfig repoConfig = getRepoConfig(repoKey);
        if (Objects.isNull(repoConfig)) {
            return Response.status(BAD_REQUEST).build();
        }
        repoConfig.getSecurityConfig().setPrimaryKeyPairRef(null);
        repoConfig.getSecurityConfig().promoteKeyPair();
        repositoryService.upsertRepoConfig(repoConfig);
        return Response.ok()
                .entity("Primary Key Pair of repo '" + repoKey + "' deleted successfully.")
                .build();
    }


    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("secondary")
    public Response setSecondary(String secondaryKeyName) {
        RepoConfig repoConfig = getRepoConfig(repoKey);
        if (Objects.isNull(repoConfig)) {
            return Response.status(BAD_REQUEST).build();
        }
        KeyPairDescriptor keyPairDescriptor = configDescriptor.getKeyPair(secondaryKeyName);
        if (Objects.isNull(keyPairDescriptor)) {
            return Response.status(BAD_REQUEST)
                    .entity(KEY_PAIR_DOES_NOT_EXISTS_MSG)
                    .build();
        }
        if (Objects.isNull(repoConfig.getSecurityConfig().getPrimaryKeyPairRef())) {
            return Response.status(BAD_REQUEST)
                    .entity("Could not set secondary while primary is not set")
                    .build();
        }
        repoConfig.getSecurityConfig().setSecondaryKeyPairRef(keyPairDescriptor.getKey());
        repositoryService.upsertRepoConfig(repoConfig);
        return Response.ok()
                .entity("Key Pair '" + secondaryKeyName + "' of repo '" + repoKey + "' was set as secondary.")
                .build();
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("secondary")
    public Response deleteSecondary() {
        RepoConfig repoConfig = getRepoConfig(repoKey);
        if (Objects.isNull(repoConfig)) {
            return Response.status(BAD_REQUEST).build();
        }
        repoConfig.getSecurityConfig().setSecondaryKeyPairRef(null);
        repositoryService.upsertRepoConfig(repoConfig);
        return Response.ok()
                .entity("Secondary Key Pair of repo '" + repoKey + "' deleted successfully.")
                .build();
    }

    @GET
    @Path("primary/public")
    @Produces(MediaType.TEXT_PLAIN)
    @RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
    public Response getPrimaryPublicKey() {
        RepoConfig repoConfig = getRepoConfig(repoKey);
        if (Objects.isNull(repoConfig)) {
            return Response.status(BAD_REQUEST).build();
        }
        String primaryKeyPairRef = repoConfig.getSecurityConfig().getPrimaryKeyPairRef();
        if (Objects.isNull(primaryKeyPairRef)) {
            return Response.status(BAD_REQUEST)
                    .entity("Repo '" + repoKey + "' Not containing primary Key Pair")
                    .build();

        }
        KeyPairDescriptor keyPair = configDescriptor.getKeyPair(primaryKeyPairRef);
        if (Objects.isNull(keyPair)) {
            return Response.status(BAD_REQUEST).entity(KEY_PAIR_DOES_NOT_EXISTS_MSG).build();
        }

        return Response.ok()
                .header("Content-Disposition",
                        "attachment; filename=" + keyPair.getAliasWithExtension())
                .entity(keyPair.getPublicKey()).build();
    }

    @GET
    @Path("secondary/public")
    @Produces(MediaType.TEXT_PLAIN)
    @RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
    public Response getSecondaryPublicKey() {
        RepoConfig repoConfig = getRepoConfig(repoKey);
        if (Objects.isNull(repoConfig)) {
            return Response.status(BAD_REQUEST).build();
        }
        String secondaryKeyPairRef = repoConfig.getSecurityConfig().getSecondaryKeyPairRef();
        if (Objects.isNull(secondaryKeyPairRef)) {
            return Response.status(BAD_REQUEST)
                    .entity("Repo '" + repoKey + "' Not containing secondary Key Pair")
                    .build();

        }
        KeyPairDescriptor keyPair = configDescriptor.getKeyPair(secondaryKeyPairRef);
        if (Objects.isNull(keyPair)) {
            return Response.status(BAD_REQUEST).entity(KEY_PAIR_DOES_NOT_EXISTS_MSG).build();
        }
        return Response.ok()
                .header("Content-Disposition",
                        "attachment; filename=" + keyPair.getAliasWithExtension())
                .entity(keyPair.getPublicKey()).build();
    }

    private RepoConfig getRepoConfig(String repoKey) {
        if ((isTrash(repoKey) || isSupportBundle(repoKey) || isCache(repoKey))) {
            return null;
        }
        return repositoryService.getRepoConfigByKey(repoKey);
    }
}