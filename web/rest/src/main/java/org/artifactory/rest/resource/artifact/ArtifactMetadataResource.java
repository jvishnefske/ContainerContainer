/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.artifactory.rest.resource.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.util.RestUtils;
import org.artifactory.rest.util.PATCH;
import org.jfrog.common.JsonUtils;
import org.jfrog.common.StringList;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.artifactory.api.rest.constant.ArtifactRestConstants.METADATA_ROOT;
import static org.artifactory.api.rest.constant.ArtifactRestConstants.PATH_PARAM;

/**
 * @author dudim
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(METADATA_ROOT + "/{" + PATH_PARAM + ": .+}")
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@ActivityDetails(group = RestApiGroup.ARTIFACT_API)
@Slf4j
public class ArtifactMetadataResource {

    @PathParam(PATH_PARAM)
    String path;

    private final ArtifactMetadataResourceHelper metadataResourceHelper;

    private final PropertiesService propertiesService;

    public ArtifactMetadataResource(PropertiesService propertiesService,
            ArtifactMetadataResourceHelper metadataResourceHelper) {
        this.metadataResourceHelper = metadataResourceHelper;
        this.propertiesService = propertiesService;
    }

    @DELETE
    public Response deleteAllProperties(@QueryParam("recursive") String recursive) {
        RepoPath repoPath = RepoPathFactory.create(path);
        metadataResourceHelper.validateUserHasAnnotateAuthorization(repoPath);
        Properties properties = propertiesService.getProperties(repoPath);
        if (!properties.keys().isEmpty()) {
            StringList packagePropertiesKeys = metadataResourceHelper.convertPropertiesToStringList(properties);
            return metadataResourceHelper.propertiesAddon()
                    .deletePathProperties(path, recursive, packagePropertiesKeys);
        }
        return Response.noContent().build();
    }

    @PATCH
    @Consumes({APPLICATION_JSON, RestUtils.APPLICATION_JSON_MERGE})
    public Response patchMetadata(String patch, @QueryParam("recursiveProperties") String recursiveProperties) {
        RepoPath repoPath = RepoPathFactory.create(path);
        metadataResourceHelper.validateUserHasAnnotateAuthorization(repoPath);
        JsonNode patchNode = JsonUtils.getInstance().readTree(patch);
        JsonNode props = patchNode.get("props");
        JsonNode stats = patchNode.get("stats");
        if (stats == null && props == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("props or stats fields required").build();
        }
        if (stats != null && !metadataResourceHelper.handleStatistics(stats, repoPath)) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Failed to set statistics on " + repoPath)
                    .build();
        }
        if (props != null){
            try {
                metadataResourceHelper.handlePatchProperties(props, repoPath, recursiveProperties);
                return Response.noContent().build();
            } catch (Exception e) {
                log.warn("Failed to set properties on {}", repoPath, e);
                return Response.status(Response.Status.BAD_REQUEST).entity("Failed to set properties on " + repoPath
                                + ": " + e.getMessage()).build();
            }
        }
        return Response.noContent().build();
    }
}