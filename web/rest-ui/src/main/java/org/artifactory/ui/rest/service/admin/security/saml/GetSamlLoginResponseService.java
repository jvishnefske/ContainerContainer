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

package org.artifactory.ui.rest.service.admin.security.saml;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.sso.saml.SamlException;
import org.artifactory.addon.sso.saml.SamlHandler;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.common.util.RequestUtils;
import org.artifactory.security.PluggableAuthenticationProviderIfc;
import org.artifactory.security.RealmAwareAuthenticationManager;
import org.artifactory.webapp.servlet.HttpArtifactoryRequest;
import org.artifactory.security.exceptions.UserLockedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;

/**
 * @author Gidi Shabat
 */
@Slf4j
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetSamlLoginResponseService implements RestService {
    private static final String RESPONSE_ERROR = "Error occurred while trying to login using SAML: %s. " +
            "Check your Artifactory logs for more details.";
    @Autowired
    private SamlHandler samlHandler;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        try {
            Map<String, List<String>> formParameters = RequestUtils.getFormParameters(request);
            samlHandler.handleLoginResponse(request.getServletRequest(), response.getServletResponse(), formParameters);
            // authenticate pluggable providers
            authenticateAdditives(request.getServletRequest());
        } catch (SamlException e) {
            log.error("Error occurred while trying to login using SAML: {}", e.getMessage());
            log.debug("Error occurred while trying to login using SAML", e);
            if (StringUtils.isNotEmpty(e.getUiErrorMessage())) {
                response.error(String.format(RESPONSE_ERROR, e.getUiErrorMessage()));
            } else {
                response.error(
                        "Error occurred while trying to login using SAML. Check your Artifactory logs for more " +
                                "details.");
            }
        } catch (UserLockedException e) {
            log.warn("An attempt to login via SAML was denied: {}", e.getMessage());
            log.debug("Error occurred while trying to login using SAML", e);
            response.error(String.format(RESPONSE_ERROR, "Locked user")).responseCode(SC_FORBIDDEN);
        } catch (Exception e) {
            log.error("Error occurred while trying to login using SAML: {}", e.getMessage());
            log.debug("Error occurred while trying to login using SAML", e);
            response.error(
                    "Error occurred while trying to login using SAML. Check your Artifactory logs for more details.");
        }
    }

    /**
     * call public api plugin's authenticate calls of the additive plugins
     */
    @VisibleForTesting
    void authenticateAdditives(HttpServletRequest request) {
        RealmAwareAuthenticationManager authenticationManager =
                ContextHelper.get().beanForType(RealmAwareAuthenticationManager.class);
        List<PluggableAuthenticationProviderIfc> providers = authenticationManager
                .getPluggableAuthenticationProviders();
        providers.forEach(provider -> {
            try {
                provider.authenticateAdditive(new HttpArtifactoryRequest(request));
            } catch (Exception e) {
                log.error("Can't resolve additive plugin in realm {} : {}", provider.getRealm(), e.getMessage());
                log.debug("Can't resolve additive plugin :", e);
            }
        });
    }
}