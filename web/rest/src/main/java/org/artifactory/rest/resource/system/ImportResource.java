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

package org.artifactory.rest.resource.system;


import ch.qos.logback.classic.Level;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.aol.AolService;
import org.artifactory.api.common.ImportExportStatusHolder;
import org.artifactory.api.config.ImportService;
import org.artifactory.api.config.ImportSettingsImpl;
import org.artifactory.api.config.PathValidatorUtil;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.rest.constant.ImportRestConstants;
import org.artifactory.api.rest.constant.RepositoriesRestConstants;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.importexport.ImportIsDisabledException;
import org.artifactory.rest.exception.ForbiddenWebAppException;
import org.artifactory.sapi.common.ImportKind;
import org.artifactory.sapi.common.ImportSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * @author freds
 * @author Tomer Cohen
 */
@Slf4j
@Path(ImportRestConstants.PATH_ROOT)
@RolesAllowed(AuthorizationService.ROLE_ADMIN)
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
public class ImportResource {

    @Context
    HttpServletResponse httpResponse;
    @Autowired
    RepositoryService repositoryService;
    @Autowired
    SecurityService securityService;
    @Autowired
    ImportService importService;

    @GET
    @Path(ImportRestConstants.SYSTEM_PATH)
    @Produces({SystemRestConstants.MT_IMPORT_SETTINGS, MediaType.APPLICATION_JSON})
    public ImportSettingsConfigurationImpl settingsExample() {
        ImportSettingsConfigurationImpl settingsConfiguration = new ImportSettingsConfigurationImpl();
        settingsConfiguration.importPath = "/import/path";
        return settingsConfiguration;
    }

    @POST
    @Path(ImportRestConstants.SYSTEM_PATH)
    @Consumes({SystemRestConstants.MT_IMPORT_SETTINGS, MediaType.APPLICATION_JSON})
    public Response activateImport(ImportSettingsConfigurationImpl settings) {
        log.debug("Activating import {}", settings);
        if (!ConstantValues.systemImportEnabled.getBoolean()) {
            throw new ForbiddenWebAppException(ImportIsDisabledException.SYSTEM_IMPORT_IS_DISABLED_MESSAGE);
        }
        AolService aolService = ContextHelper.get().beanForType(AolService.class);

        if (!aolService.isAol() && settings.hybridMigration) {
            String message = "The hybrid migration available only for Artifactory SaaS instances";
            return Response.status(HttpStatus.SC_FORBIDDEN).entity(message).build();
        }

        if (aolService.isAol() && !aolService.isDashboardUser()) {
            String message = "The System Import and Export features are not available for Artifactory SaaS users";
            return Response.status(HttpStatus.SC_FORBIDDEN).entity(message).build();
        }

        if (!PathValidatorUtil.isImportValidPath(settings.importPath)) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ImportSettings.INVALID_IMPORT_DIR).build();
        }
        if (!PathValidatorUtil.isImportPathInAllowList(settings.importPath)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ImportSettings.IMPORT_PATH_NOT_IN_ALLOWLIST).build();
        }
        ImportExportStatusHolder holder = settings.hybridMigration ?
                new ImportExportStatusHolder() : new ImportExportStreamStatusHolder(httpResponse);
        try {
            ImportSettingsImpl importSettings = new ImportSettingsImpl(new File(settings.importPath), holder);
            importSettings.setIncludeMetadata(settings.includeMetadata);
            importSettings.setVerbose(settings.verbose);
            importSettings.setFailFast(settings.failOnError);
            importSettings.setFailIfEmpty(settings.failIfEmpty);
            importSettings.setShouldActOnMissionControl(settings.importMissionControl);
            importSettings.setEnableCopySecurityAccessDir(settings.isEnableCopySecurityAccessDir); //For Aol use
            if (settings.hybridMigration) {
                importSettings.setImportKind(ImportKind.HYBRID_MIGRATION);
                importSettings.setMarkerFile(settings.markerFile);
                importSettings.setIncludeMetadata(false);
                importSettings.setExcludeContent(true);
                ContextHelper.get().beanForType(ImportService.class).importAsync(importSettings);
                return Response.ok().build();
            }
            importService.importSync(importSettings);
            if (!httpResponse.isCommitted() && holder.hasErrors()) {
                return Response.serverError().entity(holder.getLastError().getMessage()).build();
            }
        } catch (Exception e) {
            holder.error("Received uncaught exception", e, log);
            if (!httpResponse.isCommitted()) {
                return Response.serverError().entity(e.getMessage()).build();
            }
        }
        return Response.ok().build();
    }

    @POST
    @Path(ImportRestConstants.REPOSITORIES_PATH)
    public void importRepositories(
            //The base path to import from (may contain a single repo or multiple repos with named sub folders
            @QueryParam(RepositoriesRestConstants.PATH) String path,
            //Empty/null repo -> all
            @QueryParam(RepositoriesRestConstants.TARGET_REPO) String targetRepo,
            //Include metadata - default 1
            @QueryParam(RepositoriesRestConstants.INCLUDE_METADATA) String includeMetadata,
            //Verbose - default 0
            @QueryParam(RepositoriesRestConstants.VERBOSE) String verbose) {

        if (StringUtils.isBlank(path)) {
            throw new BadRequestException("You must provide a repository path to import from.");
        }

        if (!ConstantValues.repositoryImportEnabled.getBoolean()) {
            throw new ForbiddenWebAppException(ImportIsDisabledException.REPOSITORIES_IMPORT_IS_DISABLED_MESSAGE);
        }

        if (!PathValidatorUtil.isImportValidPath(path)) {
            throw new BadRequestException(ImportSettings.INVALID_IMPORT_DIR);
        }
        if (!PathValidatorUtil.isImportPathInAllowList(path)) {
            throw new BadRequestException(ImportSettings.IMPORT_PATH_NOT_IN_ALLOWLIST);
        }

        ImportExportStreamStatusHolder statusHolder = new ImportExportStreamStatusHolder(httpResponse);
        String repoNameToImport = targetRepo;
        if (StringUtils.isBlank(repoNameToImport)) {
            repoNameToImport = "All repositories";
        }
        statusHolder.status("Starting Repositories Import of " + repoNameToImport + " from " + path, log);
        if (!securityService.isAdmin()) {
            statusHolder.error(
                    "The user: '" + securityService.currentUsername() +
                            "' is not permitted to import repositories",
                    HttpStatus.SC_FORBIDDEN, log);
            return;
        }
        AolService aolService = ContextHelper.get().beanForType(AolService.class);
        if (aolService.isAol() && !aolService.isDashboardUser()) {
            statusHolder.errorWithDifferentLogLevel(
                    "Import repositories from server is not permitted when running on the cloud",
                    HttpStatus.SC_FORBIDDEN, log, Level.WARN);
            return;
        }
        if (StringUtils.isEmpty(path)) {
            statusHolder.error("Source directory path may not be empty.", HttpStatus.SC_BAD_REQUEST, log);
        }

        File baseDir = new File(path);
        if (!baseDir.exists()) {
            statusHolder.error("Directory " + path + " does not exist.", HttpStatus.SC_BAD_REQUEST, log);
            return;
        }
        ImportSettingsImpl importSettings = new ImportSettingsImpl(baseDir, statusHolder);
        if (StringUtils.isNotBlank(includeMetadata)) {
            importSettings.setIncludeMetadata(Integer.parseInt(includeMetadata) == 1);
        }
        if (StringUtils.isNotBlank(verbose)) {
            importSettings.setVerbose(Integer.parseInt(verbose) == 1);
        }
        try {
            if (StringUtils.isBlank(targetRepo)) {
                repositoryService.importAll(importSettings);
            } else {
                importSettings.setIndexMarkedArchives(true);
                repositoryService.importRepo(targetRepo, importSettings);
            }
        } catch (Exception e) {
            statusHolder.error("Unable to import repository", e, log);
        }
    }
}
