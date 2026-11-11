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

package org.artifactory.ui.rest.service.admin.advanced.storage;

import org.artifactory.aol.AolService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.storage.StorageService;
import org.jfrog.storage.binstore.ifc.model.BinaryProvidersInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author Gidi Shabat
 */
@Component
public class GetBinaryProvidersInfoService implements RestService {

    @Autowired
    private StorageService storageService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        AolService aolService = ContextHelper.get().beanForType(AolService.class);
        if (aolService.isAol() && !aolService.isDashboardUser()) {
            response.error("Artifactory SaaS does not support this feature.");
            return;
        }
        BinaryProvidersInfo<Map<String, String>> binaryProviderInfo = storageService.getBinaryProviderInfo();
        response.iModel(binaryProviderInfo.rootTreeElement);
    }
}
