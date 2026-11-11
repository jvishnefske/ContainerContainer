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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.rest.common.model.xray.RepositoriesHolder;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.AuthenticationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * @author Shay Yaakov
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class IndexXrayService implements RestService {
    private static final Logger log = LoggerFactory.getLogger(IndexXrayService.class);

    private AddonsManager addonsManager;

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        XrayAddon xrayAddon = addonsManager.addonByType(XrayAddon.class);
        String remoteAddress = AuthenticationHelper.getRemoteAddress(AuthenticationHelper.getAuthentication());
        String err = "Failing Xray index request received from " + remoteAddress + ": {}";
        if (!xrayAddon.isXrayEnabled()) {
            response.error("Xray configuration does not exist on this Artifactory instance or its disabled")
                    .responseCode(400);
            log.warn(err, "No Xray configuration found or is disabled.");
            return;
        }

        String repos = request.getQueryParamByKey("repos");
        List<String> reposToIndex;
        if (StringUtils.isBlank(repos)) {
            final RepositoriesHolder holder = (RepositoriesHolder) request.getImodel();
            if (holder == null || CollectionUtils.isEmpty(holder.getRepos())) {
                logReposError(response, err);
                return;
            }
            reposToIndex = holder.getRepos();
        } else {
            reposToIndex = Arrays.asList(repos.split("\\s*,\\s*"));
        }
        xrayAddon.indexRepos(reposToIndex);
        response.responseCode(202);
    }

    private void logReposError(RestResponse response, String err) {
        response.error("No repos were given to index.").responseCode(HttpStatus.BAD_REQUEST.value());
        log.warn(err, "No repos were given to index.");
    }

}