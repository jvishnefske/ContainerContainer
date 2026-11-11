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

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.permission;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.RepoAcl;
import org.artifactory.ui.rest.service.common.EffectivePermissionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Used in Effective Permission tab when the number of permission targets per entity (user/group) is larger than 5.
 * Then we don't show all the permissions, but only when user is requesting it specifically.
 *
 * @author nadavy
 */
@Slf4j
@Component
public class GetRepoEffectivePermissionsByEntityService implements RestService {

    private AuthorizationService authorizationService;
    private SecurityService securityService;
    private EffectivePermissionHelper effectivePermissionHelper;

    @Autowired
    public GetRepoEffectivePermissionsByEntityService(UserGroupService userGroupService,
            AuthorizationService authorizationService, SecurityService securityService) {
        this.authorizationService = authorizationService;
        this.securityService = securityService;
        this.effectivePermissionHelper = new EffectivePermissionHelper(userGroupService);
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String repoKey = request.getQueryParamByKey("repoKey");
        String path = request.getQueryParamByKey("path");
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        if (authorizationService.canManage(repoPath)) {
            EntityInfo entityInfo = getEntityInfo(request, repoPath);
            List<String> permissionsByEntity = getPermissionsByEntity(entityInfo);
            if (permissionsByEntity != null) {
                response.iModel(permissionsByEntity);
            }
        } else {
            response.responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
        }
    }

    private EntityInfo getEntityInfo(ArtifactoryRestRequest request, RepoPath repoPath) {
        EntityInfo entityInfo = new EntityInfo();
        entityInfo.repoPath = repoPath;
        entityInfo.entityName = request.getPathParamByKey("username");
        if (Strings.isNullOrEmpty(entityInfo.entityName)) {
            entityInfo.entityName = request.getPathParamByKey("groupname");
            entityInfo.isGroup = true;
        }
        return entityInfo;
    }

    /**
     * Returns all permissions names of a given entity
     */
    private List<String> getPermissionsByEntity(EntityInfo entityInfo) {
        List<RepoAcl> repoPathAcls = authorizationService.getRepoPathAcls(entityInfo.repoPath);
        boolean isGroup = entityInfo.isGroup;
        String entityName = entityInfo.entityName;
        return effectivePermissionHelper.getPermissionByEntity(repoPathAcls, isGroup, entityName);
    }

    private class EntityInfo {
        boolean isGroup = false;
        String entityName;
        RepoPath repoPath;
    }
}
