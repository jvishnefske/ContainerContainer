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

package org.artifactory.rest.resource.artifact.legacy;

import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.constant.ArtifactRestConstants;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static org.artifactory.api.rest.constant.ArtifactRestConstants.PATH_DOWNLOAD;

/**
 * REST API for downloading an artifact
 *
 * @author Yoav Landman
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(PATH_DOWNLOAD)
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@ActivityDetails(group = RestApiGroup.ARTIFACT_API)
public class DownloadResource {

    @Context
    HttpServletResponse httpResponse;

    public enum Content {
        //Name casing must match the param name (cannot do b y overriding toString())
        none, progress
    }

    /**
     * Download an artifact
     *
     * @param path    The path of the source artifact to be downloaded
     * @param content The content handling policy (none/progress)
     * @param mark    Every how many bytes to print a progress mark (when using progress tracking policy)
     * @deprecated Use {@link org.artifactory.rest.resource.replication.ReplicationResource} instead
     */
    @GET
    @Path("{path: .+}")
    @Produces({MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN})
    @Deprecated(forRemoval = false)
    public Response downloadArtifactOrFolder(
            @PathParam("path") String path,
            @QueryParam(ArtifactRestConstants.PARAM_CONTENT) Content content,
            @QueryParam(ArtifactRestConstants.PARAM_MARK) int mark) throws Exception {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        return addonsManager.addonByType(RestAddon.class).download(path, content, mark, httpResponse);
    }
}
