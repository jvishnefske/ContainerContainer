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

package org.artifactory.ui.rest.service.artifacts.browse.generic;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.repo.BaseBrowsableItem;
import org.artifactory.api.repo.RootNodesFilterResult;
import org.artifactory.api.repo.exception.FolderExpectedException;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.ui.rest.model.artifacts.browse.generic.NativeBrowserChild;
import org.artifactory.ui.rest.model.artifacts.browse.generic.NativeBrowserModel;
import org.artifactory.util.UiRequestUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.artifactory.ui.rest.resource.artifacts.browse.generic.NativeBrowserResource.REPO_PATH;

/**
 * @author BarakH
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Slf4j
public class BrowseNativeModelService extends BaseBrowseNativeService {

    @Override
    public void execute(ArtifactoryRestRequest<String> request, RestResponse response) {
        RepoPath repoPath = null;
        try {
            String path = request.getPathParamByKey(REPO_PATH);
            if (StringUtils.isEmpty(path)) {
                throw new NotFoundException("No repository path has been provided");
            }
            repoPath = RepoPathFactory.create(path);
            String repoKey = repoPath.getRepoKey();
            if (!repositoryService.getAllRepoKeys().contains(repoKey)) {
                log.debug("Repository does not exist: {}", repoPath.toPath());
                throw new ForbiddenException("");
            }
            boolean isFolder = isFolderPath(path, request, response);
            if (isFolder) {
                final NativeBrowserModel nativeBrowserModel = buildNativeBrowserData(request, response, repoPath);
                if (nativeBrowserModel == null) {
                    return;
                }
                response.iModel(nativeBrowserModel);
            } else {
                response.iModel(
                        new NativeBrowserModel(repoPath.getRepoKey(), repoPath.getPath(), null, false, false, null));
            }
        } catch (ForbiddenException | NotFoundException e) {
            if (securityService.isAnonAccessEnabled() && securityService.isAnonymous()) {
                setUnauthorizedResponse(response, e.getMessage());
            } else {
                setNotFoundResponse(response, e.getMessage());
            }
        } catch (ItemNotFoundRuntimeException e) {
            if (checkForSpecificCases(response, Objects.requireNonNull(repoPath), e)) {
                return;
            }
            response.iModel(new NativeBrowserModel(repoPath.getRepoKey(), repoPath.getPath(),
                    null, false, false, null));
        } catch (Exception e) {
            setNotFoundResponse(response, e.getMessage());
        }
    }

    /**
     * Method defines type of provided repo path. It fetches parent's tree, finds item by name and checks its type.
     * If the provided path can't be verified, for example, it does not exist - it will be treated like a folder.
     */
    @VisibleForTesting
    boolean isFolderPath(String path, ArtifactoryRestRequest<String> request, RestResponse response) {
        if (StringUtils.isBlank(path)) {
            return true;
        }
        if (path.endsWith("/")) {
            return true;
        }
        RepoPath originalRepoPath = RepoPathFactory.create(path);
        RepoPath parentRepoPath = originalRepoPath.getParent();
        if (parentRepoPath == null) {
            return true;
        }
        NativeBrowserModel parentBrowserModel = buildNativeBrowserData(request, response, parentRepoPath);
        if (parentBrowserModel == null) {
            return true;
        }
        if (parentBrowserModel.getChildren() == null) {
            return false;
        }
        NativeBrowserChild targetItem = parentBrowserModel.getChildren().stream()
                .filter(child -> child.getName().equals(originalRepoPath.getName()))
                .findFirst()
                .orElse(null);
        return targetItem == null ? StringUtils.isBlank(FilenameUtils.getExtension(originalRepoPath.getName())) :
                targetItem.isFolder();
    }

    @Nullable
    @VisibleForTesting
    NativeBrowserModel buildNativeBrowserData(ArtifactoryRestRequest<String> request, RestResponse response,
            RepoPath repoPath) {
        try {
            RootNodesFilterResult rootNodesFilterResult = new RootNodesFilterResult();
            final List<? extends BaseBrowsableItem> browsableItems =
                    getItems(repoPath, null, rootNodesFilterResult);

            boolean isUiRequest = UiRequestUtils.isUiRestRequest(request.getServletRequest());
            boolean isChallengeSet = setNativeModelResponseToHttpChallenge(repoPath, rootNodesFilterResult,
                    browsableItems, response, isUiRequest);
            if (isChallengeSet) {
                return null;
            }
            return transformIntoUiModel(repoPath, request.getServletRequest(), browsableItems);
        } catch (FolderExpectedException e) {
            log.debug("", e);
            // This is a file, not a folder.
            if (!hasPermissionOnPath(repoPath)) {
                throw new ForbiddenException("");
            }
            // The UI will open a download request to the provided repoPath.
            return new NativeBrowserModel(repoPath.getRepoKey(), repoPath.getPath(), null, false, false, null);
        } catch (ItemNotFoundRuntimeException e) {
            if (checkForSpecificCases(response, repoPath, e)) {
                return null;
            }
            return new NativeBrowserModel(repoPath.getRepoKey(), repoPath.getPath(), null, false, false, null);
        }
    }

    private boolean setNativeModelResponseToHttpChallenge(RepoPath repoPath,
            RootNodesFilterResult rootNodesFilterResult,
            List<? extends BaseBrowsableItem> items, RestResponse<?> response, boolean isUiRequest) {
        boolean isChallengeSet =
                super.setResponseToHttpChallenge(repoPath, rootNodesFilterResult, items, response, true);
        if (isChallengeSet && !isUiRequest) {
            response.responseCode(SC_FORBIDDEN);
        }
        return isChallengeSet;
    }

    private NativeBrowserModel transformIntoUiModel(RepoPath repoPath, HttpServletRequest request,
            List<? extends BaseBrowsableItem> browsableItems) {
        final List<NativeBrowserChild> nativeItems = browsableItems.stream()
                .map(item -> new NativeBrowserChild(item.getName(), item.getLastModified(), item.getSize(),
                        item.isFolder(), item.isRemote()))
                .collect(Collectors.toList());

        return new NativeBrowserModel(repoPath.getRepoKey(), repoPath.getPath(), getAddressFooter(),
                repoPath.isRoot(), true, nativeItems);
    }

    private void setNotFoundResponse(RestResponse response, String message) {
        response.error(message).responseCode(HttpStatus.SC_NOT_FOUND);
        log.debug("{} - {}", message, HttpStatus.SC_NOT_FOUND);
    }

    private void setUnauthorizedResponse(RestResponse response, String message) {
        response.error(message).responseCode(HttpStatus.SC_UNAUTHORIZED);
        log.debug("{} - {}", message, HttpStatus.SC_UNAUTHORIZED);
    }
}
