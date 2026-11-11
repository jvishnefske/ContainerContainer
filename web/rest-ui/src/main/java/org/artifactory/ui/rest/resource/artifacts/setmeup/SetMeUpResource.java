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

package org.artifactory.ui.rest.resource.artifacts.setmeup;

import lombok.Setter;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.util.JsonUtil;
import org.artifactory.ui.rest.model.setmeup.GetReposByKeysRequest;
import org.artifactory.ui.rest.model.setmeup.GradleSettingModel;
import org.artifactory.ui.rest.model.setmeup.IvySettingModel;
import org.artifactory.ui.rest.model.setmeup.MavenSettingModel;
import org.artifactory.ui.rest.service.setmeup.SetMeUpServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static org.artifactory.ui.rest.service.setmeup.BaseSetMeUpHandler.REPO_KEY;
import static org.artifactory.ui.rest.service.setmeup.GetAllReposByPackageHandler.PACKAGE_TYPE;

/**
 * @author Chen Keinan
 */
@Path("setMeUp")
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SetMeUpResource extends BaseResource {

    @Autowired
    @Qualifier("streamingRestResponse")
    public void setArtifactoryResponse(RestResponse artifactoryResponse) {
        this.artifactoryResponse = artifactoryResponse;
    }

    @Setter(onMethod_ = {@Autowired})
    SetMeUpServiceFactory setMeUpServiceFactory;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRepoKeyTypeForSetMeUp() {
        return runService(setMeUpServiceFactory.getLegacySetMeUp());
    }

    @GET
    @Path("/repositories/{repoKey: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSetMeUpRepoByKey(@PathParam(REPO_KEY) String repoKey) {
        return runService(setMeUpServiceFactory.getRepoHandler());
    }

    @POST
    @Path("/repositories")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSetMeUpReposByKeys(GetReposByKeysRequest request) {
        return runService(setMeUpServiceFactory.getReposByKeysHandler(), request);
    }

    @GET
    @Path("/packages/{packageType}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllSetMeUpReposByPackageType(@PathParam(PACKAGE_TYPE) String packageType) {
        return runService(setMeUpServiceFactory.getAllReposByPackageHandler());
    }

    @GET
    @Path("packageTypes")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllSetMeUpPackages() {
        return runService(setMeUpServiceFactory.getAllPackagesHandler());
    }

    @GET
    @Path("mavenSettings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMavenSettings()
            throws Exception {
        return runService(setMeUpServiceFactory.mavenSettingGenerator());
    }

    @GET
    @Path("gradleSettings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGradleSettings() {
        return runService(setMeUpServiceFactory.gradleSettingGenerator());
    }

    @GET
    @Path("ivySettings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIvySettings() {
        return runService(setMeUpServiceFactory.ivySettingGenerator());
    }

    @GET
    @Path("reverseProxyData")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getReverseProxyData() {
        return runService(setMeUpServiceFactory.getReverseProxySetMeUpData());
    }

    @POST
    @Path("mavenSnippet")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response generateMavenSnippet(MavenSettingModel mavenSettingModel) {
        return runService(setMeUpServiceFactory.getMavenSettingSnippet(), mavenSettingModel);
    }

    @POST
    @Path("gradleSnippet")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response generateGradleSnippet(GradleSettingModel gradleSettingModel) {
        return runService(setMeUpServiceFactory.getGradleSettingSnippet(), gradleSettingModel);
    }

    @POST
    @Path("ivySnippet")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response generateIvySnippet(IvySettingModel ivySettingModel) {
        return runService(setMeUpServiceFactory.getIvySettingSnippet(), ivySettingModel);
    }

    @GET
    @Path("downloadBuildGradle")
    @Produces(MediaType.TEXT_PLAIN)
    public Response downloadGradleProperties(@QueryParam("data") String data)
            throws Exception {
        GradleSettingModel gradleSettingModel = (GradleSettingModel) JsonUtil.mapDataToModel(data,
                GradleSettingModel.class);
        return runService(setMeUpServiceFactory.getGradleSettingSnippet(), gradleSettingModel);
    }

    @GET
    @Path("downloadBuildMaven")
    @Produces(MediaType.APPLICATION_XML)
    public Response downloadMavenSnippet(@QueryParam("data") String data)
            throws Exception {
        MavenSettingModel mavenSettingModel = (MavenSettingModel) JsonUtil.mapDataToModel(data,
                MavenSettingModel.class);
        return runService(setMeUpServiceFactory.getMavenSettingSnippet(), mavenSettingModel);
    }

    @GET
    @Path("downloadBuildIvy")
    @Produces(MediaType.APPLICATION_XML)
    public Response generateIvySnippet(@QueryParam("data") String data)
            throws Exception {
        IvySettingModel ivySettingModel = (IvySettingModel) JsonUtil.mapDataToModel(data,
                IvySettingModel.class);
        return runService(setMeUpServiceFactory.getIvySettingSnippet(), ivySettingModel);
    }

    @GET
    @Path("mavenDistributionManagement")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response mavenDistributionManagement() {
        return runService(setMeUpServiceFactory.getMavenDistributionMgnt());
    }

    @GET
    @Path("publicKeyFilename/{repoKey: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPublicKeyFilenameByRepoId(@PathParam("repoKey") String repoKey) {
        return runService(setMeUpServiceFactory.getPublicKeyFilenamePerRepoService(), repoKey);
    }

    @GET
    @Path("nix/trustedPublicKey/{repoKey: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getNixTrustedPublicKeyByRepoId(@PathParam("repoKey") String repoKey) {
        return runService(setMeUpServiceFactory.getNixTrustedPublicKeyPerRepoService(), repoKey);
    }

    @GET
    @Path("terraform/registry/{repoKey: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTerraformConfig(@PathParam("repoKey") String repoKey) {
        return runService(setMeUpServiceFactory.getTerraformConfigSnippetService(), repoKey);
    }

    @GET
    @Path("AIEditorExtensionGenerateToken")
    @Produces(MediaType.APPLICATION_JSON)
    public Response generateAIEditorExtensionToken() {
        return runService(setMeUpServiceFactory.getAIEditorExtensionGenerateTokenHandler());
    }
}