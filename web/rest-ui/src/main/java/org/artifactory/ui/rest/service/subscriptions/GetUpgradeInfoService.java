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

package org.artifactory.ui.rest.service.subscriptions;

import org.artifactory.api.subscriptions.SubscriptionsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.subscriptions.UpgradeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author Alexei Vainshtein
 */
@Component
public class GetUpgradeInfoService implements RestService {
    private static final Logger log = LoggerFactory.getLogger(GetUpgradeInfoService.class);
    private final SubscriptionsService subscriptionsService;

    public GetUpgradeInfoService(SubscriptionsService subscriptionsService) {
        this.subscriptionsService = subscriptionsService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        try {
            UpgradeInfo upgradeInfo = new UpgradeInfo(subscriptionsService.getPurchaseUrl());
            response.iModel(upgradeInfo);
        } catch (IOException e) {
            String message = "Exception occurred while trying to generate the upgrade url: ";
            log.error("{}{}",message, e.getMessage());
            log.debug(message, e);
            response.error(message + e.getMessage());
        }
    }
}
