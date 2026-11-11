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

package org.artifactory.ui.rest.resource.artifacts.browse.treebrowser.tabs.generalinfo;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.RestGeneralTab;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.info.BaseInfo;
import org.artifactory.ui.rest.service.artifacts.browse.BrowseServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Chen Keinan
 */
@Path("artifactgeneral")
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GeneralArtifactResource extends BaseResource {

    @Autowired
    private BrowseServiceFactory browseFactory;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response populateTreeBrowser(RestGeneralTab generalTab) {
        return runService(browseFactory.getGetGeneralArtifactsService(), generalTab);
    }

    @POST
    @Path("artifactsCount")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getArtifactCount(BaseInfo baseInfo) {
        return runService(browseFactory.getArtifactsCountAndSizeService(), baseInfo);
    }

    @GET
    @Path("associatedVirtualRepos/{repoKey}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAssociatedVirtualRepos() {
        return runService(browseFactory.getAssociatedVirtualReposService());
    }

    @GET
    @Path("includedRepos/{repoKey}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIncludedRepos() {
        return runService(browseFactory.getIncludedReposService());
    }
}
