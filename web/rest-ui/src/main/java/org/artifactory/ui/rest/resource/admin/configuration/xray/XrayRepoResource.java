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

package org.artifactory.ui.rest.resource.admin.configuration.xray;

import lombok.NonNull;
import org.artifactory.addon.xray.XrayBasicSettingsModel;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.model.xray.XrayAllowBlockedDownloadModel;
import org.artifactory.rest.common.model.xray.XrayAllowWhenUnavailableModel;
import org.artifactory.rest.common.model.xray.XrayEnabledModel;
import org.artifactory.rest.common.model.xray.XrayRepoModel;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.service.admin.configuration.ConfigServiceFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

import static org.artifactory.projects.ProjectsService.PROJECT_KEY;

/**
 * @author Chen Keinan
 */
@Path("xrayRepo")
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class XrayRepoResource extends BaseResource {

    private final AuthorizationService authorizationService;
    private final ConfigServiceFactory configServiceFactory;

    @Inject
    public XrayRepoResource(@NonNull AuthorizationService authorizationService,
            @NonNull ConfigServiceFactory configServiceFactory) {
        this.authorizationService = authorizationService;
        this.configServiceFactory = configServiceFactory;
    }

    @GET
    @Path("getIndex")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    public Response getXrayIndexedRepo(@QueryParam(PROJECT_KEY) @Nullable String projectKey) {
        return runService(configServiceFactory.getXrayIndexedRepo());
    }

    @POST
    @Path("updateXrayBasicSettings")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setXrayBasicSettings(XrayBasicSettingsModel xrayBasicSettings) {
        return runService(configServiceFactory.updateXrayBasicSettings(), xrayBasicSettings);
    }

    @Deprecated
    @POST
    @Path("setXrayEnabled")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setXrayEnabled(XrayEnabledModel xrayEnabled) {
        return runService(configServiceFactory.setXrayEnabled(), xrayEnabled);
    }

    @Deprecated
    @POST
    @Path("setAllowWhenUnavailable")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setAllowDownloadWhenXrayUnavailable(XrayAllowWhenUnavailableModel xrayAllowWhenUnavailableModel) {
        return runService(configServiceFactory.setAllowDownloadWhenXrayUnavailable(), xrayAllowWhenUnavailableModel);
    }

    @Deprecated
    @POST
    @Path("setAllowBlockedArtifactsDownload")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setAllowBlockedArtifactsDownload(XrayAllowBlockedDownloadModel xrayAllowBlockedDownloadModel) {
        return runService(configServiceFactory.setAllowBlockedArtifactsDownload(), xrayAllowBlockedDownloadModel);
    }

    @GET
    @Path("getIntegrationConfig")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    public Response getXrayIntegrationConfig(@QueryParam(PROJECT_KEY) @Nullable String projectKey) {
        return runService(configServiceFactory.GetXrayIntegrationConfig());
    }

    @GET
    @Path("getNoneIndex")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    public Response getNoneXrayIndexedRepo(@QueryParam(PROJECT_KEY) @Nullable String projectKey) {
        return runService(configServiceFactory.getNoneXrayIndexedRepo());
    }

    @POST
    @Path("addIndex")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addXrayIndexedRepo(List<XrayRepoModel> repos) {
        return runService(configServiceFactory.addXrayIndexedRepo(), repos);
    }

    @POST
    @Path("removeIndex")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeXrayIndexedRepo(List<XrayRepoModel> repos) {
        return runService(configServiceFactory.removeXrayIndexedRepo(), repos);
    }

    @PUT
    @Path("indexRepos")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    public Response selectXrayIndexedRepos(@QueryParam(PROJECT_KEY) @Nullable String projectKey,
            List<XrayRepoModel> repos) {
        return runService(configServiceFactory.updateXrayIndexRepos(), repos);
    }
}
