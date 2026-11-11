package org.artifactory.ui.rest.service.artifacts.browse.generic;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.repo.BaseBrowsableItem;
import org.artifactory.api.repo.BrowsableItemCriteria;
import org.artifactory.api.repo.RootNodesFilterResult;
import org.artifactory.api.repo.exception.FolderExpectedException;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.ui.rest.model.artifacts.browse.generic.NativeBrowserChild;
import org.artifactory.ui.rest.model.artifacts.browse.generic.NativeBrowserPaginatedModel;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.NativeNodeFilter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.NativeArtifactsFetchBulkStrategy;
import org.artifactory.util.UiRequestUtils;
import org.jfrog.commons.continues.ContinueResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.artifactory.ui.rest.resource.artifacts.browse.generic.NativeBrowserResource.REPO_PATH;


/**
 * @author Hussein Alshqerat
 */
@Component
@Slf4j
public class BrowseNativeModelV2Service extends BaseBrowseNativeService {
    private static final NativeArtifactsFetchBulkStrategy nativeArtifactsFetchBulkStrategy =
            new NativeArtifactsFetchBulkStrategy();
    private static final String RECORD_NUM = "recordNum";

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
            boolean isFolder = isFolderPath(path, request);
            if (isFolder) {
                final NativeBrowserPaginatedModel nativeBrowserModel =
                        buildNativeBrowserData(request, response, repoPath);
                if (nativeBrowserModel == null) {
                    return;
                }
                response.iModel(nativeBrowserModel);
            } else {
                NativeBrowserPaginatedModel result =
                        NativeBrowserPaginatedModel.createInstance(repoPath.getRepoKey(), repoPath.getPath(), null,
                                false, false, "-1", null);
                response.iModel(result);
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
            response.iModel(NativeBrowserPaginatedModel.createInstance(repoPath.getRepoKey(), repoPath.getPath(),
                    null, false, false, "-1", null));
        } catch (Exception e) {
            setNotFoundResponse(response, e.getMessage());
        }
    }

    private static long getRecordNum(ArtifactoryRestRequest<String> request) {
        String value = request.getQueryParamByKey(RECORD_NUM);
        return isBlank(value) ? 0 : Long.parseLong(value);
    }

    /**
     * Method defines type of provided repo path. It fetches parent's tree, finds item by name and checks its type.
     * If the provided path can't be verified, for example, it does not exist - it will be treated like a folder.
     */
    @VisibleForTesting
    boolean isFolderPath(String path, ArtifactoryRestRequest<String> request) {
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
        Stream<? extends BaseBrowsableItem> parentBrowserChildrenStream = getNativeBrowserData(request, parentRepoPath);
        if (parentBrowserChildrenStream == null) {
            return true;
        }
        BaseBrowsableItem targetItem = parentBrowserChildrenStream
                .filter(child -> child.getName().equals(originalRepoPath.getName())).findFirst().orElse(null);
        return targetItem != null && targetItem.isFolder();
    }

    private Stream<? extends BaseBrowsableItem> getNativeBrowserData(ArtifactoryRestRequest<String> request,
            RepoPath parentRepoPath) {
        NativeNodeFilter nativeNodeFilter = buildNativeNodeFilter(request, parentRepoPath);
        String repoType = getRepoType(nativeNodeFilter.getRepoPath());
        BrowsableItemCriteria.Builder builder = new BrowsableItemCriteria.Builder(
                nativeNodeFilter.getRepoPath()).
                requestProperties(nativeNodeFilter.getRequestProps());
        builder.includeChecksums(!ConstantValues.uiHideChecksums.getBoolean());
        BrowsableItemCriteria criteria = builder.build();
        return getBrowsableItemsStream(repoType, criteria, nativeNodeFilter);
    }

    @Nullable
    @VisibleForTesting
    NativeBrowserPaginatedModel buildNativeBrowserData(ArtifactoryRestRequest<String> request, RestResponse response,
            RepoPath repoPath) {
        try {
            NativeNodeFilter nativeNodeFilter = buildNativeNodeFilter(request, repoPath);
            ContinueResult<? extends BaseBrowsableItem> browsableItems;
            if (ConstantValues.treeBrowserStreamEnabled.getBoolean()) {
                browsableItems = fetchItems(nativeNodeFilter);
            } else {
                browsableItems = getItemsPage(nativeNodeFilter, nativeArtifactsFetchBulkStrategy);
            }
            boolean isUiRequest = UiRequestUtils.isUiRestRequest(request.getServletRequest());
            boolean isChallengeSet =
                    setNativeModelResponseToHttpChallenge(repoPath, nativeNodeFilter.getRootNodesFilterResult(),
                            browsableItems.getData(), response, isUiRequest);
            if (isChallengeSet) {
                return null;
            }
            return transformIntoUiModel(repoPath, browsableItems.getData(), browsableItems.getContinueState());
        } catch (FolderExpectedException e) {
            log.debug("", e);
            // This is a file, not a folder.
            if (!hasPermissionOnPath(repoPath)) {
                throw new ForbiddenException("");
            }
            // The UI will open a download request to the provided repoPath.
            return NativeBrowserPaginatedModel.createInstance(repoPath.getRepoKey(), repoPath.getPath(), null, false,
                    false, "-1", null);
        } catch (ItemNotFoundRuntimeException e) {
            if (checkForSpecificCases(response, repoPath, e)) {
                return null;
            }
            return NativeBrowserPaginatedModel.createInstance(repoPath.getRepoKey(), repoPath.getPath(), null, false,
                    false, "-1", null);
        }
    }

    private NativeNodeFilter buildNativeNodeFilter(ArtifactoryRestRequest<String> request, RepoPath repoPath) {
        long recordNum = getRecordNum(request);
        MutableCentralConfigDescriptor mutableDescriptor = centralConfigService.getMutableDescriptor();
        long limit = mutableDescriptor.getItemsBrowserPagingSize();
        boolean includeChecksums = !ConstantValues.uiHideChecksums.getBoolean();
        NativeNodeFilter nativeNodeFilter =
                NativeNodeFilter.builder()
                        .repoPath(repoPath)
                        .rootNodesFilterResult(new RootNodesFilterResult())
                        .updateRootNodesFilterFlag(securityService.isAnonymous())
                        .includeChecksums(includeChecksums)
                        .build();
        nativeNodeFilter.setLimit(limit);
        nativeNodeFilter.setContinueIndex(recordNum);
        return nativeNodeFilter;
    }

    private boolean setNativeModelResponseToHttpChallenge(RepoPath repoPath,
            RootNodesFilterResult rootNodesFilterResult, List<? extends BaseBrowsableItem> items,
            RestResponse<?> response, boolean isUiRequest) {
        boolean isChallengeSet =
                super.setResponseToHttpChallenge(repoPath, rootNodesFilterResult, items, response, true);
        if (isChallengeSet && !isUiRequest) {
            response.responseCode(SC_FORBIDDEN);
        }
        return isChallengeSet;
    }

    private NativeBrowserPaginatedModel transformIntoUiModel(RepoPath repoPath,
            List<? extends BaseBrowsableItem> browsableItems, String continueState) {
        final List<NativeBrowserChild> nativeItems = browsableItems.stream()
                .map(item -> new NativeBrowserChild(item.getName(), item.getLastModified(), item.getSize(),
                        item.isFolder(), item.isRemote())).toList();
        return NativeBrowserPaginatedModel.createInstance(repoPath.getRepoKey(), repoPath.getPath(), getAddressFooter(),
                repoPath.isRoot(), true, continueState, nativeItems);
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
