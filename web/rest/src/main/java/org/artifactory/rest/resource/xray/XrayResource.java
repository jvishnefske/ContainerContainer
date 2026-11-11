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

package org.artifactory.rest.resource.xray;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.security.AuthorizationService;
import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.model.xray.RepositoriesHolder;
import org.artifactory.rest.common.model.xray.XrayArtifactInfo;
import org.artifactory.rest.common.model.xray.XrayConfigModel;
import org.artifactory.rest.common.model.xray.XrayRepoModel;
import org.artifactory.rest.common.model.xray.XrayScanBuildModel;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.services.ConfigServiceFactory;
import org.artifactory.rest.services.RepoServiceFactory;
import org.artifactory.util.StreamUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;
import static org.artifactory.rest.common.model.xray.ArtifactXrayModelHelper.getXrayInfo;
import static org.artifactory.rest.resource.xray.XrayResourceHelper.toModel;

/**
 * @author Shay Yaakov
 * @author Chen Keinan
 */
@Slf4j
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Component
@Path("xray")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class XrayResource extends BaseResource {
    //Suddenly required by the scan build functionality for no apparent reason, adding this to appease our corporate
    //overlords.
    private static final String NGINX_ACCEL_HEADER = "X-Accel-Buffering";
    private static final String SCAN_SUMMARY_NOT_AVAILABLE = "Scan summary report is not available";

    @Autowired
    private RepoServiceFactory repoServiceFactory;
    @Autowired
    private ConfigServiceFactory configServiceFactory;
    @Autowired
    private AddonsManager addonsManager;
    @Autowired
    private BuildFacade buildFacade;

    @POST
    @Path("index_repos")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response indexRepos(RepositoriesHolder repositoriesHolder) {
        return runService(repoServiceFactory.indexXray(), repositoriesHolder);
    }

    /**
     * @deprecated backward compatibility, use index_repos instead
     */
    @Deprecated(since = "Artifactory v7.8")
    @POST
    @Path("index")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response index() {
        return runService(repoServiceFactory.indexXray());
    }

    @POST
    @Path("scanBuild")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.WILDCARD)
    @RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
    public Response scanBuild(XrayScanBuildModel xrayScanBuildModel) {
        String buildRepo = buildFacade.getPreferredBuildRepo(xrayScanBuildModel.getBuildRepo(),
                xrayScanBuildModel.getProject());
        xrayScanBuildModel.setBuildRepo(buildRepo);

        if (!buildFacade
                .canReadBuild(xrayScanBuildModel.getBuildName(), xrayScanBuildModel.getBuildNumber(), buildRepo)) {
            throw new AuthorizationRestException("User is not authorized to access build info");
        }
        CloseableHttpResponse scanBuildResponse = null;
        try {
            XrayAddon xrayAddon = addonsManager.addonByType(XrayAddon.class);
            if (BUILDINFO_REPO_KEY.equals(buildRepo) || xrayAddon.isXrayVersionSupportProjects()) {
                scanBuildResponse = xrayAddon.scanBuild(toModel(xrayScanBuildModel));
            } else {
                return Response.status(HttpStatus.SC_BAD_REQUEST)
                        .entity("Scanning non-default build repositories is unsupported by Xray version lower than: " +
                                XrayAddon.XRAY_MIN_VER_WITH_PROJECTS_SUPPORT).build();
            }
        } catch (Exception e) {
            log.warn("Error Executing scan build request: ", e);
        }
        if (scanBuildResponse == null || scanBuildResponse.getEntity() == null) {
            log.error("Scan summary report for build name {} number {} from {} project is not available, " +
                            "check connectivity to Xray",
                    xrayScanBuildModel.getBuildName(), xrayScanBuildModel.getBuildNumber(),
                    xrayScanBuildModel.getProject());
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .entity(SCAN_SUMMARY_NOT_AVAILABLE)
                    .build();
        }
        final int xrayStatus = scanBuildResponse.getStatusLine().getStatusCode();
        if (xrayStatus != HttpStatus.SC_OK) {
            StreamUtils.close(scanBuildResponse);
            return buildXrayErrorResponse(xrayStatus, xrayScanBuildModel);
        }
        try {
            return XrayResourceHelper.streamResponse(scanBuildResponse)
                    .header(NGINX_ACCEL_HEADER, "no")
                    .build();
        } catch (Exception e) {
            StreamUtils.close(scanBuildResponse);
            log.error("Caught error streaming scan build response to client: ", e);
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .entity("Error executing scan build request: " + e.getMessage())
                    .build();
        }
    }

    private Response buildXrayErrorResponse(int xrayStatus, XrayScanBuildModel xrayScanBuildModel) {
        final String message = "Scan summary report for build name {} number {} from {} project is not " +
                "available: Xray responded with HTTP {}";
        if (xrayStatus >= HttpStatus.SC_INTERNAL_SERVER_ERROR) {
            log.error(message, xrayScanBuildModel.getBuildName(), xrayScanBuildModel.getBuildNumber(),
                    xrayScanBuildModel.getProject(), xrayStatus);
        } else {
            log.warn(message, xrayScanBuildModel.getBuildName(), xrayScanBuildModel.getBuildNumber(),
                    xrayScanBuildModel.getProject(), xrayStatus);
        }
        return Response.status(xrayStatus)
                .entity(SCAN_SUMMARY_NOT_AVAILABLE)
                .build();
    }

    @DELETE
    @Path("clearAllIndexTasks")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clearAllIndexTasks() {
        return runService(repoServiceFactory.clearAllIndexTasks());
    }

    @GET
    @Path("license")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response license() {
        return runService(repoServiceFactory.getXrayLicense());
    }

    @GET
    @Path("{repoKey}/indexStats")
    public Response getIndexStats(@PathParam("repoKey") String repoKey) {
        return runService(repoServiceFactory.getXrayIndexStats());
    }

    /**
     * @deprecated backward compatibility, use getXrayIndexedRepoRepos instead
     */
    @Deprecated
    @GET
    @Path("repos")
    @RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfigureReposIndexing() {
        return runService(configServiceFactory.getXrayConfiguredRepos());
    }

    @GET
    @Path("indexRepos")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getXrayIndexedRepo() throws Exception {
        return runService(configServiceFactory.getXrayIndexedRepo());
    }

    @GET
    @Path("buildrepo/names")
    @RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    public Response getBuildRepoNames(@QueryParam(ProjectsService.PROJECT_KEY) String projectKey) {
        return runService(configServiceFactory.getXrayBuildRepoNames());
    }

    @PUT
    @Path("indexRepos")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response selectXrayIndexedRepos(List<XrayRepoModel> repos) {
        return runService(configServiceFactory.updateXrayIndexRepos(), repos);
    }

    @GET
    @Path("nonIndexRepos")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getNoneXrayIndexedRepo() throws Exception {
        return runService(configServiceFactory.getNoneXrayIndexedRepo());
    }

    @POST
    @Path("setAlertIgnored")
    @Produces(MediaType.TEXT_PLAIN)
    public Response resetRepoBlocks(@QueryParam("path") String path) {
        RepoPath repoPath = RepoPathFactory.create(path);
        try {
            if (addonsManager.addonByType(XrayAddon.class).setAlertIgnored(repoPath)) {
                return Response.ok().entity("Download will be allowed for path " + path).build();
            }
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Alert couldn't be ignored. Check logs for more information.").build();
        } catch (IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @POST
    @Path("cleanProperties")
    public Response cleanProperties() {
        try {
            addonsManager.addonByType(XrayAddon.class).cleanXrayPropertiesFromDB();
            return Response.ok().entity("Xray properties cleanup job has been successfully scheduled").build();
        } catch (IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @POST
    @Path("cleanXrayClientCaches")
    public Response cleanCaches() {
        try {
            addonsManager.addonByType(XrayAddon.class).cleanXrayClientCaches();
            return Response.ok().entity("Invalidated all Xray client caches").build();
        } catch (IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @POST
    @Path("allowBlockedArtifactsDownload")
    public Response allowBlockedArtifactsDownload(@QueryParam("allow") Boolean allow) {
        if (allow == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing parameter allow[true|false]").build();
        }
        if (addonsManager.addonByType(XrayAddon.class).updateAllowBlockedArtifactsDownload(allow)) {
            return Response.ok().entity("Xray configuration for allow blocked artifacts download set to: " + allow)
                    .build();
        }
        return Response.serverError().entity("Encountered error on config changes. Check logs for more information.")
                .build();
    }

    @POST
    @Path("allowDownloadWhenUnavailable")
    public Response allowDownloadWhenUnavailable(@QueryParam("allow") Boolean allow) {
        if (allow == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing parameter allow[true|false]").build();
        }
        if (addonsManager.addonByType(XrayAddon.class).updateAllowDownloadWhenUnavailable(allow)) {
            return Response.ok()
                    .entity("Xray configuration for allow download when Xray is unavailable set to: " + allow).build();
        }
        return Response.serverError().entity("Encountered error on config changes. Check logs for more information.")
                .build();
    }

    @POST
    @Path("setBlockUnscannedArtifactsDownloadTimeout")
    public Response setBlockUnscannedArtifactsDownloadTimeout(@QueryParam("seconds") Integer seconds) {
        if (seconds == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing query parameter 'seconds'").build();
        }
        if (addonsManager.addonByType(XrayAddon.class).setBlockUnscannedArtifactsDownloadTimeout(seconds)) {
            return Response.ok()
                    .entity("Xray configuration for block unscanned artifacts download timeout set to: " + seconds)
                    .build();
        }
        return Response.serverError().entity("Encountered error on config changes. Check logs for more information.")
                .build();
    }

    @GET
    @Path("artifactInfo")
    @Produces(MediaType.APPLICATION_JSON)
    public Response artifactInfo(@QueryParam("path") String path) {
        RepoPath repoPath = RepoPathFactory.create(path);
        try {
            XrayArtifactInfo xrayInfo =
                    getXrayInfo(addonsManager.addonByType(XrayAddon.class).getArtifactXrayInfo(repoPath));
            return Response.ok().entity(xrayInfo).build();
        } catch (IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /**
     * Used by Xray to notify Artifactory upon a change to repository policies,
     * which will trigger a full update from our side + propagate the notification to other nodes.
     *
     * @param internal In case True: will not propagate to other nodes (means the request came from other Artifactory
     *                 server that got the original request)
     */
    @POST
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
    @Path("notifyPolicyChange")
    public Response createPolicyChangeNotification(@QueryParam("internal") boolean internal) {
        log.debug("Received a policy change notification, internal: {}", internal);
        XrayAddon xrayAddon = addonsManager.addonByType(XrayAddon.class);

        if (!xrayAddon.isXrayEnabled()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Xray is disabled. notifyPolicyChange is not supported when Xray integration is disabled.")
                    .build();
        }

        if (xrayAddon.createPolicyChangeNotification(internal)) {
            return Response.status(Response.Status.CREATED).build();
        }
        return Response.serverError().entity("Encountered error on notification. Check logs for more information.")
                .build();
    }

    //Configuration endpoints

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createXrayConfig(XrayConfigModel xrayConfigModel) {
        return runService(configServiceFactory.createXrayConfig(), xrayConfigModel);
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateXrayConfig(XrayConfigModel xrayConfigModel) {
        return runService(configServiceFactory.updateXrayConfig(), xrayConfigModel);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteXrayConfig() {
        return runService(configServiceFactory.deleteXrayConfig());
    }
}
