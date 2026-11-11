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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.mail.MailServerConfiguration;
import org.artifactory.api.mail.MailService;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.mail.MailServerDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.ui.rest.model.admin.configuration.mail.MailServer;
import org.artifactory.util.AolUtils;
import org.artifactory.util.EmailException;
import org.artifactory.util.MailUtils;
import org.artifactory.util.UrlValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Chen Keinan
 */
@Slf4j
@Component
public class TestMailService<T extends MailServer> implements RestService<T> {
    static final String INVALID_CONFIG_ERROR_MSG =
            "Mail server configuration must be enabled with a defined host and port.";

    private MailService mailService;

    @Autowired
    public void setMailService(MailService mailService) {
        this.mailService = mailService;
    }

    @Override
    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {
        AolUtils.assertNotAol("TestMail");
        // get mail configuration instance
        MailServerConfiguration mailServerConfiguration = getMailServerConfiguration(request);
        // validate configuration data
        if (!validateConfig(mailServerConfiguration)) {
            response.error(INVALID_CONFIG_ERROR_MSG);
            return;
        }
        validateUrl(mailServerConfiguration.getArtifactoryUrl());
        // send Test mail
        sendMail(mailServerConfiguration, request.getImodel(), response);
    }

    /**
     * get mail server configuration
     *
     * @param artifactoryRequest - encapsulate all date related to request
     * @return instance of mail server configuration data
     */
    private MailServerConfiguration getMailServerConfiguration(ArtifactoryRestRequest artifactoryRequest) {
        MailServerDescriptor serverData = (MailServerDescriptor) artifactoryRequest.getImodel();
        return new MailServerConfiguration(
                serverData.isEnabled(), serverData.getHost(), serverData.getPort(), serverData.getUsername(),
                CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), serverData.getPassword()), serverData.getFrom(),
                serverData.getSubjectPrefix(),
                serverData.isTls(), serverData.isSsl(), serverData.getArtifactoryUrl());
    }

    /**
     * validate mail server setting
     *
     * @param mailServerConfiguration - mail server configuration
     * @return if true - configuration is valid
     */
    private boolean validateConfig(MailServerConfiguration mailServerConfiguration) {
        boolean hasHost = false;
        boolean hasPort = false;
        if (mailServerConfiguration != null) {
            hasHost = !StringUtils.isEmpty(mailServerConfiguration.getHost());
            hasPort = (mailServerConfiguration.getPort() > 0);
        }
        return hasHost && hasPort && mailServerConfiguration.isEnabled();
    }

    private void validateUrl(String url) {
        UrlValidator urlValidator = new UrlValidator(false);
        try {
            urlValidator.validate(url);
        } catch (UrlValidator.UrlValidationException e) {
            throw new BadRequestException("Artifactory Url is in illegal form.");
        }
    }

    /**
     * send test mail to receipt
     *
     * @param configuration - mail server configuration
     * @param model         - mail server model
     * @param response      - encapsulate all data require for response
     */
    private void sendMail(MailServerConfiguration configuration, MailServer model, RestResponse response) {
        String testRecipient = model.getTestReceipt();
        if (!StringUtils.isEmpty(testRecipient)) {
            try {
                mailService.sendMail(new String[] {testRecipient}, "Test",
                        MailUtils.getTestMessage(configuration.getArtifactoryUrl()).toString(),
                        configuration);
                String confirmMessage = "A test message has been sent successfully to '" + testRecipient + "'";
                response.info(confirmMessage);
                log.info(confirmMessage);
            } catch (EmailException e) {
                String message = e.getMessage();
                if (message == null) {
                    message = "An error occurred while sending an e-mail. See logs for additional information";
                }
                log.error(message, e);
                response.error(message);
            }
        } else {
            response.error("Test recipient field cannot be empty");
        }
    }
}
