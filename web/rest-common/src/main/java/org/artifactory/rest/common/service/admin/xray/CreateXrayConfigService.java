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

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.rest.common.model.xray.XrayConfigModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.AuthenticationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

import static jakarta.ws.rs.core.HttpHeaders.USER_AGENT;

/**
 * @author Chen Keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class CreateXrayConfigService implements RestService<XrayConfigModel> {
    private static final Logger log = LoggerFactory.getLogger(CreateXrayConfigService.class);

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private CentralConfigService configService;

    /**
     * Executes and handles Xray config creation in Artifactory
     * Checks and relies on the UserAgent convention in the Xray code
     *
     * @param request  - encapsulate all data require for request processing
     * @param response - encapsulate all data require from response
     */
    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        XrayAddon xrayAddon = addonsManager.addonByType(XrayAddon.class);
        String xrayUserAgent = request.getHttpHeaders().getRequestHeader(USER_AGENT).get(0);
        if (xrayUserAgent == null || xrayUserAgent.isEmpty()) {
            response.error("Unable to create Xray Configuration - no user-agent was provided in the request")
                    .responseCode(HttpStatus.SC_FORBIDDEN);
            return;
        }
        if (!xrayAddon.isXrayVersionValid(xrayUserAgent)) {
            log.warn("Attempt to setup an Xray instance with an incompatible version was made: {}", xrayUserAgent);
            response.error("Xray version ( " + xrayUserAgent +
                    " ) is below 3.0.0 - Please upgrade Xray to use Artifactory 7.0.0 and above")
                    .responseCode(HttpStatus.SC_FORBIDDEN);
            return;
        }
        if (!addonsManager.isEntitled(EntitlementType.JFXR)) {
            response.error("Artifactory license does not support xray").responseCode(HttpStatus.SC_FORBIDDEN);
        } else {
            if (xrayAddon.isXrayConfigMissing()) {
                // create xray Config
                createXrayConfig(response, request);
            } else {
                response.error("Xray config already exists on this instance.")
                        .responseCode(HttpServletResponse.SC_CONFLICT);
            }
        }
    }

    private void createXrayConfig(RestResponse response, ArtifactoryRestRequest request) {
        String remoteAddress = AuthenticationHelper.getRemoteAddress(AuthenticationHelper.getAuthentication());
        log.info("Creating Xray config, request received from instance at: {}", remoteAddress);
        XrayConfigModel xrayConfigModel = (XrayConfigModel) request.getImodel();
        boolean created = createXrayConfig(xrayConfigModel);
        if (created) {
            response.iModel(xrayConfigModel);
            response.responseCode(HttpServletResponse.SC_CREATED);
        } else {
            String err = "Failed to create Xray config.";
            response.error(err).responseCode(HttpServletResponse.SC_BAD_REQUEST);
            log.warn(err);
        }
    }

    private boolean createXrayConfig(XrayConfigModel xrayModel) {
        if (xrayModel.validate(true)) {
            if (configService.getSystemConfiguration().isRouterEnabled()) {
                xrayModel.setXrayBaseUrl(configService.getSystemConfiguration().getRouterUrl() + "/xray");
            }
            MutableCentralConfigDescriptor descriptor = configService.getMutableDescriptor();
            log.debug("Creating Xray config for instance {}", xrayModel.getXrayBaseUrl());
            descriptor.setXrayConfig(xrayModel.toDescriptor());
            configService.saveEditedDescriptorAndReload(descriptor, xrayModel.toDescriptor());
            return true;
        }
        log.debug("Invalid Xray config model received!");
        return false;
    }
}
