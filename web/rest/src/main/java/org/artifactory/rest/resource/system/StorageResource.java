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


import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.aol.AolService;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.jackson.JacksonFactory;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.backup.InternalBackupService;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.descriptor.backup.BackupDescriptor;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.artifactory.storage.jobs.FilestorePrunerJob;
import org.artifactory.util.AolUtils;
import org.jfrog.storage.binstore.ifc.model.BinaryProvidersInfo;
import org.jfrog.storage.common.prune.PruneRequestModel;
import org.jfrog.storage.common.prune.PruneResponseModel;
import org.jfrog.storage.common.prune.PruneStatusReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import static org.artifactory.storage.fs.service.ConfigsServiceKey.STORAGE_PRUNE_REPORT;
import static org.jfrog.common.ExceptionLogUtils.debugOrError;

/**
 * @author yoavl
 */
@RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
public class StorageResource {
    private static final Logger log = LoggerFactory.getLogger(StorageResource.class);
    private final HttpServletResponse httpResponse;
    private final StorageService storageService;
    private final InternalBackupService backupService;
    private final ArtifactoryBinaryService binaryStore;
    private final TaskService taskService;
    private final ConfigsService configsService;
    public StorageResource(StorageService storageService, InternalBackupService backupService,
            ArtifactoryBinaryService binaryStore, TaskService taskService, ConfigsService configsService,
            HttpServletResponse httpResponse) {
        this.storageService = storageService;
        this.httpResponse = httpResponse;
        this.binaryStore = binaryStore;
        this.backupService = backupService;
        this.taskService = taskService;
        this.configsService = configsService;
    }

    @POST
    @Path("compress")
    public Response compress() {
        BasicStatusHolder statusHolder = new ImportExportStreamStatusHolder(httpResponse);
        storageService.compress(statusHolder);
        return response(statusHolder);
    }

    @GET
    @Path("size")
    @Produces(MediaType.TEXT_PLAIN)
    @ActivityDetails(group = RestApiGroup.SYSTEM_INFO_API)
    public String size() {
        return binaryStore.getStorageSize() + "";
    }

    @POST
    @Path("exportds")
    @Deprecated
    public Response activateExport(@QueryParam("to") String destDir) {
        throw new IllegalStateException("Export data is no longer supported");
    }

    @POST
    @Path("gc")
    public Response activateGc() {
        BasicStatusHolder statusHolder = new ImportExportStreamStatusHolder(httpResponse);
        storageService.callManualGarbageCollect(statusHolder);
        if (statusHolder.isError() || statusHolder.getException() != null) {
            return response(statusHolder, statusHolder.getStatusCode());
        }
        return response(statusHolder);
    }

    @GET
    @Path("prune/status")
    @ActivityDetails(group = RestApiGroup.SYSTEM_INFO_API)
    public Response pruneStatus() {
        PruneStatusReport pruneStatus = configsService.getConfig(STORAGE_PRUNE_REPORT, PruneStatusReport.class);
        return pruneStatus == null ?
                Response.status(HttpStatus.SC_PRECONDITION_FAILED).entity(new PruneResponseModel("No Prune task found"))
                        .build() :
                Response.ok(pruneStatus).build();
    }

    @POST
    @Path("prune/start")
    public Response pruneStart(@Valid PruneRequestModel pruneRequestModel) {
        Response.ResponseBuilder res;
        try {
            AolUtils.assertDashboardUser();
            if (pruneRequestModel == null) {
                pruneRequestModel = PruneRequestModel.EMPTY;
            }
            // run the Prune task
            // If the previous task is still running, then IllegalStateException will be thrown
            StatusHolder taskInvocationResult = createAndRunPruneTask(pruneRequestModel);
            if (taskInvocationResult.isError()) {
                log.error("Pruning Unreferenced Data task cannot be started: {}",
                        taskInvocationResult.getLastError());
                log.debug("Pruning Unreferenced Data task cannot be started: {}",
                        taskInvocationResult.getLastError(), taskInvocationResult.getException());
                res = Response.status(HttpStatus.SC_PRECONDITION_FAILED)
                        .entity(new PruneResponseModel("Pruning Unreferenced Data task cannot be started"));
            } else {
                if (StringUtils.isBlank(pruneRequestModel.getStartFromDirectory())) {
                    res = Response.status(HttpStatus.SC_ACCEPTED)
                            .entity(new PruneResponseModel("Pruning Unreferenced Data task has been submitted"));
                } else {
                    res = Response.status(HttpStatus.SC_ACCEPTED)
                            .entity(new PruneResponseModel("Prune Unreferenced Data task resumes from directory " +
                                    pruneRequestModel.getStartFromDirectory()));
                }
            }
        } catch (BadRequestException e) { // may be thrown by assertDashboardUser()
            log.warn("Not sufficient privileges to start Pruning Unreferenced Data task: {}", e.getMessage());
            log.debug("Not sufficient privileges to start Pruning Unreferenced Data task: {}", e.getMessage(), e);
            res = Response.status(HttpStatus.SC_FORBIDDEN).entity(new PruneResponseModel(e.getMessage()));
        } catch (Exception e) {
            log.error("Cannot start Pruning Unreferenced Data task: {}", e.getMessage());
            log.debug("Cannot start Pruning Unreferenced Data task: {}", e.getMessage(), e);
            res = Response.serverError()
                    .entity(new PruneResponseModel("Cannot start Pruning Unreferenced Data task: " + e.getMessage()));
        }
        return res.build();
    }

    @POST
    @Path("prune/stop")
    @Produces("application/json")
    public Response pruneStop() {
        try {
            AolUtils.assertDashboardUser();
        } catch (BadRequestException e) {
            log.warn("Not sufficient privileges to stop Pruning Unreferenced Data task: {}", e.getMessage());
            log.debug("Not sufficient privileges to stop Pruning Unreferenced Data task: {}", e.getMessage(), e);
            return Response.status(HttpStatus.SC_FORBIDDEN).entity(new PruneResponseModel(e.getMessage())).build();
        }
        Response.ResponseBuilder res;
        PruneStatusReport pruneStatus = configsService.getConfig(STORAGE_PRUNE_REPORT, PruneStatusReport.class);
        if (pruneStatus == null) {
            res = Response.status(HttpStatus.SC_PRECONDITION_FAILED)
                    .entity(new PruneResponseModel("No running Prune task found"));
        } else {
            try {
                // Put the Prune-stop marker in DB. A running Prune task checks for existence of this marker.
                // Once it's found, the Prune task will stop its execution.
                configsService.addOrUpdateConfig(ConfigsServiceKey.STORAGE_PRUNE_STOP_MARKER, true,
                        System.currentTimeMillis());
                res = Response.status(HttpStatus.SC_ACCEPTED)
                                .entity(new PruneResponseModel("Prune task stop request submitted"));
            } catch (Exception e) {
                res = Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                        .entity(new PruneResponseModel("Could not stop Prune task due to " + e.getMessage()));
            }
        }
        return res.build();
    }

    BasicStatusHolder createAndRunPruneTask(PruneRequestModel pruneRequestModel) {
        Task task = TaskUtils.createManualTask(FilestorePrunerJob.class, 0L);
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        taskService.checkCanStartManualTask(task, FilestorePrunerJob.class, statusHolder);
        if (!statusHolder.isError()) {
            try {
                task.addAttribute("PRUNE_REQUEST_MODEL", pruneRequestModel);
                taskService.startTask(task, true, true);
            } catch (Exception e) {
                statusHolder.error("Error activating Prune task: " + e.getMessage(), e, log);
            }
        }
        return statusHolder;
    }

    @POST
    @Path("backup")
    public Response activateBackup(@QueryParam("key") String backupKey) {
        final BasicStatusHolder statusHolder = new ImportExportStreamStatusHolder(httpResponse);
        BackupDescriptor backupDescriptor = backupService.getBackup(backupKey);
        if (backupDescriptor != null) {
            if (backupDescriptor.isEnabled()) {
                backupService.scheduleImmediateSystemBackup(backupDescriptor, statusHolder);
                return response(statusHolder);
            } else {
                return Response.serverError().entity(
                        "Backup identified with key '" + backupKey + "' is disabled").build();
            }
        } else {
            return Response.serverError().entity("No backup identified with key '" + backupKey + "'").build();
        }
    }

    @GET
    @Path("info")
    @ActivityDetails(group = RestApiGroup.SYSTEM_INFO_API)
    public Response getStorageInfo() {
        AolService aolService = ContextHelper.get().beanForType(AolService.class);
        if (aolService.isAol() && !aolService.isDashboardUser()) {
            return Response.status(HttpStatus.SC_METHOD_NOT_ALLOWED)
                    .entity("Artifactory SaaS does not support this feature.").build();
        }
        BinaryProvidersInfo<Map<String, String>> binaryProviderInfo = storageService.getBinaryProviderInfo();
        // Serialize the result to Json
        ObjectMapper objectMapper = JacksonFactory.createObjectMapper();
        String info;
        try {
            info = objectMapper.writeValueAsString(binaryProviderInfo.rootTreeElement);
            return Response.ok().entity(info).build();
        } catch (IOException e) {
            String message = "Failed to serialize the binary provider info to JSON";
            log.error(message);
            log.debug(message, e);
            return Response.serverError().build();
        }
    }

    @POST
    @Path("optimize")
    public Response optimize() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        storageService.runShardingBalancer(statusHolder);
        if (statusHolder.isError()) {
            if (statusHolder.getException() != null) {
                debugOrError(log, "Sharding balancer cannot be started: " + statusHolder.getLastError(),
                        statusHolder.getException());
                return statusHolder.getStatusCode() == HttpStatus.SC_CONFLICT ?
                        response(statusHolder, HttpStatus.SC_CONFLICT) :
                        response(statusHolder, HttpStatus.SC_PRECONDITION_FAILED);
            }
            return response(statusHolder, HttpStatus.SC_PRECONDITION_FAILED);
        }
        return response(statusHolder, HttpStatus.SC_ACCEPTED);
    }

    private Response response(BasicStatusHolder statusHolder) {
        return response(statusHolder, HttpStatus.SC_OK);
    }

    private Response response(BasicStatusHolder statusHolder, int statusCode) {
        return statusHolder.getException() == null ? Response.status(statusCode).build() :
                Response.status(statusCode).entity(statusHolder.getStatusMsg()).build();
    }
}