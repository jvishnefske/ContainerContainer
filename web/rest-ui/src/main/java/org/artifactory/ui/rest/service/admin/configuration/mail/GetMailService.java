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

package org.artifactory.ui.rest.service.admin.configuration.mail;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.mail.MailServer;
import org.artifactory.util.AolUtils;
import org.artifactory.util.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Chen Keinan
 */
@Component
public class GetMailService implements RestService {

    private CentralConfigService centralConfigService;

    @Autowired
    public void setCentralConfigService(CentralConfigService centralConfigService) {
        this.centralConfigService = centralConfigService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        AolUtils.assertNotAol("GetMail");
        String contextUrl = HttpUtils.getServletContextUrl(request.getServletRequest());
        MailServer mailServer = getMailServerFromConfigDescriptor(contextUrl);
        // update response with mail server model
        response.iModel(mailServer);
    }

    /**
     * get mail server from config descriptor and populate data to mail server model
     *
     * @return mail server model
     */
    private MailServer getMailServerFromConfigDescriptor(String contextUrl) {
        MutableCentralConfigDescriptor configDescriptor = centralConfigService.getMutableDescriptor();
        if (configDescriptor.getMailServer() != null) {
            MailServer mailServer = new MailServer(configDescriptor.getMailServer());
            mailServer.setPassword(null);
            return mailServer;
        } else {
            MailServer mailServer = new MailServer();
            mailServer.setArtifactoryUrl(contextUrl);
            return mailServer;
        }
    }
}
