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

package org.artifactory.webapp.servlet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.web.FeatureFlagService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.rest.resource.mfe.MfeProjectConfig;
import org.artifactory.rest.resource.mfe.MfeProjectRegistry;
import org.artifactory.rest.resource.mfe.MigratedRoutesPayload;
import org.artifactory.rest.resource.mfe.server.MFEFileServer;
import org.jfrog.client.util.PathUtils;

import java.io.File;
import java.io.IOException;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nirt
 */
@Slf4j
public class ArtifactoryMfeFilter extends DelayedFilterBase {
    private static final String WEBAPP_PATH = "/webapp";
    private static final String WEBAPP_CONTEXT_PATH = "/artifactory";

    /**
     * Gets all configured MFE projects from the shared registry.
     */
    private List<MfeProjectConfig> getMfeProjects() {
        return mfeProjectRegistry.getAllProjects();
    }
    private static final FileNameMap MIMETYPES = URLConnection.getFileNameMap();
    private static final String APPLICATION_JAVASCRIPT_CONTENT_TYPE = "application/javascript";
    private static final String JAVA_SCRIPT_FILE_EXTENSION = "js";
    private static final String APP_UMD_FILE = "/app.umd.js";
    private static final String MANIFEST_FILE = "/manifest.js";
    private static final String MANIFEST_UMD_FILE = "/manifest.umd.js";
    private static final String MANIFEST_ESM_FILE = "/manifest.esm.js";
    private static final String FEATURE_FLAGS_PLACEHOLDER_STRING =
            "\"THIS_IS_A_PLACEHOLDER_DO_NOT_CHANGE_OR_REMOVE_THIS_LINE\"";
    private static final String MIGRATED_ROUTES_PLACEHOLDER_STRING =
            "\"PLACEHOLDER_FOR_LIST_OF_ROUTES_DO_NOT_CHANGE_OR_REMOVE_THIS_LINE\"";
    private static final String PLACEHOLDER_FOR_CONTROLLED_ROLLOUT_FLAG =
            "\"PLACEHOLDER_FOR_CONTROLLED_ROLLOUT_FLAG_DO_NOT_CHANGE_OR_REMOVE_THIS_LINE\"";
    private static final ObjectMapper mapper = new ObjectMapper();
    MFEFileServer mfeFileServer;
    private MfeProjectRegistry mfeProjectRegistry;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String servletPath = ((HttpServletRequest) request).getServletPath();
        String resourcePath = servletPath.substring(WEBAPP_PATH.length());

        if (assertPathTraversal(request)) {
            sendNotFoundResponse(httpResponse);
            log.debug("Possible path traversal attempt! Requested path: {}", resourcePath);
            return;
        }

        if (shouldSkipCurrentFilter(request)) {
            chain.doFilter(request, response);
            return;
        }

        log.debug("serving MFE static files {}", resourcePath);
        String filename = resourcePath.toLowerCase().replace('/', File.separatorChar);
        String fileExtension = PathUtils.getExtension(filename);



        try {
            String contentType = getContentType(fileExtension, filename);
            if (resourcePath.endsWith(MANIFEST_FILE) || resourcePath.endsWith(MANIFEST_UMD_FILE) ||
                    resourcePath.endsWith(MANIFEST_ESM_FILE) || resourcePath.endsWith(APP_UMD_FILE)) {
                String merged = getManifestWithFeatureFlags(resourcePath);
                merged = substituteControlledRolloutFlagPlaceholder(merged, resourcePath);
                String staticFileContent = getStaticFileWithMigratedRoutesList(merged, resourcePath);
                httpResponse.setContentType(contentType);
                httpResponse.setStatus(200);
                httpResponse.getOutputStream().write(staticFileContent.getBytes());
                return;
            }
            mfeFileServer.serveFile(httpResponse, resourcePath, contentType);
        } catch (NotFoundException e) {
            log.debug("Failed to serve MFE file: {}", resourcePath);
            sendNotFoundResponse(httpResponse);
        }
    }

    static String getContentType(String fileExtension, String filename) {
        String contentType;
        if (JAVA_SCRIPT_FILE_EXTENSION.equals(fileExtension)) {
            contentType = APPLICATION_JAVASCRIPT_CONTENT_TYPE;
        } else {
            contentType = MIMETYPES.getContentTypeFor(filename);
        }
        return contentType;
    }

    public String getManifestWithFeatureFlags(String resourcePath) {
        FeatureFlagService featureFlagService = getArtifactoryContext().beanForType(FeatureFlagService.class);
        String featureFlags = featureFlagService.getAllFeatureFlags();
        String myManifest = mfeFileServer.readFileContent(resourcePath);
        log.debug("Adding feature flags to MFE manifest: {}", featureFlags);
        return myManifest.replace(FEATURE_FLAGS_PLACEHOLDER_STRING, featureFlags);
    }

    /**
     * Processes static file content by replacing migrated routes placeholder with actual routes.
     * If the current project has migrated routes, returns the content as-is (no replacement).
     * If the current project does not have migrated routes, collects and 
     * merges routes from all projects that have them.
     * @param staticFileContent The static file content that may contain a placeholder
     * @param resourcePath The resource path to determine which project this content belongs to
     * @return The processed static file content with migrated routes replaced,
     *   or original content if project has its own migrated routes
     */
    public String getStaticFileWithMigratedRoutesList(String staticFileContent, String resourcePath) {
        MfeProjectConfig matchingProject = mfeProjectRegistry.findProjectForResource(resourcePath);
        
        if (matchingProject == null) {
            return staticFileContent;
        }

        // If this project has migrated routes, return as-is (it handles its own routes)
        if (matchingProject.isHasMigratedRoutes()) {
            log.debug("Project {} has its own migrated routes, returning content as-is", matchingProject.getBasePath());
            return staticFileContent;
        }

        // Project does not have migrated routes - collect from all projects that have them
        List<String> allRoutes = new ArrayList<>();
        for (MfeProjectConfig project : mfeProjectRegistry.getAllProjects()) {
            if (project.isHasMigratedRoutes()) {
                allRoutes.addAll(readEffectiveUnloadRoutesFromProject(project));
            }
        }

        if (allRoutes.isEmpty()) {
            log.debug("No migrated routes found from any project for resource path: {}", resourcePath);
            return staticFileContent;
        }

        try {
            String routesArrayStr = mapper.writeValueAsString(allRoutes);
            log.debug(
                    "Replacing placeholder with {} routes from other projects for {}", 
                    allRoutes.size(), resourcePath);
            return staticFileContent.replace(MIGRATED_ROUTES_PLACEHOLDER_STRING, routesArrayStr);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize migrated routes to JSON: {}", e.getMessage(), e);
            return staticFileContent;
        }
    }

    /**
     * Replaces the controlled-rollout placeholder in a manifest with the bare boolean
     * {@code true}/{@code false} for the MFE the manifest belongs to. The MFE is resolved via
     * {@link MfeProjectRegistry#findProjectForResource(String)} (longest non-empty {@code basePath}
     * prefix, falling back to the core project). When no project can be resolved we fail closed and
     * write {@code false}, because without a known project we have no rollout signal to assert.
     */
    @VisibleForTesting
    String substituteControlledRolloutFlagPlaceholder(String staticFileContent, String resourcePath) {
        MfeProjectConfig project = mfeProjectRegistry.findProjectForResource(resourcePath);
        boolean rolloutEnabled = project != null && project.isControlledRolloutEnabled();
        return staticFileContent.replace(PLACEHOLDER_FOR_CONTROLLED_ROLLOUT_FLAG, Boolean.toString(rolloutEnabled));
    }

    /**
     * Parses migrated-routes.json and returns routes to unload from core for this MFE (controlled rollout applied).
     */
    private List<String> readEffectiveUnloadRoutesFromProject(MfeProjectConfig project) {
        String migratedRoutesPath = project.getMigratedRoutesPath();
        if (migratedRoutesPath == null) {
            return List.of();
        }

        try {
            String migratedRoutes = mfeFileServer.readFileContent(migratedRoutesPath);
            if (migratedRoutes == null || migratedRoutes.isEmpty()) {
                log.debug("No migrated routes found in {}", migratedRoutesPath);
                return List.of();
            }

            MigratedRoutesPayload payload = mapper.readValue(migratedRoutes, MigratedRoutesPayload.class);
            List<String> routesList = payload.getRoutesList();

            if (routesList.isEmpty()) {
                log.debug("No routesList entries in {}", migratedRoutesPath);
                return List.of();
            }

            boolean rolloutEnabled = project.isControlledRolloutEnabled();
            List<String> unloadFromCore =
                    MigratedRoutesPayload.computeUnloadFromCore(routesList, payload.getRolloutPhaseList(),
                            rolloutEnabled);

            List<String> rolloutPhaseList = payload.getRolloutPhaseList();
            log.info(
                    "MFE migrated routes unload recomputed for mfeId={}, rolloutEnabled={}, routesListSize={}, "
                            + "rolloutPhaseListSize={}, unloadFromCoreSize={}",
                    project.getMfeName().getValue(),
                    rolloutEnabled,
                    routesList.size(),
                    rolloutPhaseList.size(),
                    unloadFromCore.size());

            log.debug("Read {} effective unload routes from {}", unloadFromCore.size(), migratedRoutesPath);
            return unloadFromCore;
        } catch (NotFoundException e) {
            log.debug("Failed to read migrated routes list from {}", migratedRoutesPath);
        } catch (JsonProcessingException e) {
            log.debug("Failed to parse migrated routes JSON from {}: {}",
                    migratedRoutesPath, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error processing migrated routes from {}: {}",
                    migratedRoutesPath, e.getMessage(), e);
        }
        return List.of();
    }


    @VisibleForTesting
    void sendNotFoundResponse(HttpServletResponse response) throws IOException {
        ArtifactoryResponse artifactoryResponse = new HttpArtifactoryResponse(response);
        String msg = "webapp resource wasn't found";
        artifactoryResponse.sendError(HttpStatus.SC_NOT_FOUND, msg, log);
    }

    @VisibleForTesting
    boolean shouldSkipCurrentFilter(ServletRequest request) {
        String requestURI = ((HttpServletRequest) request).getRequestURI();
        log.debug("request path: {}", requestURI);

        for (MfeProjectConfig project : getMfeProjects()) {
            String jsPath = WEBAPP_CONTEXT_PATH + project.getJsPath(WEBAPP_PATH);
            String imgPath = WEBAPP_CONTEXT_PATH + project.getImgPath(WEBAPP_PATH);
            String assetsPath = WEBAPP_CONTEXT_PATH + project.getAssetsPath(WEBAPP_PATH);
            String migratedRoutesPath = project.getMigratedRoutesPath();
            String fullMigratedRoutesPath = migratedRoutesPath != null
                    ? WEBAPP_CONTEXT_PATH + WEBAPP_PATH + migratedRoutesPath
                    : null;
            if (requestURI.contains(jsPath) || requestURI.contains(imgPath) || requestURI.contains(assetsPath)
                    || (fullMigratedRoutesPath != null && requestURI.equals(fullMigratedRoutesPath))) {
                return false; // Don't skip - this request should be handled
            }
        }
        return true;
    }

    @VisibleForTesting
    boolean assertPathTraversal(ServletRequest request) throws IOException {
        String path = ((HttpServletRequest) request).getRequestURI().toLowerCase();
        File filepath = new File(path);
        String pathUsingCanonical = filepath.getCanonicalPath().toLowerCase();
        String pathUsingAbsolute = filepath.getAbsolutePath().toLowerCase();
        return !pathUsingCanonical.equals(pathUsingAbsolute);
    }

    @Override
    protected void initLater(FilterConfig config) {
        ArtifactoryContext artifactoryContext = getArtifactoryContext();
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        ArtifactoryHome.bind(artifactoryContext.getArtifactoryHome());
        mfeFileServer = artifactoryContext.beanForType(MFEFileServer.class);
        mfeProjectRegistry = artifactoryContext.beanForType(MfeProjectRegistry.class);
    }

}