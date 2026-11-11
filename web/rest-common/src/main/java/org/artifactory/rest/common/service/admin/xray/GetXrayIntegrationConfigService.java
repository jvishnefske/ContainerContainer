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

package org.artifactory.rest.common.service.admin.xray;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.XrayDescriptor;
import org.artifactory.rest.common.model.xray.XrayIntegrationModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.common.util.RequestUtils;
import org.artifactory.exception.http.ForbiddenException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author Yuval Reches
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class GetXrayIntegrationConfigService implements RestService {

    private final CentralConfigService configService;
    private final SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        validateProjectAdminPermissions(RequestUtils.getNormalizedProjectKey(request));

        boolean enabled = false;
        boolean allowBlocked = false;
        boolean allowWhenUnavailable = false;
        int blockUnscannedTimeoutSeconds = 60;
        int blockUnfinishedScansTimeoutSeconds = 1800;
        XrayDescriptor xrayConfig = configService.getDescriptor().getXrayConfig();
        if (xrayConfig != null) {
            enabled = xrayConfig.isEnabled();
            allowBlocked = xrayConfig.isAllowBlockedArtifactsDownload();
            allowWhenUnavailable = xrayConfig.isAllowOperationsXrayUnavailable();
            blockUnscannedTimeoutSeconds = xrayConfig.getBlockUnscannedTimeoutSeconds();
            blockUnfinishedScansTimeoutSeconds = xrayConfig.getBlockUnfinishedScansTimeoutSeconds();
        }
        XrayIntegrationModel xrayIntegrationModel = new XrayIntegrationModel(enabled, allowBlocked,
                allowWhenUnavailable, blockUnscannedTimeoutSeconds, blockUnfinishedScansTimeoutSeconds);
        response.iModel(xrayIntegrationModel);
    }

    private void validateProjectAdminPermissions(@NonNull String projectKey) {
        if (!securityService.isProjectAdmin(projectKey)) {
            throw new ForbiddenException("Forbidden for non admin users");
        }
    }
}
