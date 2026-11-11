package org.artifactory.ui.rest.service.admin.security.permissions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.common.service.security.permissions.RestSecurityRequestHandlerV2;
import org.artifactory.security.permissions.PermissionTargetModel;
import org.artifactory.ui.rest.model.admin.security.permissions.AllPermissionTargetsResourcesUIModel;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.security.PermissionTargetNaming.NAMING_UI;

/**
 * Used in Permissions page
 *
 * @author Omri Ziv
 */
@Component
public class GetPermissionTargetResourcesService implements RestService {

    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private RestSecurityRequestHandlerV2 securityRequestHandler;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String permissionTargetName = request.getPathParamByKey("name");
        PermissionTargetModel backendModel =
                securityRequestHandler.getPermissionTarget(permissionTargetName, NAMING_UI);
        if (backendModel == null) {
            response.responseCode(HttpServletResponse.SC_NOT_FOUND)
                    .error("Permission target '" + permissionTargetName + "' not found!");
            return;
        }
        List<RepoKeyType> allRealRepos = getAllRepoKeys();

        response.iModel(new AllPermissionTargetsResourcesUIModel(backendModel, allRealRepos));
    }

    /**
     * @return list of all current repo keys.
     */
    @VisibleForTesting
    List<RepoKeyType> getAllRepoKeys() {
        List<RepoKeyType> repos = Lists.newArrayList();
        repositoryService.getLocalRepoConfigsExcludingInfoRepos().stream()
                .map(repo -> new RepoKeyType(LOCAL.typeNameLowercase(), repo.getPackageType(),
                        repo.getKey()))
                .forEach(repos::add);
        repositoryService.getRemoteRepoConfigs()
                .stream()
                .map(repo ->
                        new RepoKeyType(REMOTE.typeNameLowercase(), repo.getPackageType(),
                                repo.getKey()))
                .forEach(repos::add);
        return repos;
    }
}
