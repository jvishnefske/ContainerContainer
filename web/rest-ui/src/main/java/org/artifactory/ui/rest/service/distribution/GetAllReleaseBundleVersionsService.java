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

package org.artifactory.ui.rest.service.distribution;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.api.release.bundle.ReleaseBundleSearchFilter;
import org.artifactory.api.rest.distribution.bundle.models.BundleVersion;
import org.artifactory.api.rest.distribution.bundle.models.BundleVersionsResponse;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.bundle.BundleType;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Tomer Mayost
 */
@Component
public class GetAllReleaseBundleVersionsService implements RestService {

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private AuthorizationService authService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        ReleaseBundleAddon releaseBundleAddon = addonsManager.addonByType(ReleaseBundleAddon.class);
        ReleaseBundleSearchFilter versionFilter = createVersionFilter(request);
        BundleVersionsResponse bundles = releaseBundleAddon.getBundleVersions(versionFilter);
        List<BundleVersion> completedVersions =
                bundles.getVersions().stream().filter(ver -> "complete".equalsIgnoreCase(ver.getStatus()))
                        .collect(Collectors.toList());
        BundleType type = BundleType.fromType(request.getPathParamByKey("type"));
        if (BundleType.TARGET.equals(type)) {
            completedVersions.forEach(bundle -> fillCanDeleteBundle(bundle, request.getPathParamByKey("name")));
        }
        bundles.setVersions(completedVersions);
        response.iModel(bundles);
    }

    private void fillCanDeleteBundle(BundleVersion bundle, String name) {
        RepoPath repoPath = RepoPathFactory.create(bundle.getStoringRepo(), name + "/" + bundle.getVersion());
        bundle.setCanDelete(authService.canDelete(repoPath));
    }

    private ReleaseBundleSearchFilter createVersionFilter(ArtifactoryRestRequest request) {
        String name = request.getPathParamByKey("name");
        String type = request.getPathParamByKey("type");
        String beforeStr = request.getQueryParamByKey("before");
        String afterStr = request.getQueryParamByKey("after");
        long before = StringUtils.isNotBlank(beforeStr) ? Long.parseLong(beforeStr) : 0;
        long after = StringUtils.isNotBlank(afterStr) ? Long.parseLong(afterStr) : 0;
        String orderBy = request.getQueryParamByKey("order_by");
        String direction = request.getQueryParamByKey("direction");
        String limitStr = request.getQueryParamByKey("num_of_rows");

        int limit = StringUtils.isNotBlank(limitStr) ? Integer.parseInt(limitStr) :
                ConstantValues.searchUserQueryLimit.getInt();
        limit = Math.min(limit, ConstantValues.searchUserQueryLimit.getInt());
        return ReleaseBundleSearchFilter.builder()
                .bundleType(BundleType.fromType(type))
                .name(name)
                .before(before)
                .after(after)
                .orderBy(orderBy)
                .direction(direction)
                .limit(limit)
                .daoLimit(ConstantValues.searchUserSqlQueryLimit.getInt())
                .build();
    }
}
