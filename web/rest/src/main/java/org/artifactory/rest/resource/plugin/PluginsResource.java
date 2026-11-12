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

package org.artifactory.rest.resource.plugin;

import com.google.common.collect.Maps;
import com.jfrog.build.acl.bridge.BuildFacade;
import jakarta.annotation.Nullable;
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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.addon.plugin.groovy.HostedResponseCtx;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.io.SimpleResourceStreamHandle;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.util.AolUtils;
import org.artifactory.util.KeyValueList;
import org.artifactory.util.StringInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.Reader;
import java.util.List;
import java.util.Map;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static jakarta.ws.rs.core.MediaType.WILDCARD;
import static org.artifactory.api.rest.constant.PluginRestConstants.MT_BUILD_STAGING_STRATEGY;
import static org.artifactory.api.rest.constant.PluginRestConstants.PARAM_ASYNC;
import static org.artifactory.api.rest.constant.PluginRestConstants.PARAM_PARAMS;
import static org.artifactory.api.rest.constant.PluginRestConstants.PATH_EXECUTE;
import static org.artifactory.api.rest.constant.PluginRestConstants.PATH_PROMOTE;
import static org.artifactory.api.rest.constant.PluginRestConstants.PATH_ROOT;
import static org.artifactory.api.rest.constant.PluginRestConstants.PATH_STAGING;
import static org.artifactory.projects.ProjectsService.PROJECT;

/**
 * A resource for plugin execution
 *
 * @author Tomer Cohen
 */
@Component
@Path(PATH_ROOT)
@RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
public class PluginsResource {

    private static final Logger log = LoggerFactory.getLogger(PluginsResource.class);

    private AddonsManager addonsManager;
    private BuildFacade buildFacade;
    private AolService aolService;

    @Autowired
    public PluginsResource(
            BuildFacade buildFacade,
            AddonsManager addonsManager,
            AolService aolService) {
        this.buildFacade = buildFacade;
        this.addonsManager = addonsManager;
        this.aolService = aolService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPluginInfo() {
        return getPluginInfo(null);
    }

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPluginStatus() {
        ResponseCtx responseCtx = addonsManager.addonByType(RestAddon.class).getPluginStatus();
        return responseFromResponseCtx(responseCtx);
    }

    @GET
    @Path("{pluginType: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPluginInfo(@Nullable @PathParam("pluginType") String pluginType) {
        return addonsManager.addonByType(RestAddon.class).getUserPluginInfo(pluginType);
    }

    @POST
    @Consumes(WILDCARD)
    @Path(PATH_EXECUTE + "/{executionName: .+}")
    @Produces(TEXT_PLAIN)
    public Response execute(@Context Request request,
            InputStream body,
            @PathParam("executionName") String executionName,
            @QueryParam(PARAM_PARAMS) KeyValueList paramsList,
            @QueryParam(PARAM_ASYNC) int async) throws Exception {
        Map<String, List<String>> params =
                paramsList != null ? paramsList.toStringMap() : Maps.newHashMap();
        try (ResourceStreamHandle handle = new SimpleResourceStreamHandle(body)) {
            ResponseCtx responseCtx =
                    addonsManager.addonByType(RestAddon.class).runPluginExecution(executionName, request.getMethod(),
                            params,
                            handle, async == 1);
            if (async == 1) {
                //Just return accepted (202)
                return Response.status(HttpStatus.SC_ACCEPTED).build();
            } else {
                return responseFromResponseCtx(responseCtx);
            }
        }
    }

    @PUT
    @Consumes(WILDCARD)
    @Path(PATH_EXECUTE + "/{executionName: .+}")
    @Produces(TEXT_PLAIN)
    public Response executePut(@Context Request request,
            InputStream body,
            @PathParam("executionName") String executionName,
            @QueryParam(PARAM_PARAMS) KeyValueList paramsList,
            @QueryParam(PARAM_ASYNC) int async) throws Exception {
        return execute(request, body, executionName, paramsList, async);
    }

    @GET
    @Path(PATH_EXECUTE + "/{executionName: .+}")
    @Produces(TEXT_PLAIN)
    public Response execute(@Context Request request, @PathParam("executionName") String executionName,
            @QueryParam(PARAM_PARAMS) KeyValueList paramsList,
            @QueryParam(PARAM_ASYNC) int async) throws Exception {
        Map<String, List<String>> params =
                paramsList != null ? paramsList.toStringMap() : Maps.newHashMap();
        ResponseCtx responseCtx = addonsManager.addonByType(RestAddon.class).runPluginExecution(executionName,
                request.getMethod(), params, null, async == 1);
        if (async == 1) {
            //Just return accepted (202)
            return Response.status(HttpStatus.SC_ACCEPTED).build();
        } else {
            return responseFromResponseCtx(responseCtx);
        }
    }

    @DELETE
    @Path(PATH_EXECUTE + "/{executionName: .+}")
    @Produces(TEXT_PLAIN)
    public Response executeDelete(@Context Request request, @PathParam("executionName") String executionName,
            @QueryParam(PARAM_PARAMS) KeyValueList paramsList,
            @QueryParam(PARAM_ASYNC) int async) throws Exception {
        return execute(request, executionName, paramsList, async);
    }

    @GET
    @Path(PATH_STAGING + "/{strategyName: .+}")
    @Produces({MT_BUILD_STAGING_STRATEGY, MediaType.APPLICATION_JSON})
    public Response getBuildStagingStrategy(
            @PathParam("strategyName") String strategyName,
            @QueryParam("buildRepo") String buildRepo,
            @QueryParam("buildName") String buildName,
            @QueryParam(PROJECT) String projectKey,
            @QueryParam(PARAM_PARAMS) KeyValueList paramsList) {
        Map<String, List<String>> params =
                paramsList != null ? paramsList.toStringMap() : Maps.newHashMap();
        buildRepo = buildFacade.getPreferredBuildRepo(buildRepo, projectKey);
        return addonsManager.addonByType(RestAddon.class)
                .getStagingStrategy(strategyName, buildName, buildRepo, params);
    }

    @POST
    @Path(PATH_PROMOTE + "/{promotionName: .+}/{buildName: .+}/{buildNumber: .+}")
    @Produces(TEXT_PLAIN)
    public Response promote(
            @PathParam("promotionName") String promotionName,
            @PathParam("buildName") String buildName,
            @PathParam("buildNumber") String buildNumber,
            @QueryParam("buildRepo") String buildRepo,
            @QueryParam(PROJECT) String projectKey,
            @QueryParam(PARAM_PARAMS) KeyValueList paramsList) {
        Map<String, List<String>> params =
                paramsList != null ? paramsList.toStringMap() : Maps.newHashMap();
        buildRepo = buildFacade.getPreferredBuildRepo(buildRepo, projectKey);
        ResponseCtx responseCtx = addonsManager.addonByType(RestAddon.class)
                .promoteBuildPlugin(promotionName, buildName, buildNumber, buildRepo, params);
        return responseFromResponseCtx(responseCtx);
    }

    @PUT
    @Path("{scriptName: .+}")
    @Consumes({"text/x-groovy", TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public Response deploy(Reader pluginScript, @PathParam("scriptName") String scriptName) {
        ResponseCtx responseCtx = addonsManager.addonByType(RestAddon.class).deployPlugin(pluginScript, scriptName);
        if (responseCtx.getStatus() >= 400) {
            ErrorResponse errorResponse = new ErrorResponse(responseCtx.getStatus(), responseCtx.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON).entity(errorResponse)
                    .build();
        } else {
            return responseFromResponseCtx(responseCtx);
        }
    }

    /**
     * Reloads user plugins.
     * Nothing is reloaded if there's no plugin present or no plugin modified since the last reload.
     *
     * @param internal If true - will not propagate the reload to other HA members
     */
    @POST
    @Path("reload")
    @Produces(MediaType.TEXT_PLAIN)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
    public Response reload(@QueryParam("internal") boolean internal) {
        try {
            if (aolService.isAol() &&
                    !(aolService.isDashboardUser() || addonsManager.addonByType(HaAddon.class).isHaAuthentication())) {
                return Response.status(HttpStatus.SC_FORBIDDEN).entity(AolUtils.NOT_SUPPORTED_FOR_CLOUD).build();
            }

            ResponseCtx responseCtx = addonsManager.addonByType(RestAddon.class).reloadPlugins(internal);
            return Response.status(responseCtx.getStatus())
                    // ugly hack to force text response (to overcome our default in org.artifactory.rest.common
                    //.RestErrorResponseFilter
                    .entity(new StringInputStream(responseCtx.getMessage()))
                    .type(MediaType.TEXT_PLAIN).build();
        } catch (Exception e) {
            log.error("Failed to reload plugins", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    /**
     * @param pluginName The plugin name
     * @return The content of the specified user plugin by its name (with or without the .groovy extension).
     */
    @GET
    @Path("download" + "/{pluginName: .+}")
    @Produces("text/x-groovy-source")
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    public Response downloadPlugin(@Nullable @PathParam("pluginName") String pluginName) {
        ResponseCtx responseCtx = addonsManager.addonByType(RestAddon.class).downloadPlugin(pluginName);
        return responseFromResponseCtx(responseCtx);
    }

    /**
     * Deletes a plugin from the plugins directory.
     *
     * @param pluginName The plugin name to delete (with or without .groovy extension)
     * @return Response with deletion results
     */
    @DELETE
    @Path("{pluginName: .+}")
    @Produces(MediaType.TEXT_PLAIN)
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    public Response deletePlugin(@PathParam("pluginName") String pluginName) {
        ResponseCtx responseCtx = addonsManager.addonByType(RestAddon.class).deletePlugin(pluginName);
        return responseFromResponseCtx(responseCtx);
    }

    private Response responseFromResponseCtx(ResponseCtx responseCtx) {
        Response.ResponseBuilder builder;
        int status = responseCtx.getStatus();
        if (status != ResponseCtx.UNSET_STATUS) {
            builder = Response.status(status);
        } else {
            builder = Response.ok();
        }

        if (responseCtx.getMessage() != null) {
            builder.entity(responseCtx.getMessage());
        } else if (responseCtx.getInputStream() != null) {
            builder.entity(responseCtx.getInputStream());
        }
        if (responseCtx instanceof HostedResponseCtx hosted) {
            withHostedHeaders(builder, hosted.responseHeaders());
        }
        return builder.build();
    }

    /**
     * Sends the headers of a hosted (WASM) execution's response. The host already validated them; a
     * {@code content-type} that is not a valid media type is dropped and the default {@code text/plain} stays.
     * Groovy executions return a plain {@link ResponseCtx}, whose headers are not sent, as before.
     */
    private static void withHostedHeaders(Response.ResponseBuilder builder, Map<String, List<String>> headers) {
        headers.forEach((name, values) -> {
            if ("content-type".equalsIgnoreCase(name)) {
                if (!values.isEmpty()) {
                    try {
                        builder.type(MediaType.valueOf(values.get(0)));
                    } catch (IllegalArgumentException e) {
                        log.debug("Ignoring invalid content type of a plugin execution: {}", e.getMessage());
                    }
                }
            } else {
                values.forEach(value -> builder.header(name, value));
            }
        });
    }
}
