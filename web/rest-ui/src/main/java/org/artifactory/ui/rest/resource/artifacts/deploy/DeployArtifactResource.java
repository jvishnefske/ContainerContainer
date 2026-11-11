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

package org.artifactory.ui.rest.resource.artifacts.deploy;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.model.artifacts.deploy.UploadArtifactInfo;
import org.artifactory.ui.rest.service.artifacts.deploy.DeployServiceFactory;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Chen Keinan
 */
@Path("artifact")
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DeployArtifactResource extends BaseResource {

    @Autowired
    private DeployServiceFactory deployFactory;

    @POST
    @Path("upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadArtifact(FormDataMultiPart formParams) {
        UploadArtifactInfo uploadArtifactInfo = new UploadArtifactInfo(formParams);
        return runService(deployFactory.artifactUpload(), uploadArtifactInfo);
    }

    @POST
    @Path("cancelupload")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancelUploadArtifact(UploadArtifactInfo uploadArtifactInfo) {
        return runService(deployFactory.cancelArtifactUpload(), uploadArtifactInfo);
    }

    @POST
    @Path("deploy")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deployArtifact(UploadArtifactInfo uploadArtifactInfo) {
        return runService(deployFactory.deployArtifact(), uploadArtifactInfo);
    }

    @POST
    @Path("deploy/bundle")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadBundleArtifact(UploadArtifactInfo uploadArtifactInfo) {
        return runService(deployFactory.artifactDeployBundle(), uploadArtifactInfo);
    }

    @POST
    @Path("deploy/multi")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deployMultiArtifact(FormDataMultiPart formParams) {
        UploadArtifactInfo uploadArtifactInfo = new UploadArtifactInfo(formParams);
        return runService(deployFactory.artifactMultiDeploy(), uploadArtifactInfo);
    }
}
