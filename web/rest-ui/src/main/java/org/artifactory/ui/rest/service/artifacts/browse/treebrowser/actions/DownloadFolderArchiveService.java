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

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.download.FolderDownloadResult;
import org.artifactory.api.download.FolderDownloadService;
import org.artifactory.api.repo.exception.BlackedOutException;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.security.SecurityService;
import org.artifactory.download.FolderDownloadException;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.request.InternalArtifactoryResponse;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.common.service.StreamRestResponse;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.DownloadFolder;
import org.jfrog.client.util.PathUtils;
import org.jfrog.common.archive.ArchiveType;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.artifactory.api.request.InternalRequestFactory.createInternalRequestDisableRedirect;
import static org.artifactory.api.rest.constant.ArchiveRestConstants.FOLDER_DOWNLOAD_DEBUG_ERROR_MESSAGE_PREFIX;
import static org.artifactory.api.rest.constant.ArchiveRestConstants.FOLDER_DOWNLOAD_ERROR_PATTERN;

/**
 * Downloads a folder in the requested format.
 *
 * @author Dan Feldman
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DownloadFolderArchiveService implements RestService {

    private final FolderDownloadService folderDownloadService;

    private final SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        if (securityService.isAnonymous() && !folderDownloadService.getFolderDownloadConfig().isEnabledForAnonymous()) {
            response.error("You must be logged in to download a folder or repository.").responseCode(SC_FORBIDDEN);
            return;
        }

        DownloadFolder folderToDownload = new DownloadFolder(request);
        String repoKey = folderToDownload.getRepoKey();
        String path = folderToDownload.getPath();
        RepoPath pathToDownload = RepoPathFactory.create(repoKey, path);
        try {
            InternalArtifactoryResponse res = interceptResponse(pathToDownload);
            if (res.isError()) {
                response.error(res.getStatusMessage()).responseCode(res.getStatus());
                return;
            }
        } catch (IOException e) {
            log.error("Exception occurred during response interception: {}", e.getMessage());
            log.debug("Exception occurred during response interception: {}", e.getMessage(), e);
            response.error(e.getMessage()).responseCode(HttpStatus.SC_BAD_REQUEST);
            return;
        }
        respondWithArchiveStream((StreamRestResponse) response, getArchiveType(folderToDownload.getArchiveType()),
                pathToDownload, folderToDownload.getIncludeChecksumFiles());
    }

    InternalArtifactoryResponse interceptResponse(RepoPath pathToDownload) throws IOException {
        InternalArtifactoryRequest req = createInternalRequestDisableRedirect(pathToDownload);
        InternalArtifactoryResponse res = new InternalArtifactoryResponse();
        ContextHelper.get().beanForType(AddonsManager.class).interceptResponse(res, req, false);
        return res;
    }

    private void respondWithArchiveStream(StreamRestResponse response, ArchiveType archiveType, RepoPath folder,
            Boolean includeChecksumFiles) {
        try {
            FolderDownloadResult result =
                    folderDownloadService.downloadFolder(folder, archiveType, includeChecksumFiles);
            response.setDownloadFile(createArchiveFileName(folder, archiveType));
            response.setDownload(true);
            response.iModel((StreamingOutput) result::accept);
        } catch (FolderDownloadException fde) {
            response.responseCode(fde.getCode()).error(fde.getMessage());
            log.warn(FOLDER_DOWNLOAD_ERROR_PATTERN, folder.toPath(), fde.getMessage());
            log.debug(FOLDER_DOWNLOAD_DEBUG_ERROR_MESSAGE_PREFIX + folder.toPath(), fde);
        } catch (BlackedOutException boe) {
            response.responseCode(boe.getErrorCode()).error(boe.getMessage());
            log.error(FOLDER_DOWNLOAD_ERROR_PATTERN, folder.toPath(), boe.getMessage());
            log.debug(FOLDER_DOWNLOAD_DEBUG_ERROR_MESSAGE_PREFIX + folder.toPath(), boe);
        } catch (Exception e) {
            response.error(e.getMessage());
            log.error(FOLDER_DOWNLOAD_ERROR_PATTERN, folder.toPath(), e.getMessage());
            log.debug(FOLDER_DOWNLOAD_DEBUG_ERROR_MESSAGE_PREFIX + folder.toPath(), e);
        }
    }

    //Defaults to zip if no input from user
    private ArchiveType getArchiveType(ArchiveType archiveType) {
        return archiveType != null ? archiveType : ArchiveType.ZIP;
    }

    /**
     * <Folder name>.<archive extension>
     */
    private String createArchiveFileName(RepoPath path, ArchiveType archiveType) {
        return (path.isRoot() ? path.getRepoKey() : PathUtils.getLastPathElement(path.getPath())) + "." +
                archiveType.value();
    }
}
