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

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.ui.rest.model.admin.configuration.mail.MailServer;
import org.artifactory.util.AolUtils;
import org.artifactory.util.UrlValidator;
import org.springframework.stereotype.Component;

/**
 * @author Chen Keinan
 */
@AllArgsConstructor
@Component
@Slf4j
public class UpdateMailService implements RestService {

    private CentralConfigService centralConfigService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        AolUtils.assertNotAol("UpdateMail");
        saveMailChangesToDescriptor(request, response);
    }

    /**
     * save mail changes to descriptor
     *
     * @param artifactoryRequest - encapsulate artifactory data related  to request
     */
    private void saveMailChangesToDescriptor(ArtifactoryRestRequest artifactoryRequest,
            RestResponse artifactoryResponse) {
        MailServer mailServer = (MailServer) artifactoryRequest.getImodel();
        validateUrl(mailServer.getArtifactoryUrl());
        MutableCentralConfigDescriptor configDescriptor = centralConfigService.getMutableDescriptor();
        configDescriptor.setMailServer(mailServer);
        centralConfigService.saveEditedDescriptorAndReload(configDescriptor, mailServer);
        artifactoryResponse.info("Successfully updated Mail server settings");
    }

    private void validateUrl(String url) {
        UrlValidator urlValidator = new UrlValidator(false);
        try {
            urlValidator.validate(url);
        } catch (UrlValidator.UrlValidationException e) {
            throw new BadRequestException("Artifactory Url is in illegal form.");
        }
    }
}
