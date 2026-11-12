/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.rest.resource.pkgapi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.CloseableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * {@code /api/pkg/{repoKey}/{path}}: the plugin HTTP API under a neutral prefix, for package types that are not
 * registries. Same behaviour as {@link DockerPluginApiResource} without the registry ping and the {@code /v2/}
 * rewrite.
 *
 * <p>Every method delegates to {@link PluginApiEndpoint}, which checks the repository and permissions before any
 * plugin code runs. A repository whose type has no API handler answers 404.
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path("pkg")
public class PkgPluginApiResource {

    private static final String API_PATH = "{repoKey}/{path: .*}";

    @Context
    private CloseableService closeableService;

    private final PluginApiEndpoint endpoint;

    @Autowired
    public PkgPluginApiResource(PluginApiEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    PkgPluginApiResource(PluginApiEndpoint endpoint, CloseableService closeableService) {
        this.endpoint = endpoint;
        this.closeableService = closeableService;
    }

    @GET
    @Path(API_PATH)
    public Response get(@PathParam("repoKey") String repoKey, @PathParam("path") String path,
            @Context HttpServletRequest request) throws IOException {
        return serve(repoKey, path, request);
    }

    @HEAD
    @Path(API_PATH)
    public Response head(@PathParam("repoKey") String repoKey, @PathParam("path") String path,
            @Context HttpServletRequest request) throws IOException {
        return serve(repoKey, path, request);
    }

    @PUT
    @Path(API_PATH)
    public Response put(@PathParam("repoKey") String repoKey, @PathParam("path") String path,
            @Context HttpServletRequest request) throws IOException {
        return serve(repoKey, path, request);
    }

    @POST
    @Path(API_PATH)
    public Response post(@PathParam("repoKey") String repoKey, @PathParam("path") String path,
            @Context HttpServletRequest request) throws IOException {
        return serve(repoKey, path, request);
    }

    @PATCH
    @Path(API_PATH)
    public Response patch(@PathParam("repoKey") String repoKey, @PathParam("path") String path,
            @Context HttpServletRequest request) throws IOException {
        return serve(repoKey, path, request);
    }

    @DELETE
    @Path(API_PATH)
    public Response delete(@PathParam("repoKey") String repoKey, @PathParam("path") String path,
            @Context HttpServletRequest request) throws IOException {
        return serve(repoKey, path, request);
    }

    private Response serve(String repoKey, String path, HttpServletRequest request) throws IOException {
        return endpoint.serve(true, repoKey, path, request, closeableService::add);
    }
}
