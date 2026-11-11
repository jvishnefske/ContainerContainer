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

package org.artifactory.ui.rest.service.admin.security.general;

import org.artifactory.descriptor.security.UserLockPolicy;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.access.AccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Michael Pasternak
 */
@Component
public class GetUserLockPolicyService implements RestService {

    @Autowired
    private AccessService accessService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        UserLockPolicy userLockPolicy = new UserLockPolicy();
        int attempts = accessService.getUserLockPolicyConfig().getAttempts();
        userLockPolicy.setEnabled(attempts > 0);
        userLockPolicy.setLoginAttempts(attempts);
        response.iModel(userLockPolicy);
    }
}
