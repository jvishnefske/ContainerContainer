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


import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.ExportSettingsImpl;
import org.artifactory.api.config.PathValidatorUtil;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.rest.constant.ImportRestConstants;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.model.RepoConfig;
import org.artifactory.sapi.common.ExportSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * @author freds
 */
@Slf4j
@Path(SystemRestConstants.PATH_EXPORT)
@RolesAllowed(AuthorizationService.ROLE_ADMIN)
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
public class ExportResource {

    @Context
    HttpServletResponse httpResponse;

    @Autowired
    private RepositoryService repoService;

    @GET
    @Path(ImportRestConstants.SYSTEM_PATH)
    @Produces({SystemRestConstants.MT_EXPORT_SETTINGS, MediaType.APPLICATION_JSON})
    public ExportSettingsConfigurationImpl settingsExample() {
        assertNotAol();
        ExportSettingsConfigurationImpl settings = new ExportSettingsConfigurationImpl();
        settings.setExportPath("/export/path");
        return settings;
    }

    @POST
    @Path(ImportRestConstants.SYSTEM_PATH)
    @Consumes({SystemRestConstants.MT_EXPORT_SETTINGS, MediaType.APPLICATION_JSON})
    public Response activateExport(ExportSettingsConfigurationImpl settings) {
        assertNotAol();
        assertNoColdInstance(settings);
        if (!PathValidatorUtil.isExportImportValidPath(settings.getExportPath())) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ExportSettings.INVALID_EXPORT_DIR).build();
        }
        if (!PathValidatorUtil.isExportPathInAllowList(settings.getExportPath())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ExportSettings.EXPORT_PATH_NOT_IN_ALLOWLIST).build();
        }
        ImportExportStreamStatusHolder holder = new ImportExportStreamStatusHolder(httpResponse);
        ExportSettingsImpl exportSettings = new ExportSettingsImpl(new File(settings.getExportPath()), holder);
        exportSettings.setIncludeMetadata(settings.isIncludeMetadata());
        exportSettings.setCreateArchive(settings.isCreateArchive());
        exportSettings.setIgnoreRepositoryFilteringRulesOn(settings.isBypassFiltering());
        exportSettings.setVerbose(settings.isVerbose());
        exportSettings.setFailFast(settings.isFailOnError());
        exportSettings.setFailIfEmpty(settings.isFailIfEmpty());
        exportSettings.setM2Compatible(settings.isM2());
        exportSettings.setIncremental(settings.isIncremental());
        exportSettings.setExcludeContent(settings.isExcludeContent());
        exportSettings.setExcludeArtifactBundles(settings.isExcludeArtifactBundles());
        exportSettings.setShouldActOnMissionControl(settings.isExportMissionControl());

        if (settings.isIncludeMetadata() || !settings.isExcludeContent()) {
            exportSettings.setRepositories(getAllLocalRepoKeys());
        }
        log.debug("Activating export {}", settings);
        try {
            ContextHelper.get().exportTo(exportSettings);
            if (!httpResponse.isCommitted() && holder.isError()) {
                return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).entity(
                        "Export finished with errors. Check " +
                                "Artifactory logs for more details."
                ).build();
            }
        } catch (Exception e) {
            if (!httpResponse.isCommitted()) {
                return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
            }
        }
        return Response.ok().build();
    }

    private void assertNoColdInstance(ExportSettingsConfigurationImpl exportSettings) {
        if (exportSettings.isExcludeContent()) {
            return;
        }
        ArtifactRetentionAddon artifactRetentionAddon = ContextHelper.get().beanForType(AddonsManager.class)
                .addonByType(ArtifactRetentionAddon.class);

        if (artifactRetentionAddon.isColdInstanceV1orV2()) {
            log.debug("Canceling system export request - exporting binaries in not supported in cold instance");
            throw new BadRequestException("Exporting binaries in not supported in cold instance");
        }
    }

    private List<String> getAllLocalRepoKeys() {
        return repoService.getLocalFederatedAndCachedRepoConfigs().stream()
                .map(RepoConfig::getKey)
                .toList();
    }

    private void assertNotAol() {
        AolService aolService = ContextHelper.get().beanForType(AolService.class);
        if (aolService.isAol() && !aolService.isDashboardUser()) {
            log.debug("Canceling system export request - not allowed on AOL");
            throw new BadRequestException("Export System function is not supported when running on the cloud");
        }
    }
}
