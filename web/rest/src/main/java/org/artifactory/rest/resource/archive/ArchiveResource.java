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

package org.artifactory.rest.resource.archive;


import com.jfrog.build.acl.bridge.BuildFacade;
import com.jfrog.build.acl.model.api.BuildArtifactsMapping;
import com.jfrog.build.acl.model.api.BuildArtifactsRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.http.HttpStatus;
import org.apache.sshd.common.util.io.IoUtils;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.PathValidatorUtil;
import org.artifactory.api.download.FolderDownloadResult;
import org.artifactory.api.repo.exception.BlackedOutException;
import org.artifactory.api.rest.constant.ArchiveRestConstants;
import org.artifactory.api.rest.constant.BuildRestConstants;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.download.FolderDownloadException;
import org.artifactory.mime.MimeType;
import org.artifactory.mime.NamingUtils;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.exception.UnauthorizedException;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.rest.util.ErrorMessagesConstants;
import org.artifactory.traffic.TrafficEntry;
import org.artifactory.traffic.TrafficService;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.HttpUtils;
import org.glassfish.jersey.server.CloseableService;
import org.jfrog.common.archive.ArchiveType;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.api.rest.constant.ArchiveRestConstants.FOLDER_DOWNLOAD_DEBUG_ERROR_MESSAGE_PREFIX;

/**
 * Resource class which handles archive operations
 *
 * @author Shay Yaakov
 */
@Slf4j
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(ArchiveRestConstants.PATH_ROOT)
@RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
@ActivityDetails(group = RestApiGroup.ARTIFACT_API)
public class ArchiveResource {

    @Context
    private CloseableService closeableService;
    @Autowired
    @Setter
    private SecurityService securityService;
    @Autowired
    private CentralConfigService centralConfig;
    @Autowired
    private AddonsManager addonsManager;
    @Autowired
    private TrafficService trafficService;
    @Autowired
    private BuildFacade buildFacade;

    @POST
    @Path(ArchiveRestConstants.PATH_BUILD_ARTIFACTS)
    @Consumes({BuildRestConstants.MT_BUILD_ARTIFACTS_REQUEST, MediaType.APPLICATION_JSON})
    public Response getBuildArtifactsArchive(BuildArtifactsRequest buildArtifactsRequest) {
        if (isBlank(buildArtifactsRequest.getBuildName())) {
            throw new BadRequestException(ErrorMessagesConstants.BLANK_BUILD_NAME_MESSAGE);
        }
        boolean buildNumberIsBlank = isBlank(buildArtifactsRequest.getBuildNumber());
        boolean buildStatusIsBlank = isBlank(buildArtifactsRequest.getBuildStatus());
        if (buildNumberIsBlank && buildStatusIsBlank) {
            throw new BadRequestException(ErrorMessagesConstants.BLANK_BUILD_NUMBER_AND_STATUS_MESSAGE);
        }
        if (!buildNumberIsBlank && !buildStatusIsBlank) {
            throw new BadRequestException(ErrorMessagesConstants.BUILD_NUMBER_AND_STATUS_BOTH_PROVIDED_MESSAGE);
        }

        if (buildArtifactsRequest.getArchiveType() == null) {
            throw new BadRequestException(ErrorMessagesConstants.ARCHIVE_TYPE_EMPTY_MESSAGE);
        }

        if (!areMappingOutputsValid(buildArtifactsRequest)) {
            throw new BadRequestException(ErrorMessagesConstants.INVALID_MAPPING_OUTPUT_MESSAGE);
        }

        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        try {
            final File buildArtifactsArchive = restAddon.getBuildArtifactsArchive(buildArtifactsRequest);
            if (buildArtifactsArchive == null) {
                throw new NotFoundException(
                        String.format(ErrorMessagesConstants.BUILD_ARTIFACTS_ARCHIVE_NOT_FOUND_MESSAGE,
                                buildArtifactsRequest.getBuildName(),
                                buildArtifactsRequest.getBuildNumber()));
            }

            markForDeletionAtResponseEnd(buildArtifactsArchive);
            MimeType mimeType = NamingUtils.getMimeType(buildArtifactsArchive.getName());
            long archiveSize = buildArtifactsArchive.length();

            return Response
                    .ok(createStreaming(buildArtifactsRequest, buildArtifactsArchive, archiveSize))
                    .type(mimeType.getType()).build();
        } catch (IOException e) {
            log.error("Failed to create builds artifacts archive: " + e.getMessage(), e);
            throw new NotFoundException(ErrorMessagesConstants.BUILD_ARTIFACTS_ARCHIVE_FAILED);
        }
    }

    private boolean areMappingOutputsValid(BuildArtifactsRequest buildArtifactsRequest) {
        List<BuildArtifactsMapping> mappings = buildArtifactsRequest.getMappings();
        if (CollectionUtils.isNullOrEmpty(mappings)) {
            return true;
        }
        return buildArtifactsRequest
                .getMappings()
                .stream()
                .map(BuildArtifactsMapping::getOutput)
                .filter(Objects::nonNull)
                .allMatch(PathValidatorUtil::isPathForArchiveIsValid);
    }

    @GET
    @Path("download/{repoKey}/{path: .+}")
    public Response downloadFolder(@PathParam("repoKey") String repoKey, @PathParam("path") String path,
            @QueryParam("archiveType") String archiveTypeString,
            @QueryParam("includeChecksumFiles") Boolean includeChecksumFiles) {
        return downloadFolderOrRepo(repoKey, path, archiveTypeString, includeChecksumFiles);
    }

    @GET
    @Path("download/{repoKey}")
    public Response downloadFolderOrRepo(@PathParam("repoKey") String repoKey,
            @QueryParam("archiveType") String archiveTypeString,
            @QueryParam("includeChecksumFiles") Boolean includeChecksumFiles) {
        return downloadFolderOrRepo(repoKey, "", archiveTypeString, includeChecksumFiles);
    }

    private Response downloadFolderOrRepo(String repoKey, String path, String archiveTypeString,
            Boolean includeChecksumFiles) {
        if (securityService.isAnonymous() &&
                !centralConfig.getDescriptor().getFolderDownloadConfig().isEnabledForAnonymous()) {
            throw new UnauthorizedException("You must be logged in to download a folder or repository.");
        }
        RepoPath pathToDownload = RepoPathFactory.create(repoKey, path);
        ArchiveType archiveType;
        try {
            archiveType = ArchiveType.fromValue(archiveTypeString);
        } catch (IllegalArgumentException iae) {
            log.debug("", iae);
            throw new BadRequestException(iae.getMessage());
        }
        boolean includeChecksums = shouldIncludeChecksumFiles(includeChecksumFiles);
        Response.ResponseBuilder response;
        try {
            FolderDownloadResult result = addonsManager.addonByType(RestAddon.class)
                    .downloadFolderOrRepo(pathToDownload, archiveType, includeChecksums);
            response =
                    Response.status(HttpStatus.SC_OK).type(MediaType.WILDCARD).entity((StreamingOutput) result::accept);
        } catch (FolderDownloadException fde) {
            response = Response.status(fde.getCode()).entity(fde.getMessage());
            log.debug(FOLDER_DOWNLOAD_DEBUG_ERROR_MESSAGE_PREFIX + "{}", pathToDownload, fde);
        } catch (BlackedOutException boe) {
            response = Response.status(boe.getErrorCode()).entity(boe.getMessage());
            log.debug(FOLDER_DOWNLOAD_DEBUG_ERROR_MESSAGE_PREFIX + "{}", pathToDownload, boe);
        } catch (Exception e) {
            response = Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).entity(e.getMessage());
            log.debug(FOLDER_DOWNLOAD_DEBUG_ERROR_MESSAGE_PREFIX + "{}", pathToDownload, e);
        }
        return response.build();
    }

    private boolean shouldIncludeChecksumFiles(Boolean includeChecksumFiles) {
        if (includeChecksumFiles == null) {
            return false;
        } else {
            return includeChecksumFiles;
        }
    }

    private void markForDeletionAtResponseEnd(final File buildArtifactsArchive) {
        // delete the file after jersey streamed it back to the client
        closeableService.add(() -> FileUtils.deleteQuietly(buildArtifactsArchive));
    }

    private StreamingOutput createStreaming(BuildArtifactsRequest buildArtifactsRequest, File buildArtifactsArchive,
            long archiveSize) {
        return outputStream ->
        {
            CountingInputStream countingStream = null;
            try {
                InputStream archiveStream = getArchiveStream(buildArtifactsArchive);
                countingStream = new CountingInputStream(archiveStream);
                countingStream.transferTo(outputStream);
            } catch (Exception e) {
                log.error("Error streaming build archive for {}", buildArtifactsRequest.getBuildName(), e);
            } finally {
                if (countingStream != null) {
                    IoUtils.closeQuietly(countingStream);
                    logTraffic(buildArtifactsRequest, archiveSize, countingStream.getByteCount());
                }
            }
        };
    }

    InputStream getArchiveStream(File buildArtifactsArchive) throws FileNotFoundException {
        return new FileInputStream(buildArtifactsArchive);
    }

    void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    void setCloseableService(CloseableService closeableService) {
        this.closeableService = closeableService;
    }

    void setBuildFacade(BuildFacade buildFacade) {
        this.buildFacade = buildFacade;
    }

    void setTrafficService(TrafficService trafficService) {
        this.trafficService = trafficService;
    }

    private void logTraffic(BuildArtifactsRequest buildArtifactsRequest, long archiveSize, long byteCount) {
        String remoteAddress = HttpUtils.getRemoteClientAddress();
        String buildRepo = buildFacade
                .getPreferredBuildRepo(buildArtifactsRequest.getBuildRepo(), buildArtifactsRequest.getProject());
        String buildName = buildArtifactsRequest.getBuildName();
        String buildNumber = buildArtifactsRequest.getBuildNumber();
        String name = isBlank(buildName) ? buildNumber : buildName;
        String repoPath = String.format("%s:%s", buildRepo, name);

        TrafficEntry entry = TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .actor(TrafficEntry.TrafficActor.USER)
                .redirectTarget(TrafficEntry.TrafficTarget.DIRECT)
                .userAddress(remoteAddress)
                .repoPath(repoPath)
                .traceId(MDC.get("uber-trace-id"))
                .size(archiveSize)
                .requestedSize(archiveSize)
                .transferredSize(byteCount)
                .build();
        trafficService.handleTrafficEntry(entry);
    }
}
