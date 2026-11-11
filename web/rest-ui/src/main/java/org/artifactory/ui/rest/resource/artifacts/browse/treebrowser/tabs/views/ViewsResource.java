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

package org.artifactory.ui.rest.resource.artifacts.browse.treebrowser.tabs.views;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.ViewArtifact;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.BaseArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.bower.BowerArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.cargo.CargoArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.composer.ComposerArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.debian.DebianArtifactMetadataInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.docker.DockerArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.docker.ancestry.DockerAncestryArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.gems.GemsArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.pub.PubArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.puppet.PuppetArtifactInfoModel;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.rpm.RpmArtifactInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.terraform.TerraformStateInfo;
import org.artifactory.ui.rest.service.artifacts.browse.BrowseServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * @author Chen Keinan
 */
@Path("")
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ViewsResource extends BaseResource {

    @Autowired
    BrowseServiceFactory browseFactory;

    @Autowired
    @Qualifier("streamingRestResponse")
    @Override
    public void setArtifactoryResponse(RestResponse artifactoryResponse) {
        this.artifactoryResponse = artifactoryResponse;
    }

    @POST
    @Path("views/pom")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewPom(ViewArtifact viewArtifact) {
        return runService(browseFactory.viewArtifactService(), viewArtifact);
    }

    @POST
    @Path("views/gems")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewGems(GemsArtifactInfo gemsArtifactInfo) {
        return runService(browseFactory.gemsViewService(), gemsArtifactInfo);
    }

    @POST
    @Path("views/rpm")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewRpm(RpmArtifactInfo rpmArtifactInfo) {
        return runService(browseFactory.rpmViewService(), rpmArtifactInfo);
    }

    @POST
    @Path("views/debian")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewDebian(DebianArtifactMetadataInfo debianArtifactInfo) {
        return runService(browseFactory.DebianViewService(), debianArtifactInfo);
    }

    @POST
    @Path("views/opkg")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewOpkg(DebianArtifactMetadataInfo debianArtifactInfo) {
        return runService(browseFactory.DebianViewService(), debianArtifactInfo);
    }

    @POST
    @Path("views/puppet")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewPuppet(PuppetArtifactInfoModel puppetArtifactInfoModel) {
        return runService(browseFactory.puppetViewService(), puppetArtifactInfoModel);
    }

    @POST
    @Path("views/bower")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewBower(BowerArtifactInfo bowerArtifactInfo) {
        return runService(browseFactory.bowerViewService(), bowerArtifactInfo);
    }

    @POST
    @Path("views/conan")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewConan(BaseArtifactInfo artifactInfo) {
        return runService(browseFactory.conanViewService(), artifactInfo);
    }

    @POST
    @Path("views/conan_package")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewConanPackage(BaseArtifactInfo artifactInfo) {
        return runService(browseFactory.conanPackageViewService(), artifactInfo);
    }

    @POST
    @Path("views/docker")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewDocker(DockerArtifactInfo dockerArtifactInfo) {
        return runService(browseFactory.dockerViewService(), dockerArtifactInfo);
    }

    @POST
    @Path("views/dockerv2")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewDockerV2(DockerArtifactInfo dockerArtifactInfo) {
        return runService(browseFactory.dockerV2ViewTreeService(), dockerArtifactInfo);
    }

    @POST
    @Path("views/dockerancestry")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewDockerAncestry(DockerAncestryArtifactInfo dockerAncestryArtifactInfo) {
        return runService(browseFactory.dockerAncestryViewService(), dockerAncestryArtifactInfo);
    }

    @GET
    @Path("views/dockerproxy{id:(/[^/]+?)?}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewDockerProxyConfig() {
        return runService(browseFactory.dockerProxyViewService());
    }

    @POST
    @Path("views/composer")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewComposer(ComposerArtifactInfo composerArtifactInfo) {
        return runService(browseFactory.composerViewService(), composerArtifactInfo);
    }

    @POST
    @Path("views/chef")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewChef(BaseArtifactInfo artifactInfo) {
        return runService(browseFactory.chefViewService(), artifactInfo);
    }

    @POST
    @Path("views/terraformBackend")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewTerraformBackend(TerraformStateInfo artifactInfo) {
        return runService(browseFactory.terraformBackendViewService(), artifactInfo);
    }

    @POST
    @Path("views/pub")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewPub(PubArtifactInfo artifactInfo) {
        return runService(browseFactory.pubViewService(), artifactInfo);
    }

    @POST
    @Path("views/cargo")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewCargo(CargoArtifactInfo artifactInfo) {
        return runService(browseFactory.cargoViewService(), artifactInfo);
    }

    public <Y> Response runService(RestService<Y> service, Y model, @Context HttpServletRequest servletRequest,
            @Context HttpServletResponse servletResponse, @Context Request request, @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders) {
        return super.runService(service, model, servletRequest, servletResponse, request, uriInfo, httpHeaders);
    }
}