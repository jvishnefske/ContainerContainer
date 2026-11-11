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

package org.artifactory.ui.rest.service.admin.advanced.securitydescriptor;

import com.google.common.annotations.VisibleForTesting;
import org.artifactory.api.security.SecurityService;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.factory.JaxbAdapter;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.SaltedPassword;
import org.artifactory.security.SecurityInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.ui.rest.model.admin.advanced.securitydescriptor.SecurityDescriptorModel;
import org.artifactory.util.AolUtils;
import org.artifactory.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Chen Keinan
 */
@Component
public class GetSecurityDescriptorService implements RestService {

    @Autowired
    private SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        AolUtils.assertNotAol("GetSecurityDescriptor");
        // get security descriptor model
        SecurityDescriptorModel securityDescriptorModel = getSecurityDescriptorModel();
        // update response data
        response.iModel(securityDescriptorModel);
    }

    /**
     * get security descriptor model from security descriptor info
     *
     * @return security descriptor model
     */
    private SecurityDescriptorModel getSecurityDescriptorModel() {
        SecurityInfo securityData = SecurityUtils.getEncryptSecurityInfo();
        redactPasswords(securityData);
        JaxbAdapter jaxbAdapter = InfoFactoryHolder.get().getSecurityJaxbAdapter();
        return new SecurityDescriptorModel(jaxbAdapter.toXML(securityData));
    }

    @VisibleForTesting
    void redactPasswords(SecurityInfo securityData) {
        if (securityData.getUsers() != null) {
            for (UserInfo user : securityData.getUsers()) {
                ((MutableUserInfo) user).setPassword(new SaltedPassword(null, null));
            }
        }
    }
}
