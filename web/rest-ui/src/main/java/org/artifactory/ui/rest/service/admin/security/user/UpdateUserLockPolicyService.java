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

package org.artifactory.ui.rest.service.admin.security.user;

import org.artifactory.config.AccessConfigMapUtils;
import org.artifactory.descriptor.security.UserLockPolicy;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.AccessClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Michael Pasternak
 */
@Component
public class UpdateUserLockPolicyService<T extends UserLockPolicy> implements RestService<T> {

    @Autowired
    private AccessService accessService;

    @Override
    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {
        UserLockPolicy userLockPolicy = request.getImodel();

        Map<String, Object> accessMap = new HashMap<>();
        AccessConfigMapUtils.addLoginAttempts(userLockPolicy.getLoginAttempts(), accessMap);
        try {
            accessService.updateConfigurationModel(accessMap);
        } catch (AccessClientException e) {
            response.responseCode(400);
            response.error(e.getMessage());
            return;
        }
        response.info("UserLockPolicy was successfully updated");
    }
}
